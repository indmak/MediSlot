package com.medislot.service;

import com.medislot.exception.BusinessException;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 上传路径安全：扩展名白名单化 + 目标必须落在存储目录内。
 */
class UploadSupportTest {

    @Test
    void keepsSafeExtensions() {
        assertEquals(".pdf", UploadSupport.safeExtension("报告.PDF"));
        assertEquals(".png", UploadSupport.safeExtension("a.png"));
        assertEquals("", UploadSupport.safeExtension("noext"));
        assertEquals("", UploadSupport.safeExtension(null));
    }

    @Test
    void stripsTraversalFromFilename() {
        // 恶意文件名：扩展名里带分隔符 / 上跳
        assertEquals("", UploadSupport.safeExtension("a.txt/../../../../etc/passwd"));
        assertEquals("", UploadSupport.safeExtension("x.//..//..//evil"));
        assertEquals("", UploadSupport.safeExtension("x.p/../../../y"));
    }

    @Test
    void resolveWithinRejectsEscape() {
        Path base = Path.of("/tmp/medislot/uploads").toAbsolutePath().normalize();
        Path ok = UploadSupport.resolveWithin(base, "abc123.png");
        assertTrue(ok.startsWith(base));
        assertThrows(BusinessException.class, () -> UploadSupport.resolveWithin(base, "../secret.txt"));
        assertThrows(BusinessException.class, () -> UploadSupport.resolveWithin(base, "a/../../etc/passwd"));
    }
}
