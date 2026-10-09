package com.medislot.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * 登录前的「待两步验证」门禁。
 *
 * <p>密码校验通过但尚未完成 TOTP 验证的用户，只持有 {@link #AUTHORITY}，
 * 仅允许访问两步验证相关页面，其余请求一律重定向回验证/绑定页。
 */
public class PreTwoFactorFilter extends OncePerRequestFilter {

    public static final String SESSION_PHONE = "MEDISLOT_PRE_2FA_PHONE";
    public static final String SESSION_MODE = "MEDISLOT_PRE_2FA_MODE";
    public static final String MODE_VERIFY = "VERIFY";
    public static final String MODE_SETUP = "SETUP";
    public static final String AUTHORITY = "ROLE_PRE_2FA";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getAuthorities().stream().anyMatch(a -> AUTHORITY.equals(a.getAuthority()))
                && !allowed(request.getRequestURI())) {
            HttpSession session = request.getSession(false);
            String mode = session == null ? null : (String) session.getAttribute(SESSION_MODE);
            response.sendRedirect(MODE_SETUP.equals(mode) ? "/account/2fa" : "/login/2fa");
            return;
        }
        chain.doFilter(request, response);
    }

    private boolean allowed(String path) {
        return path.equals("/login/2fa") || path.equals("/account/2fa")
                || path.startsWith("/account/2fa/") || path.equals("/logout")
                || path.startsWith("/css/") || path.startsWith("/js/") || path.startsWith("/images/")
                || path.equals("/favicon.ico") || path.equals("/error");
    }
}
