package com.medislot.service;

import com.medislot.entity.Role;
import dev.samstevens.totp.code.DefaultCodeGenerator;
import dev.samstevens.totp.code.DefaultCodeVerifier;
import dev.samstevens.totp.code.HashingAlgorithm;
import dev.samstevens.totp.exceptions.QrGenerationException;
import dev.samstevens.totp.qr.QrData;
import dev.samstevens.totp.qr.ZxingPngQrGenerator;
import dev.samstevens.totp.secret.DefaultSecretGenerator;
import dev.samstevens.totp.secret.SecretGenerator;
import dev.samstevens.totp.time.SystemTimeProvider;
import org.springframework.stereotype.Service;

import java.util.Base64;

/**
 * TOTP 两步验证（兼容腾讯身份验证器 / Google Authenticator 等标准 App）。
 *
 * <p>6 位数字、30 秒周期、SHA1，允许前后各 1 个周期的时间偏差。
 */
@Service
public class TwoFactorService {

    private static final String ISSUER = "MediSlot";

    private final SecretGenerator secretGenerator = new DefaultSecretGenerator();
    private final DefaultCodeVerifier codeVerifier = new DefaultCodeVerifier(
            new DefaultCodeGenerator(HashingAlgorithm.SHA1, 6), new SystemTimeProvider());
    private final ZxingPngQrGenerator qrGenerator = new ZxingPngQrGenerator();

    public TwoFactorService() {
        codeVerifier.setTimePeriod(30);
        codeVerifier.setAllowedTimePeriodDiscrepancy(1);
    }

    /** 该角色是否强制启用两步验证。 */
    public boolean isRequired(Role role) {
        return role == Role.ADMIN || role == Role.KB_MAINTAINER;
    }

    /** 生成新的 Base32 密钥。 */
    public String generateSecret() {
        return secretGenerator.generate();
    }

    /** 校验 6 位动态码。 */
    public boolean verify(String secret, String code) {
        if (secret == null || code == null || code.isBlank()) {
            return false;
        }
        return codeVerifier.isValidCode(secret, code.trim());
    }

    /** 生成可直接放入 img[src] 的二维码 Data URI（otpauth://）。 */
    public String qrDataUri(String account, String secret) {
        QrData data = new QrData.Builder()
                .label(account)
                .secret(secret)
                .issuer(ISSUER)
                .algorithm(HashingAlgorithm.SHA1)
                .digits(6)
                .period(30)
                .build();
        try {
            byte[] png = qrGenerator.generate(data);
            return "data:image/png;base64," + Base64.getEncoder().encodeToString(png);
        } catch (QrGenerationException e) {
            throw new IllegalStateException("二维码生成失败", e);
        }
    }
}
