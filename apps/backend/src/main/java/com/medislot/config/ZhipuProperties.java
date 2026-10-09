package com.medislot.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 智谱（BigModel）嵌入配置。密钥以挂载文件提供：
 * <pre>/run/secrets/medislot/external/zhipu/apikey -> medislot.external.zhipu.apikey</pre>
 */
@Component
@ConfigurationProperties(prefix = "medislot.external.zhipu")
public class ZhipuProperties {

    private String apikey;
    private String baseUrl = "https://open.bigmodel.cn/api/paas/v4";
    private String model = "embedding-3";
    private int dimensions = 1024;

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

    public int getDimensions() {
        return dimensions;
    }

    public void setDimensions(int dimensions) {
        this.dimensions = dimensions;
    }

    public boolean isConfigured() {
        return apikey != null && !apikey.isBlank();
    }
}
