package com.medislot.service;

import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 登录失败锁定（进程内计数，单实例部署足够；多实例需换 Redis）。
 *
 * <p>同一手机号连续失败 {@value #MAX_ATTEMPTS} 次后锁定 {@code LOCK_DURATION} 分钟，
 * 成功登录即清零。
 */
@Service
public class LoginAttemptService {

    private static final int MAX_ATTEMPTS = 5;
    private static final Duration LOCK_DURATION = Duration.ofMinutes(15);

    private final Map<String, Attempt> attempts = new ConcurrentHashMap<>();

    public boolean isBlocked(String key) {
        Attempt a = attempts.get(normalize(key));
        if (a == null) {
            return false;
        }
        if (a.lockedUntil == null) {
            return false;
        }
        if (Instant.now().isBefore(a.lockedUntil)) {
            return true;
        }
        attempts.remove(normalize(key));
        return false;
    }

    public void loginFailed(String key) {
        String k = normalize(key);
        if (k == null) {
            return;
        }
        Attempt a = attempts.computeIfAbsent(k, x -> new Attempt());
        a.count++;
        if (a.count >= MAX_ATTEMPTS) {
            a.lockedUntil = Instant.now().plus(LOCK_DURATION);
        }
    }

    public void loginSucceeded(String key) {
        attempts.remove(normalize(key));
    }

    private String normalize(String key) {
        return key == null ? null : key.trim();
    }

    private static final class Attempt {
        private int count;
        private Instant lockedUntil;
    }
}
