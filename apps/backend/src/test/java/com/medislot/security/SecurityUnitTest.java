package com.medislot.security;

import com.medislot.entity.Role;
import com.medislot.exception.BusinessException;
import com.medislot.service.LoginAttemptService;
import com.medislot.service.TwoFactorService;
import com.medislot.service.UserService;
import dev.samstevens.totp.code.DefaultCodeGenerator;
import dev.samstevens.totp.code.HashingAlgorithm;
import dev.samstevens.totp.time.SystemTimeProvider;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 登录加固纯逻辑测试：TOTP 生成/校验、强制角色、密码策略、失败锁定。
 */
class SecurityUnitTest {

    @Test
    void totpSecretVerifiesOwnCode() throws Exception {
        TwoFactorService svc = new TwoFactorService();
        String secret = svc.generateSecret();
        long counter = new SystemTimeProvider().getTime() / 30;
        String code = new DefaultCodeGenerator(HashingAlgorithm.SHA1, 6).generate(secret, counter);

        assertTrue(svc.verify(secret, code));
        assertFalse(svc.verify(secret, "000000"));
        assertFalse(svc.verify(null, code));
        assertFalse(svc.verify(secret, null));
    }

    @Test
    void totpRequiredOnlyForPrivilegedRoles() {
        TwoFactorService svc = new TwoFactorService();
        assertTrue(svc.isRequired(Role.ADMIN));
        assertTrue(svc.isRequired(Role.KB_MAINTAINER));
        assertFalse(svc.isRequired(Role.DOCTOR));
        assertFalse(svc.isRequired(Role.PATIENT));
    }

    @Test
    void qrDataUriIsPngDataUri() {
        TwoFactorService svc = new TwoFactorService();
        String uri = svc.qrDataUri("13800000000", svc.generateSecret());
        assertTrue(uri.startsWith("data:image/png;base64,"));
    }

    @Test
    void passwordPolicyEnforced() {
        assertThrows(BusinessException.class, () -> UserService.validatePassword("short1"));
        assertThrows(BusinessException.class, () -> UserService.validatePassword("allletters"));
        assertThrows(BusinessException.class, () -> UserService.validatePassword("12345678"));
        assertThrows(BusinessException.class, () -> UserService.validatePassword(null));
        assertDoesNotThrow(() -> UserService.validatePassword("abcd1234"));
    }

    @Test
    void loginLockoutAfterFiveFailures() {
        LoginAttemptService svc = new LoginAttemptService();
        String phone = "13900000001";
        for (int i = 0; i < 4; i++) {
            svc.loginFailed(phone);
            assertFalse(svc.isBlocked(phone), "4 次失败不应锁定");
        }
        svc.loginFailed(phone);
        assertTrue(svc.isBlocked(phone), "第 5 次失败应锁定");
        svc.loginSucceeded(phone);
        assertFalse(svc.isBlocked(phone), "成功登录应清零");
    }
}
