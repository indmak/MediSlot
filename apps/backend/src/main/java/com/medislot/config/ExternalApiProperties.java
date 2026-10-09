package com.medislot.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 外部 API 密钥配置（示例：DeepSeek）。
 *
 * <p>密钥不写入代码或仓库，也不通过环境变量传递，而是以挂载文件形式提供：
 * <pre>
 *   /run/secrets/medislot/external/deepseek/apikey  ->  medislot.external.deepseek.apikey
 * </pre>
 * 由 {@code spring.config.import=optional:configtree:/run/secrets/} 注入。
 */
@Component
@ConfigurationProperties(prefix = "medislot.external.deepseek")
public class ExternalApiProperties {

    /** DeepSeek API key；未配置时为空。 */
    private String apikey;

    public String getApikey() {
        return apikey;
    }

    public void setApikey(String apikey) {
        this.apikey = apikey;
    }

    /** 是否已配置密钥（避免在日志中输出密钥本身）。 */
    public boolean isConfigured() {
        return apikey != null && !apikey.isBlank();
    }
}
