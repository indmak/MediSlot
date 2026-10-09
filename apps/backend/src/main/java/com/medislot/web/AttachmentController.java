package com.medislot.web;

import com.medislot.entity.MessageAttachment;
import com.medislot.entity.User;
import com.medislot.exception.BusinessException;
import com.medislot.service.AttachmentService;
import com.medislot.service.UserService;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 会话附件上传 / 下载。
 */
@Controller
@RequestMapping("/attachments")
public class AttachmentController {

    private final AttachmentService attachmentService;
    private final UserService userService;

    public AttachmentController(AttachmentService attachmentService, UserService userService) {
        this.attachmentService = attachmentService;
        this.userService = userService;
    }

    @PostMapping
    @ResponseBody
    public Map<String, Object> upload(@RequestParam("file") MultipartFile file,
                                      @RequestParam Long conversationId,
                                      Authentication authentication) {
        User user = userService.findByPhone(authentication.getName());
        try {
            MessageAttachment attachment = attachmentService.store(conversationId, user, file);
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("ok", true);
            result.put("id", attachment.getId());
            result.put("name", attachment.getOriginalName());
            result.put("url", "/attachments/" + attachment.getId());
            result.put("isImage", attachment.isImage());
            return result;
        } catch (BusinessException e) {
            return Map.of("ok", false, "error", e.getMessage());
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<Resource> download(@PathVariable Long id, Authentication authentication) {
        User user = userService.findByPhone(authentication.getName());
        MessageAttachment attachment = attachmentService.getRequired(id);
        if (!attachmentService.canAccess(attachment, user)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        Resource resource = attachmentService.loadResource(attachment);
        MediaType mediaType;
        try {
            mediaType = MediaType.parseMediaType(attachment.getContentType());
        } catch (Exception e) {
            mediaType = MediaType.APPLICATION_OCTET_STREAM;
        }
        // 图片内联展示（聊天内 <img>）；其它一律作为附件下载，避免在浏览器中直接执行
        boolean inline = mediaType.getType().equals("image");
        ContentDisposition disposition = ContentDisposition.builder(inline ? "inline" : "attachment")
                .filename(safeFilename(attachment.getOriginalName()), StandardCharsets.UTF_8)
                .build();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .header("X-Content-Type-Options", "nosniff")
                .contentType(mediaType)
                .body(resource);
    }

    /** 去掉路径分隔符与控制字符，避免响应头注入 / 文件名穿越。 */
    private static String safeFilename(String name) {
        if (name == null || name.isBlank()) {
            return "file";
        }
        String cleaned = name.replaceAll("[\\\\/\\r\\n\\t\"\\x00-\\x1f]", "_").trim();
        if (cleaned.isBlank() || cleaned.equals(".") || cleaned.equals("..")) {
            return "file";
        }
        return cleaned.length() > 200 ? cleaned.substring(0, 200) : cleaned;
    }
}
