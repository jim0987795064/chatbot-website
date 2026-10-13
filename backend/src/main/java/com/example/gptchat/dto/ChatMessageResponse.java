package com.example.gptchat.dto;

import com.example.gptchat.entity.ChatMessage;

import java.time.LocalDateTime;

/**
 * 對外的訊息格式。和 ChatMessage entity 分開，
 * 之後改資料庫 schema 才不會直接打到前端。
 */
public record ChatMessageResponse(
    Long id,
    String role,
    String content,
    LocalDateTime createdAt
) {

    public static ChatMessageResponse from(ChatMessage message) {
        return new ChatMessageResponse(
            message.getId(),
            message.getRole(),
            message.getContent(),
            message.getCreatedAt()
        );
    }
}
