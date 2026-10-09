package com.medislot.security;

import com.medislot.entity.Role;
import com.medislot.entity.User;
import com.medislot.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

/**
 * 两步验证登录门禁的 Web 层测试。
 */
@SpringBootTest
@ActiveProfiles("test")
class TwoFactorWebTest {

    @Autowired
    private WebApplicationContext context;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity())
                .build();
    }

    @Test
    void twoFactorPageRequiresLogin() throws Exception {
        mockMvc.perform(get("/login/2fa"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }

    @Test
    void account2faRequiresLogin() throws Exception {
        mockMvc.perform(get("/account/2fa"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }

    @Test
    @WithMockUser(username = "13700008888", roles = "DOCTOR")
    void account2faRendersForLoggedInUser() throws Exception {
        userRepository.save(new User("13700008888", passwordEncoder.encode("doctor123"), "两步测试医生", Role.DOCTOR));

        mockMvc.perform(get("/account/2fa"))
                .andExpect(status().isOk())
                .andExpect(view().name("account/two-factor"))
                .andExpect(content().string(containsString("两步验证")))
                .andExpect(content().string(containsString("data:image/png;base64,")));
    }

    @Test
    void adminLoginWithout2faRedirectsToSetup() throws Exception {
        userRepository.save(new User("13000009999", passwordEncoder.encode("admin123"), "两步测试管理员", Role.ADMIN));

        mockMvc.perform(post("/login").with(csrf())
                        .param("username", "13000009999")
                        .param("password", "admin123"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/account/2fa"));
    }
}
