package com.medislot.service;

import com.medislot.exception.BusinessException;

import java.nio.file.Path;

/**
 * 上传文件的安全处理：扩展名白名单化 + 目标路径约束在存储目录内（防路径穿越）。
 */
final class UploadSupport {

    private UploadSupport() {
    }

    /**
     * 从原始文件名提取安全扩展名：仅允许 1~10 位字母数字，否则返回空串。
     * 避免 {@code a.txt/../../x} 这类文件名把分隔符带入存储路径。
     */
    static String safeExtension(String originalFilename) {
        if (originalFilename == null) {
            return "";
        }
        int dot = originalFilename.lastIndexOf('.');
        if (dot < 0) {
            return "";
        }
        String ext = originalFilename.substring(dot + 1);
        return ext.matches("[A-Za-z0-9]{1,10}") ? "." + ext.toLowerCase() : "";
    }

    /** 解析相对路径并确保仍位于 base 目录内。 */
    static Path resolveWithin(Path base, String relative) {
        Path resolved = base.resolve(relative).normalize();
        if (!resolved.startsWith(base)) {
            throw new BusinessException("非法文件路径");
        }
        return resolved;
    }
}
