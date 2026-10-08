package com.medislot;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * 应用上下文加载测试（使用 H2 内存库，无需 MySQL）。
 */
@SpringBootTest
@ActiveProfiles("test")
class MediSlotApplicationTests {

    @Test
    void contextLoads() {
    }
}
