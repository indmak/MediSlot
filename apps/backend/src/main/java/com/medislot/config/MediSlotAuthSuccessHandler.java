package com.medislot.config;

import com.medislot.entity.User;
import com.medislot.service.LoginAttemptService;
import com.medislot.service.TwoFactorService;
import com.medislot.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Collection;
import java.util.List;

/**
 * 登录成功后的分流：
 * <ul>
 *   <li>管理员 / 知识库维护员：强制进入两步验证（未绑定先去绑定，已绑定则验证动态码）；</li>
 *   <li>其余角色：按角色跳转到对应首页。</li>
 * </ul>
 */
@Component
public class MediSlotAuthSuccessHandler implements AuthenticationSuccessHandler {

    private final TwoFactorService twoFactorService;
    private final UserService userService;
    private final LoginAttemptService loginAttemptService;

    public MediSlotAuthSuccessHandler(TwoFactorService twoFactorService,
                                      UserService userService,
                                      LoginAttemptService loginAttemptService) {
        this.twoFactorService = twoFactorService;
        this.userService = userService;
        this.loginAttemptService = loginAttemptService;
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                        Authentication authentication) throws IOException {
        String phone = authentication.getName();
        loginAttemptService.loginSucceeded(phone);

        User user = userService.findByPhone(phone);
        if (twoFactorService.isRequired(user.getRole())) {
            boolean enabled = user.isTotpEnabled();
            HttpSession session = request.getSession(true);
            session.setAttribute(PreTwoFactorFilter.SESSION_PHONE, phone);
            session.setAttribute(PreTwoFactorFilter.SESSION_MODE,
                    enabled ? PreTwoFactorFilter.MODE_VERIFY : PreTwoFactorFilter.MODE_SETUP);

            // 降级为「待两步验证」身份，仅保留 ROLE_PRE_2FA
            Authentication preAuth = new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                    phone, null, List.of(new SimpleGrantedAuthority(PreTwoFactorFilter.AUTHORITY)));
            SecurityContext context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(preAuth);
            SecurityContextHolder.setContext(context);
            session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, context);

            response.sendRedirect(enabled ? "/login/2fa" : "/account/2fa");
            return;
        }
        response.sendRedirect(targetFor(authentication.getAuthorities()));
    }

    /** 按角色决定登录后的落地页。 */
    public static String targetFor(Collection<? extends GrantedAuthority> authorities) {
        boolean isAdmin = authorities.stream().anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));
        boolean isKb = authorities.stream().anyMatch(a -> "ROLE_KB_MAINTAINER".equals(a.getAuthority()));
        boolean isDoctor = authorities.stream().anyMatch(a -> "ROLE_DOCTOR".equals(a.getAuthority()));
        return isAdmin ? "/admin" : (isKb ? "/admin/knowledge" : (isDoctor ? "/doctor/today" : "/"));
    }
}
