package com.example.gptchat.controller;

import com.example.gptchat.dto.ChatResponse;
import com.example.gptchat.service.ChatService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ChatController.class)
class ChatControllerWiringTest {

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    ChatService chatService;

    @Test
    void postRoutesThroughChatService() throws Exception {

        given(chatService.chat(eq("hi")))
            .willReturn(new ChatResponse("hello there"));

        mockMvc.perform(post("/api/chat")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"message\":\"hi\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.answer").value("hello there"));

        verify(chatService).chat("hi");
    }

    @Test
    void blankMessageIsRejected() throws Exception {

        mockMvc.perform(post("/api/chat")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"message\":\"  \"}"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void deleteClearsHistory() throws Exception {

        mockMvc.perform(delete("/api/chat/history"))
            .andExpect(status().isOk());

        verify(chatService).clearHistory();
    }
}
