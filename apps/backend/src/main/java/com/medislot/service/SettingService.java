package com.medislot.service;

import com.medislot.entity.AppSetting;
import com.medislot.repository.AppSettingRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 通用系统设置（管理员设置中心）。键值对，带内置默认值兜底。
 */
@Service
public class SettingService {

    private final AppSettingRepository repository;

    /** 内置默认值（库中不存在时使用）。 */
    private static final Map<String, String> DEFAULTS = new LinkedHashMap<>();

    static {
        DEFAULTS.put("consultation.enabled", "true");
        DEFAULTS.put("consultation.max-messages", "30");
        DEFAULTS.put("consultation.rate-limit-seconds", "3");
        DEFAULTS.put("consultation.max-history", "20");
        DEFAULTS.put("kb.consultation.auto-sync", "true");
        DEFAULTS.put("kb.consultation.min-messages", "4");
    }

    public SettingService(AppSettingRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public String get(String key) {
        return repository.findById(key)
                .map(AppSetting::getSettingValue)
                .orElseGet(() -> DEFAULTS.get(key));
    }

    public int getInt(String key) {
        String value = get(key);
        if (value == null) {
            return 0;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    public boolean getBoolean(String key) {
        return Boolean.parseBoolean(get(key));
    }

    @Transactional
    public void set(String key, String value) {
        AppSetting setting = repository.findById(key)
                .orElseGet(() -> new AppSetting(key, value, ""));
        setting.setSettingValue(value);
        repository.save(setting);
    }

    @Transactional(readOnly = true)
    public List<AppSetting> list() {
        return repository.findAllByOrderBySettingKeyAsc();
    }

    public Map<String, String> defaults() {
        return DEFAULTS;
    }
}
