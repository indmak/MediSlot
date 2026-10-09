package com.medislot.web;

import com.medislot.config.MediSlotAuthSuccessHandler;
import com.medislot.config.PreTwoFactorFilter;
import com.medislot.entity.User;
import com.medislot.service.TwoFactorService;
import com.medislot.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * 两步验证：登录时校验动态码（{@code /login/2fa}）与账号内绑定（{@code /account/2fa}）。
 */
@Controller
public class TwoFactorController {

    private static final String PENDING_SECRET = "MEDISLOT_2FA_PENDING_SECRET";

    private final TwoFactorService twoFactorService;
    private final UserService userService;

    public TwoFactorController(TwoFactorService twoFactorService, UserService userService) {
        this.twoFactorService = twoFactorService;
        this.userService = userService;
    }

    /** 登录第二步：输入动态码。 */
    @GetMapping("/login/2fa")
    public String verifyPage(Authentication auth) {
        return isPre2fa(auth) ? "auth/two-factor" : "redirect:/login";
    }

    @PostMapping("/login/2fa")
    public String verify(@RequestParam String code, HttpSession session,
                         HttpServletRequest request, HttpServletResponse response) {
        String phone = (String) session.getAttribute(PreTwoFactorFilter.SESSION_PHONE);
        if (phone == null) {
            return "redirect:/login";
        }
        User user = userService.findByPhone(phone);
        if (!twoFactorService.verify(user.getTotpSecret(), code)) {
            return "redirect:/login/2fa?error";
        }
        return completeLogin(request, response, phone);
    }

    /** 账号内绑定 / 查看两步验证状态。 */
    @GetMapping("/account/2fa")
    public String setupPage(HttpSession session, Authentication auth, Model model) {
        String phone = resolvePhone(session, auth);
        if (phone == null) {
            return "redirect:/login";
        }
        User user = userService.findByPhone(phone);
        model.addAttribute("phone", phone);
        model.addAttribute("enabled", user.isTotpEnabled());
        model.addAttribute("required", twoFactorService.isRequired(user.getRole()));
        if (!user.isTotpEnabled()) {
            String secret = (String) session.getAttribute(PENDING_SECRET);
            if (secret == null) {
                secret = twoFactorService.generateSecret();
                session.setAttribute(PENDING_SECRET, secret);
            }
            model.addAttribute("secret", secret);
            model.addAttribute("qrDataUri", twoFactorService.qrDataUri(phone, secret));
        }
        return "account/two-factor";
    }

    /** 校验动态码后启用两步验证；若来自强制登录流程则直接完成登录。 */
    @PostMapping("/account/2fa")
    public String enable(@RequestParam String code, HttpSession session,
                         HttpServletRequest request, HttpServletResponse response) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String phone = resolvePhone(session, auth);
        String secret = (String) session.getAttribute(PENDING_SECRET);
        if (phone == null || secret == null || !twoFactorService.verify(secret, code)) {
            return "redirect:/account/2fa?error";
        }
        User user = userService.findByPhone(phone);
        user.setTotpSecret(secret);
        user.setTotpEnabled(true);
        userService.updateTotp(user);
        session.removeAttribute(PENDING_SECRET);

        if (isPre2fa(auth)) {
            return completeLogin(request, response, phone);
        }
        return "redirect:/account/2fa?enabled";
    }

    /** 关闭两步验证（强制角色不允许关闭）。 */
    @PostMapping("/account/2fa/disable")
    public String disable(HttpSession session, Authentication auth) {
        String phone = resolvePhone(session, auth);
        if (phone == null) {
            return "redirect:/login";
        }
        User user = userService.findByPhone(phone);
        if (twoFactorService.isRequired(user.getRole())) {
            return "redirect:/account/2fa?required";
        }
        user.setTotpEnabled(false);
        user.setTotpSecret(null);
        userService.updateTotp(user);
        return "redirect:/account/2fa?disabled";
    }

    private boolean isPre2fa(Authentication auth) {
        return auth != null && auth.getAuthorities().stream()
                .anyMatch(a -> PreTwoFactorFilter.AUTHORITY.equals(a.getAuthority()));
    }

    private String resolvePhone(HttpSession session, Authentication auth) {
        String phone = (String) session.getAttribute(PreTwoFactorFilter.SESSION_PHONE);
        if (phone != null) {
            return phone;
        }
        return auth != null && auth.isAuthenticated() && !"anonymousUser".equals(auth.getName())
                ? auth.getName() : null;
    }

    /** 完成登录：写入正式身份并跳转到角色首页。 */
    private String completeLogin(HttpServletRequest request, HttpServletResponse response, String phone) {
        UserDetails details = userService.loadUserByUsername(phone);
        Authentication authentication = new UsernamePasswordAuthenticationToken(details, null, details.getAuthorities());
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        HttpSession session = request.getSession(true);
        session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, context);
        session.removeAttribute(PreTwoFactorFilter.SESSION_PHONE);
        session.removeAttribute(PreTwoFactorFilter.SESSION_MODE);
        return "redirect:" + MediSlotAuthSuccessHandler.targetFor(details.getAuthorities());
    }
}
