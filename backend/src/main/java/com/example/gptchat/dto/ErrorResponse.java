package com.example.gptchat.dto;

/**
 * 統一的錯誤回應。只有 status 和一句人看得懂的訊息，
 * 不含 stack trace 或上游供應商的原始回應。
 */
public record ErrorResponse(int status, String message) {
}
