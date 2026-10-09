package com.medislot.service;

import com.medislot.entity.KnowledgeSource;
import com.medislot.entity.KnowledgeSourceType;
import com.medislot.entity.KnowledgeVisibility;
import com.medislot.exception.BusinessException;
import com.medislot.repository.KnowledgeSourceRepository;
import com.medislot.service.external.ExternalSourceConfig;
import com.medislot.service.external.JsonPaths;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 来源三：按可配置 REST 配置抓取第三方接口，渲染成 Markdown 后写入知识库。
 *
 * <p>适合“抓取最近一年外部新知识”这类批处理：list 接口拿一批条目 → 逐条取详情 →
 * 按 {@code fields}/{@code computed}/template 渲染 MD → 去重后入库（PUBLIC）。
 */
@Service
public class ExternalKnowledgeService {

    private static final Logger log = LoggerFactory.getLogger(ExternalKnowledgeService.class);
    private static final DateTimeFormatter SLASH = DateTimeFormatter.ofPattern("yyyy/MM/dd");

    private final ObjectMapper objectMapper;
    private final KnowledgeSourceRepository sourceRepository;
    private final KnowledgeService knowledgeService;

    public ExternalKnowledgeService(ObjectMapper objectMapper,
                                    KnowledgeSourceRepository sourceRepository,
                                    KnowledgeService knowledgeService) {
        this.objectMapper = objectMapper;
        this.sourceRepository = sourceRepository;
        this.knowledgeService = knowledgeService;
    }

    // ==================== 同步 ====================

    /** 同步单个外部来源，返回新增入库文档数。同时更新来源的上次同步状态。 */
    public int sync(Long sourceId) {
        KnowledgeSource source = sourceRepository.findById(sourceId)
                .orElseThrow(() -> new BusinessException("来源不存在"));
        if (source.getType() != KnowledgeSourceType.EXTERNAL_API) {
            throw new BusinessException("该来源不是外部接口类型");
        }
        try {
            ExternalSourceConfig cfg = parseConfig(source.getConfigJson());
            validate(cfg);
            String secret = readSecret(cfg.auth);
            List<PendingDoc> docs = fetch(cfg, secret);
            int count = 0;
            for (PendingDoc doc : docs) {
                try {
                    if (knowledgeService.ingestMarkdown(doc.title, doc.markdown, doc.category, source.getId(),
                            KnowledgeSourceType.EXTERNAL_API, KnowledgeVisibility.PUBLIC, doc.url, null) != null) {
                        count++;
                    }
                } catch (RuntimeException e) {
                    log.warn("[kb] 外部来源 #{} 条目「{}」入库失败：{}", sourceId, doc.title, e.getMessage());
                }
            }
            mark(source, "OK", "抓取 " + docs.size() + " 条，新增 " + count + " 条");
            return count;
        } catch (RuntimeException e) {
            mark(source, "FAILED", e.getMessage());
            throw e;
        }
    }

    /** 按各来源的 schedule_cron 判断是否到期，执行到期的外部来源。返回新增总数。 */
    public int syncDueExternal() {
        int total = 0;
        for (KnowledgeSource source : sourceRepository.findAllByOrderByIdAsc()) {
            if (source.getType() != KnowledgeSourceType.EXTERNAL_API || !source.isEnabled()) {
                continue;
            }
            String cron = source.getScheduleCron();
            if (cron == null || cron.isBlank()) {
                continue;
            }
            try {
                CronExpression expr = CronExpression.parse(cron.trim());
                LocalDateTime base = source.getLastSyncAt() != null ? source.getLastSyncAt()
                        : (source.getCreatedAt() != null ? source.getCreatedAt() : LocalDateTime.now().minusYears(1));
                LocalDateTime next = expr.next(base);
                if (next != null && !next.isAfter(LocalDateTime.now())) {
                    total += sync(source.getId());
                }
            } catch (RuntimeException e) {
                log.warn("[kb] 外部来源 #{} 定时同步失败：{}", source.getId(), e.getMessage());
            }
        }
        return total;
    }

    // ==================== 抓取与渲染 ====================

    private List<PendingDoc> fetch(ExternalSourceConfig cfg, String secret) {
        HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
        Map<String, String> ctx = context(cfg);
        String listUrl = buildUrl(cfg.baseUrl, cfg.list.path, cfg.list.query, ctx);
        JsonNode listResp = getJson(client, listUrl, cfg.list.headers, cfg.auth, secret);
        JsonNode items = JsonPaths.at(listResp, cfg.list.itemsPath);
        if (items == null || !items.isArray()) {
            throw new BusinessException("列表接口未返回数组（itemsPath=" + cfg.list.itemsPath + "）");
        }
        int max = cfg.maxItems != null ? cfg.maxItems : 30;
        int delay = cfg.requestDelayMs != null ? cfg.requestDelayMs : 350;
        List<PendingDoc> docs = new ArrayList<>();
        int index = 0;
        for (JsonNode item : items) {
            if (docs.size() >= max) {
                break;
            }
            index++;
            String id = resolveId(item, cfg);
            JsonNode detailNode = item;
            if (cfg.detail != null && cfg.detail.path != null && !cfg.detail.path.isBlank()) {
                Map<String, String> dctx = new LinkedHashMap<>(ctx);
                dctx.put("id", id);
                String detailUrl = buildUrl(cfg.baseUrl, cfg.detail.path, cfg.detail.query, dctx);
                JsonNode detailResp = getJson(client, detailUrl, cfg.detail.headers, cfg.auth, secret);
                String responsePath = JsonPaths.subst(cfg.detail.responsePath, dctx);
                detailNode = JsonPaths.at(detailResp, responsePath);
                sleep(delay, index);
            }
            PendingDoc doc = render(cfg, id, detailNode);
            if (doc != null) {
                docs.add(doc);
            }
        }
        return docs;
    }

    PendingDoc render(ExternalSourceConfig cfg, String id, JsonNode detailNode) {
        Map<String, String> vars = new LinkedHashMap<>();
        vars.put("id", id);
        for (Map.Entry<String, String> e : cfg.fields.entrySet()) {
            vars.put(e.getKey(), JsonPaths.text(JsonPaths.at(detailNode, e.getValue())));
        }
        for (Map.Entry<String, String> e : cfg.computed.entrySet()) {
            vars.put(e.getKey(), JsonPaths.subst(e.getValue(), vars));
        }
        String markdown = JsonPaths.subst(cfg.template, vars);
        if (markdown == null || markdown.isBlank()) {
            return null;
        }
        String title = vars.getOrDefault("title", "");
        if (title.isBlank()) {
            title = "外部资料 " + id;
        }
        String url = vars.getOrDefault("url", "");
        String category = cfg.category == null || cfg.category.isBlank() ? "外部资料" : cfg.category;
        return new PendingDoc(title, markdown, url.isBlank() ? null : url, category);
    }

    private String resolveId(JsonNode item, ExternalSourceConfig cfg) {
        // 列表元素是字符串时直接作为 id；否则用 fields.id 或 item.id
        if (item != null && item.isTextual()) {
            return item.asText();
        }
        String idPath = cfg.fields != null ? cfg.fields.get("id") : null;
        if (idPath != null) {
            return JsonPaths.text(JsonPaths.at(item, idPath));
        }
        return JsonPaths.text(JsonPaths.at(item, "id"));
    }

    // ==================== 工具 ====================

    private ExternalSourceConfig parseConfig(String json) {
        if (json == null || json.isBlank()) {
            throw new BusinessException("外部来源未配置");
        }
        try {
            return objectMapper.readValue(json, ExternalSourceConfig.class);
        } catch (RuntimeException e) {
            throw new BusinessException("配置 JSON 解析失败：" + e.getMessage());
        }
    }

    private void validate(ExternalSourceConfig cfg) {
        if (cfg.baseUrl == null || cfg.baseUrl.isBlank()) {
            throw new BusinessException("缺少 baseUrl");
        }
        if (cfg.list == null || cfg.list.path == null || cfg.list.path.isBlank()) {
            throw new BusinessException("缺少 list.path");
        }
        if (cfg.list.itemsPath == null || cfg.list.itemsPath.isBlank()) {
            throw new BusinessException("缺少 list.itemsPath");
        }
        if (cfg.template == null || cfg.template.isBlank()) {
            throw new BusinessException("缺少 markdown 模板");
        }
    }

    Map<String, String> context(ExternalSourceConfig cfg) {
        LocalDate today = LocalDate.now();
        int sinceDays = cfg.sinceDays != null ? cfg.sinceDays : 365;
        LocalDate since = today.minusDays(sinceDays);
        Map<String, String> ctx = new LinkedHashMap<>();
        ctx.put("keyword", cfg.keyword == null ? "" : cfg.keyword);
        ctx.put("sinceDays", String.valueOf(sinceDays));
        ctx.put("sinceDate", since.format(SLASH));
        ctx.put("today", today.format(SLASH));
        ctx.put("sinceDateIso", since.toString());
        ctx.put("todayIso", today.toString());
        return ctx;
    }

    String buildUrl(String baseUrl, String path, Map<String, String> query, Map<String, String> vars) {
        StringBuilder sb = new StringBuilder();
        sb.append(baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl);
        if (path != null && !path.isBlank()) {
            sb.append(path.startsWith("/") ? path : "/" + path);
        }
        char sep = sb.indexOf("?") >= 0 ? '&' : '?';
        if (query != null) {
            for (Map.Entry<String, String> e : query.entrySet()) {
                String value = JsonPaths.subst(e.getValue(), vars);
                sb.append(sep)
                        .append(URLEncoder.encode(e.getKey(), StandardCharsets.UTF_8))
                        .append('=')
                        .append(URLEncoder.encode(value, StandardCharsets.UTF_8));
                sep = '&';
            }
        }
        return sb.toString();
    }

    private JsonNode getJson(HttpClient client, String url, Map<String, String> headers,
                             ExternalSourceConfig.Auth auth, String secret) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url))
                .GET()
                .timeout(Duration.ofSeconds(25))
                .header("Accept", "application/json")
                .header("User-Agent", "MediSlot-KB/1.0");
        if (headers != null) {
            headers.forEach(builder::header);
        }
        if (auth != null && auth.header != null && !auth.header.isBlank() && secret != null && !secret.isBlank()) {
            builder.header(auth.header, (auth.scheme == null ? "" : auth.scheme) + secret);
        }
        try {
            HttpResponse<String> resp = client.send(builder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (resp.statusCode() >= 400) {
                throw new BusinessException("外部接口返回 HTTP " + resp.statusCode());
            }
            return objectMapper.readTree(resp.body());
        } catch (BusinessException e) {
            throw e;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException("抓取被中断");
        } catch (Exception e) {
            throw new BusinessException("抓取失败：" + e.getMessage());
        }
    }

    private String readSecret(ExternalSourceConfig.Auth auth) {
        if (auth == null || auth.secretPath == null || auth.secretPath.isBlank()) {
            return null;
        }
        try {
            Path path = Path.of(auth.secretPath);
            if (!Files.exists(path)) {
                return null;
            }
            return Files.readString(path, StandardCharsets.UTF_8).trim();
        } catch (Exception e) {
            log.warn("[kb] 读取外部密钥失败：{}", e.getMessage());
            return null;
        }
    }

    private void sleep(int millis, int index) {
        if (millis <= 0 || index <= 0) {
            return;
        }
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void mark(KnowledgeSource source, String status, String message) {
        source.setLastSyncAt(LocalDateTime.now());
        source.setLastSyncStatus(status);
        source.setLastSyncMessage(message == null ? null : (message.length() > 500 ? message.substring(0, 500) : message));
        sourceRepository.save(source);
    }

    record PendingDoc(String title, String markdown, String url, String category) {
    }

    // ==================== PubMed 预设 ====================

    /** 返回 PubMed 预设配置 JSON，供“新建外部来源”表单预填。 */
    public String pubmedPresetJson() {
        return """
                {
                  "baseUrl": "https://eutils.ncbi.nlm.nih.gov/entrez/eutils",
                  "keyword": "clinical guideline",
                  "sinceDays": 365,
                  "maxItems": 20,
                  "requestDelayMs": 400,
                  "category": "外部资料·PubMed",
                  "list": {
                    "path": "/esearch.fcgi",
                    "query": {
                      "db": "pubmed",
                      "term": "{{keyword}}",
                      "retmax": "20",
                      "retmode": "json",
                      "sort": "date",
                      "datetype": "pdat",
                      "mindate": "{{sinceDate}}",
                      "maxdate": "{{today}}"
                    },
                    "itemsPath": "esearchresult.idlist"
                  },
                  "detail": {
                    "path": "/esummary.fcgi",
                    "query": { "db": "pubmed", "id": "{{id}}", "retmode": "json" },
                    "responsePath": "result.{{id}}"
                  },
                  "fields": {
                    "title": "title",
                    "date": "pubdate",
                    "journal": "fulljournalname",
                    "content": "title"
                  },
                  "computed": {
                    "url": "https://pubmed.ncbi.nlm.nih.gov/{{id}}/"
                  },
                  "template": "# {{title}}\\n\\n- 来源：PubMed · {{journal}}\\n- 日期：{{date}}\\n- 链接：{{url}}\\n\\n{{content}}\\n"
                }
                """;
    }
}
