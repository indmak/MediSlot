package com.medislot.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/**
 * 会话消息附件（图片 / 文件）。上传时先落库（message 为空），发送消息时再关联。
 */
@Entity
@Table(name = "message_attachment")
public class MessageAttachment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "message_id")
    private ConversationMessage message;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "conversation_id", nullable = false)
    private Conversation conversation;

    @Column(name = "uploader_id")
    private Long uploaderId;

    @Column(name = "original_name", length = 255)
    private String originalName;

    @Column(name = "stored_name", nullable = false, length = 100)
    private String storedName;

    @Column(name = "content_type", length = 100)
    private String contentType;

    @Column(nullable = false)
    private Long size;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    protected MessageAttachment() {
    }

    public MessageAttachment(Conversation conversation, Long uploaderId, String originalName,
                             String storedName, String contentType, Long size) {
        this.conversation = conversation;
        this.uploaderId = uploaderId;
        this.originalName = originalName;
        this.storedName = storedName;
        this.contentType = contentType;
        this.size = size;
    }

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }

    public boolean isImage() {
        return contentType != null && contentType.startsWith("image/");
    }

    public Long getId() {
        return id;
    }

    public ConversationMessage getMessage() {
        return message;
    }

    public void setMessage(ConversationMessage message) {
        this.message = message;
    }

    public Conversation getConversation() {
        return conversation;
    }

    public Long getUploaderId() {
        return uploaderId;
    }

    public String getOriginalName() {
        return originalName;
    }

    public String getStoredName() {
        return storedName;
    }

    public String getContentType() {
        return contentType;
    }

    public Long getSize() {
        return size;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
