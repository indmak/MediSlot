package com.medislot.dto;

import com.medislot.entity.MessageAttachment;

/**
 * 附件视图（供前端渲染）。
 */
public record AttachmentView(Long id, String url, String name, boolean isImage) {

    public static AttachmentView of(MessageAttachment attachment) {
        return new AttachmentView(
                attachment.getId(),
                "/attachments/" + attachment.getId(),
                attachment.getOriginalName(),
                attachment.isImage()
        );
    }
}
