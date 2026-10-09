package com.medislot.service;

import com.medislot.entity.Conversation;
import com.medislot.entity.ConversationMessage;
import com.medislot.entity.ConversationScope;
import com.medislot.entity.MessageAttachment;
import com.medislot.entity.User;
import com.medislot.exception.BusinessException;
import com.medislot.repository.ConversationRepository;
import com.medislot.repository.MessageAttachmentRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Set;
import java.util.UUID;

/**
 * 会话附件（图片 / 文件）业务：落盘 + 落库，发送消息时关联。
 */
@Service
public class AttachmentService {

    private static final long MAX_SIZE = 5L * 1024 * 1024;

    /** 仅允许栅格图片与 PDF；显式排除 SVG 等可执行脚本的类型，避免存储型 XSS。 */
    private static final Set<String> ALLOWED_TYPES = Set.of(
            "image/jpeg", "image/png", "image/gif", "image/webp", "application/pdf");

    private final MessageAttachmentRepository repository;
    private final ConversationRepository conversationRepository;
    private final Path storageDir;

    public AttachmentService(MessageAttachmentRepository repository,
                             ConversationRepository conversationRepository,
                             @Value("${medislot.upload.dir:uploads}") String uploadDir) {
        this.repository = repository;
        this.conversationRepository = conversationRepository;
        this.storageDir = Paths.get(uploadDir).toAbsolutePath().normalize();
        try {
            Files.createDirectories(storageDir);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Transactional
    public MessageAttachment store(Long conversationId, User user, MultipartFile file) {
        Conversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new BusinessException("会话不存在"));
        assertParticipant(conversation, user);

        if (file == null || file.isEmpty()) {
            throw new BusinessException("请选择文件");
        }
        if (file.getSize() > MAX_SIZE) {
            throw new BusinessException("文件过大（上限 5MB）");
        }
        String contentType = normalizeContentType(file.getContentType());
        if (!ALLOWED_TYPES.contains(contentType)) {
            throw new BusinessException("仅支持 JPG / PNG / GIF / WebP 图片或 PDF");
        }

        String original = file.getOriginalFilename();
        String stored = UUID.randomUUID().toString().replace("-", "") + UploadSupport.safeExtension(original);
        try {
            Files.copy(file.getInputStream(), UploadSupport.resolveWithin(storageDir, stored),
                    StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new BusinessException("文件保存失败");
        }

        MessageAttachment attachment = new MessageAttachment(
                conversation, user.getId(), original, stored, contentType, file.getSize());
        return repository.save(attachment);
    }

    @Transactional(readOnly = true)
    public MessageAttachment getRequired(Long id) {
        return repository.findById(id).orElseThrow(() -> new BusinessException("附件不存在"));
    }

    @Transactional(readOnly = true)
    public boolean canAccess(MessageAttachment attachment, User user) {
        return isParticipant(attachment.getConversation(), user);
    }

    public Resource loadResource(MessageAttachment attachment) {
        Path path = UploadSupport.resolveWithin(storageDir, attachment.getStoredName());
        if (!Files.exists(path)) {
            throw new BusinessException("文件不存在");
        }
        return new FileSystemResource(path);
    }

    private static String normalizeContentType(String contentType) {
        if (contentType == null) {
            return "";
        }
        int semi = contentType.indexOf(';');
        return (semi >= 0 ? contentType.substring(0, semi) : contentType).trim().toLowerCase();
    }

    /** 发送消息时把已上传的附件关联到消息。 */
    @Transactional
    public void link(Long attachmentId, ConversationMessage message, User user) {
        MessageAttachment attachment = getRequired(attachmentId);
        if (!attachment.getConversation().getId().equals(message.getConversation().getId())) {
            throw new BusinessException("附件与会话不匹配");
        }
        if (attachment.getUploaderId() != null && !attachment.getUploaderId().equals(user.getId())) {
            throw new BusinessException("无权使用该附件");
        }
        attachment.setMessage(message);
        repository.save(attachment);
    }

    private void assertParticipant(Conversation conversation, User user) {
        if (!isParticipant(conversation, user)) {
            throw new BusinessException("无权访问该会话");
        }
    }

    private boolean isParticipant(Conversation conversation, User user) {
        if (conversation.getScope() == ConversationScope.GROUP) {
            boolean patient = conversation.getAppointment().getPatient().getId().equals(user.getId());
            boolean doctor = conversation.getAppointment().getSchedule().getDoctor().getUser().getId().equals(user.getId());
            return patient || doctor;
        }
        return conversation.getDoctor() != null && conversation.getDoctor().getUser().getId().equals(user.getId());
    }
}
