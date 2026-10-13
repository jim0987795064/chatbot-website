package com.example.gptchat.service;

import com.example.gptchat.entity.ChatMessage;

import java.util.List;

/**
 * 聊天模型供應商的抽象，讓 ChatService 不綁定特定廠商。
 */
public interface ChatModelClient {

    /**
     * 帶完整對話歷史呼叫，讓模型有上下文。
     *
     * @param history oldest → newest 排序的訊息
     * @return 模型回覆的文字
     */
    String chat(List<ChatMessage> history);
}
