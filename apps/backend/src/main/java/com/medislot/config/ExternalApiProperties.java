package com.medislot.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 外部 API 配置（示例：DeepSeek）。
 *
 * <p>密钥不写入代码或仓库，也不通过环境变量传递，而是以挂载文件形式提供：
 * <pre>
 *   /run/secrets/medislot/external/deepseek/apikey  ->  medislot.external.deepseek.apikey
 * </pre>
 * 由 {@code spring.config.import=optional:configtree:/run/secrets/} 注入。
 * 其余字段（base-url / model / timeout-seconds）可在配置文件中覆盖。
 */
@Component
@ConfigurationProperties(prefix = "medislot.external.deepseek")
public class ExternalApiProperties {

    /** DeepSeek API key；未配置时为空。 */
    private String apikey;

    /** API 基址（OpenAI 兼容）。 */
    private String baseUrl = "https://api.deepseek.com";

    /** 模型名；默认 deepseek-flash（快、便宜）。 */
    private String model = "deepseek-flash";

    /** 请求超时（秒）。 */
    private int timeoutSeconds = 30;

    public String getApikey() {
        return apikey;
    }

    public void setApikey(String apikey) {
        this.apikey = apikey;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public int getTimeoutSeconds() {
        return timeoutSeconds;
    }

    public void setTimeoutSeconds(int timeoutSeconds) {
        this.timeoutSeconds = timeoutSeconds;
    }

    /** 是否已配置密钥（避免在日志中输出密钥本身）。 */
    public boolean isConfigured() {
        return apikey != null && !apikey.isBlank();
    }
}
