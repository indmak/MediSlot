package com.medislot.config;

import com.medislot.service.LoginAttemptService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * 登录失败处理：累计失败次数，达到阈值后锁定并给出提示。
 */
@Component
public class MediSlotAuthFailureHandler implements AuthenticationFailureHandler {

    private final LoginAttemptService loginAttemptService;

    public MediSlotAuthFailureHandler(LoginAttemptService loginAttemptService) {
        this.loginAttemptService = loginAttemptService;
    }

    @Override
    public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response,
                                        AuthenticationException exception) throws IOException {
        String phone = request.getParameter("username");
        loginAttemptService.loginFailed(phone);
        response.sendRedirect(loginAttemptService.isBlocked(phone) ? "/login?locked" : "/login?error");
    }
}
