package com.example.gptchat.controller;

import com.example.gptchat.dto.ChatMessageResponse;
import com.example.gptchat.dto.ChatRequest;
import com.example.gptchat.dto.ChatResponse;
import com.example.gptchat.service.ChatService;

import jakarta.validation.Valid;

import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private final ChatService chatService;

    public ChatController(ChatService chatService) {

        this.chatService = chatService;
    }

    @PostMapping
    public ChatResponse chat(
        @Valid @RequestBody ChatRequest request) {

        return chatService.chat(request.message());
    }

    @GetMapping("/history")
    public List<ChatMessageResponse> history() {

        return chatService.getHistory()
            .stream()
            .map(ChatMessageResponse::from)
            .toList();
    }

    @DeleteMapping("/history")
    public void clearHistory() {

        chatService.clearHistory();
    }
}
