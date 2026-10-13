package com.example.gptchat.service;

/**
 * 上游模型供應商呼叫失敗（連線、timeout、非 2xx、回應格式不符）。
 *
 * <p>刻意不把供應商的 response body 塞進 message —— 那可能含組織
 * 或帳務資訊，不該順著錯誤回應流到前端。
 */
public class ChatModelException extends RuntimeException {

    public ChatModelException(String message) {
        super(message);
    }

    public ChatModelException(String message, Throwable cause) {
        super(message, cause);
    }
}
