package com.medislot.web;

import com.medislot.entity.MessageAttachment;
import com.medislot.entity.User;
import com.medislot.exception.BusinessException;
import com.medislot.service.AttachmentService;
import com.medislot.service.UserService;
import org.springframework.core.io.Resource;
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
        String name = attachment.getOriginalName() == null ? "file" : attachment.getOriginalName();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + name + "\"")
                .contentType(mediaType)
                .body(resource);
    }
}
