package com.medislot.web;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 知识库来源管理页与知识库列表来源筛选。
 */
@SpringBootTest
@ActiveProfiles("test")
class KnowledgeSourceWebTest {

    @Autowired
    private WebApplicationContext context;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity())
                .build();
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void sourcesPageRenders() throws Exception {
        mockMvc.perform(get("/admin/knowledge/sources"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("知识库来源")))
                .andExpect(content().string(containsString("人工上传")))
                .andExpect(content().string(containsString("诊前咨询")));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void knowledgePageHasSourceFilter() throws Exception {
        mockMvc.perform(get("/admin/knowledge"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("按来源筛选")));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void newExternalSourceFormRenders() throws Exception {
        mockMvc.perform(get("/admin/knowledge/sources/new"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("新建外部来源")));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void pubmedPresetPrefillsForm() throws Exception {
        mockMvc.perform(get("/admin/knowledge/sources/new").param("preset", "pubmed"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("eutils.ncbi.nlm.nih.gov")));
    }
}
