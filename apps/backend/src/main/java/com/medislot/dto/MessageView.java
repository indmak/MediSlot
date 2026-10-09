package com.medislot.dto;

import com.medislot.entity.ConversationMessage;

import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 会话消息的 JSON 视图（供前端轮询/增量渲染）。
 */
public record MessageView(
        Long id,
        String senderType,
        String senderLabel,
        String messageType,
        String content,
        String reviewStatus,
        String reviewStatusLabel,
        String reviewNote,
        String time,
        List<AttachmentView> attachments
) {

    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("MM-dd HH:mm");

    public static MessageView of(ConversationMessage message) {
        return new MessageView(
                message.getId(),
                message.getSenderType().name(),
                message.getSenderType().getLabel(),
                message.getMessageType().name(),
                message.getContent(),
                message.getReviewStatus() == null ? null : message.getReviewStatus().name(),
                message.getReviewStatus() == null ? null : message.getReviewStatus().getLabel(),
                message.getReviewNote(),
                message.getCreatedAt() == null ? "" : TIME_FORMAT.format(message.getCreatedAt()),
                message.getAttachments().stream().map(AttachmentView::of).toList()
        );
    }
}
