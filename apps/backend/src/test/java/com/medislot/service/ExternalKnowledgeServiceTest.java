package com.medislot.service;

import com.medislot.service.external.ExternalSourceConfig;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 外部来源：PubMed 预设配置解析 + URL 组装 + Markdown 渲染（不发起真实 HTTP）。
 */
class ExternalKnowledgeServiceTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private final ExternalKnowledgeService service =
            new ExternalKnowledgeService(mapper, null, null, "/run/secrets/medislot/external/kb");

    @Test
    void pubmedPresetParsesAndRenders() {
        ExternalSourceConfig cfg = mapper.readValue(service.pubmedPresetJson(), ExternalSourceConfig.class);
        assertEquals("https://eutils.ncbi.nlm.nih.gov/entrez/eutils", cfg.baseUrl);
        assertEquals("esearchresult.idlist", cfg.list.itemsPath);
        assertEquals("result.{{id}}", cfg.detail.responsePath);

        Map<String, String> ctx = service.context(cfg);
        String listUrl = service.buildUrl(cfg.baseUrl, cfg.list.path, cfg.list.query, ctx);
        assertTrue(listUrl.startsWith("https://eutils.ncbi.nlm.nih.gov/entrez/eutils/esearch.fcgi?"), listUrl);
        assertTrue(listUrl.contains("db=pubmed"));
        assertTrue(listUrl.contains("mindate="));
        assertTrue(listUrl.contains("maxdate="));

        Map<String, String> detailCtx = new LinkedHashMap<>(ctx);
        detailCtx.put("id", "12345");
        String detailUrl = service.buildUrl(cfg.baseUrl, cfg.detail.path, cfg.detail.query, detailCtx);
        assertTrue(detailUrl.contains("id=12345"), detailUrl);

        JsonNode detail = mapper.readTree(
                "{\"title\":\"A study on fever\",\"pubdate\":\"2026 Jan\",\"fulljournalname\":\"The Lancet\"}");
        ExternalKnowledgeService.PendingDoc doc = service.render(cfg, "12345", detail);

        assertNotNull(doc);
        assertEquals("A study on fever", doc.title());
        assertEquals("外部资料·PubMed", doc.category());
        assertTrue(doc.markdown().contains("# A study on fever"));
        assertTrue(doc.markdown().contains("The Lancet"));
        assertTrue(doc.markdown().contains("https://pubmed.ncbi.nlm.nih.gov/12345/"));
    }
}
