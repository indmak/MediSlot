package com.medislot.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Spring Security 表单登录 + 角色授权。
 *
 * <p>第一层（URL 级）在此配置；第二层（数据归属）在 service 里判断。
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .authorizeHttpRequests(auth -> auth
                        // 公开：首页、医生列表/详情、静态资源、登录注册
                        .requestMatchers("/", "/doctors", "/doctors/**",
                                "/css/**", "/js/**", "/images/**",
                                "/login", "/register", "/error", "/favicon.ico").permitAll()
                        // 医生工作台
                        .requestMatchers("/doctor/**").hasRole("DOCTOR")
                        // 患者预约
                        .requestMatchers("/appointments/**").hasRole("PATIENT")
                        // 支付（模拟/沙箱）
                        .requestMatchers("/payments/**").hasRole("PATIENT")
                        // 诊前咨询（患者 / 医生，归属在 service 校验）
                        .requestMatchers("/consultations/**", "/cases/**", "/attachments/**").authenticated()
                        // 管理后台
                        .requestMatchers("/admin/**").hasRole("ADMIN")
                        .anyRequest().authenticated()
                )
                .formLogin(form -> form
                        .loginPage("/login")
                        .loginProcessingUrl("/login")
                        .successHandler((request, response, authentication) -> {
                            boolean isAdmin = authentication.getAuthorities().stream()
                                    .anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));
                            boolean isDoctor = authentication.getAuthorities().stream()
                                    .anyMatch(a -> "ROLE_DOCTOR".equals(a.getAuthority()));
                            response.sendRedirect(isAdmin ? "/admin" : (isDoctor ? "/doctor/today" : "/"));
                        })
                        .failureUrl("/login?error")
                        .permitAll()
                )
                .logout(logout -> logout
                        .logoutUrl("/logout")
                        .logoutSuccessUrl("/")
                        .permitAll()
                );
        return http.build();
    }
}
