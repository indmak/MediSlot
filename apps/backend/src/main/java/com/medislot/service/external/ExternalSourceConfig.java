package com.medislot.service.external;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 外部知识接口来源的抓取配置（序列化为 {@code knowledge_source.config_json}）。
 *
 * <p>占位符：查询参数、路径、{@code computed} 与 {@code template} 中可用
 * <code>{{keyword}}</code>、<code>{{id}}</code>、<code>{{sinceDays}}</code>、
 * <code>{{sinceDate}}</code>（yyyy/MM/dd）、<code>{{today}}</code>、<code>{{sinceDateIso}}</code>、
 * <code>{{todayIso}}</code>，以及 {@code fields}/{@code computed} 中已解析出来的变量。
 */
public class ExternalSourceConfig {

    /** 接口基址，如 https://eutils.ncbi.nlm.nih.gov/entrez/eutils 。 */
    public String baseUrl;

    /** 关键词（可被 {{keyword}} 引用）。 */
    public String keyword;

    /** 抓取最近多少天（默认 365，符合“大模型知识盲区约一年”）。 */
    public Integer sinceDays = 365;

    /** 单次最多抓取多少条。 */
    public Integer maxItems = 30;

    /** 详情请求间隔（毫秒），用于限流（默认 350）。 */
    public Integer requestDelayMs = 350;

    /** 入库文档的分类（如“外部资料”）。 */
    public String category = "外部资料";

    public Auth auth;
    public Endpoint list;
    public Endpoint detail;

    /** 变量 -> detail 响应中的 JSON 路径（简单点路径，支持 a.b[0].c）。 */
    public Map<String, String> fields = new LinkedHashMap<>();

    /** 变量 -> 字面量模板（可引用 {{id}} 与 fields 变量），用于拼 URL 等。 */
    public Map<String, String> computed = new LinkedHashMap<>();

    /** Markdown 模板，支持 {{var}}。 */
    public String template;

    public static class Auth {
        /** 密钥文件路径（容器内，如 /run/secrets/medislot/external/kb/xxx/apikey）。 */
        public String secretPath;
        /** 请求头名，如 Authorization / X-Api-Key。 */
        public String header;
        /** 头值前缀，如 "Bearer "。 */
        public String scheme;
    }

    public static class Endpoint {
        /** 相对 baseUrl 的路径，如 /esearch.fcgi 。 */
        public String path;
        public Map<String, String> query = new LinkedHashMap<>();
        public Map<String, String> headers = new LinkedHashMap<>();
        /** list：结果数组的 JSON 路径，如 esearchresult.idlist 。 */
        public String itemsPath;
        /** detail：detail 响应中对象的 JSON 路径（可含 {{id}}），如 result.{{id}} 。 */
        public String responsePath;
    }
}
