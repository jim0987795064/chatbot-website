package com.example.gptchat.dto;

import java.util.List;

/**
 * OpenAI /v1/chat/completions 的回應格式。
 */
public record ChatCompletionResponse(
    List<Choice> choices
) {

    public record Choice(
        Message message
    ) {}

    public record Message(
        String role,
        String content
    ) {}
}
