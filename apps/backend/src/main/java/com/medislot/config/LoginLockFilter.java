package com.medislot.config;

import com.medislot.service.LoginAttemptService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * 登录接口的失败锁定前置拦截：被锁定的手机号即使密码正确也直接拒绝。
 */
public class LoginLockFilter extends OncePerRequestFilter {

    private final LoginAttemptService loginAttemptService;

    public LoginLockFilter(LoginAttemptService loginAttemptService) {
        this.loginAttemptService = loginAttemptService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if ("POST".equalsIgnoreCase(request.getMethod()) && "/login".equals(request.getRequestURI())
                && loginAttemptService.isBlocked(request.getParameter("username"))) {
            response.sendRedirect("/login?locked");
            return;
        }
        chain.doFilter(request, response);
    }
}
