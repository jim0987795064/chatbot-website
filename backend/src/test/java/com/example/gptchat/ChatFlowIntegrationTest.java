package com.example.gptchat;

import com.example.gptchat.repository.ChatMessageRepository;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * 真的跑過 controller → service → repository(H2) → model client 的完整路徑，
 * 包含 GlobalExceptionHandler 的錯誤對應。上游 OpenAI 用本機 stub 取代。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ChatFlowIntegrationTest {

    private static final HttpServer STUB;
    private static volatile int stubStatus = 200;
    private static volatile String stubBody = "";

    static {
        try {
            STUB = HttpServer.create(
                new InetSocketAddress("127.0.0.1", 0), 0);
        } catch (IOException exception) {
            throw new IllegalStateException(exception);
        }

        STUB.createContext("/v1/chat/completions", exchange -> {
            byte[] out = stubBody.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders()
                .add("Content-Type", "application/json");
            exchange.sendResponseHeaders(stubStatus, out.length);
            exchange.getResponseBody().write(out);
            exchange.close();
        });

        STUB.start();
    }

    @DynamicPropertySource
    static void stubOpenAi(DynamicPropertyRegistry registry) {
        registry.add("openai.api-url", () ->
            "http://127.0.0.1:" + STUB.getAddress().getPort()
                + "/v1/chat/completions");
    }

    @AfterAll
    static void stopStub() {
        STUB.stop(0);
    }

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ChatMessageRepository repository;

    @BeforeEach
    void reset() {
        repository.deleteAll();
        stubStatus = 200;
        stubBody =
            "{\"choices\":[{\"message\":"
                + "{\"role\":\"assistant\",\"content\":\"你好！\"}}]}";
    }

    @Test
    void storesExchangeAndServesItBackAsDto() throws Exception {

        mockMvc.perform(post("/api/chat")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"message\":\"嗨\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.answer").value("你好！"));

        assertThat(repository.count()).isEqualTo(2);

        mockMvc.perform(get("/api/chat/history"))
            .andExpect(status().isOk())
            // oldest → newest，且是 DTO 欄位而非 entity
            .andExpect(jsonPath("$[0].role").value("user"))
            .andExpect(jsonPath("$[0].content").value("嗨"))
            .andExpect(jsonPath("$[0].createdAt").exists())
            .andExpect(jsonPath("$[1].role").value("assistant"))
            .andExpect(jsonPath("$[1].content").value("你好！"));
    }

    @Test
    void upstreamFailureReturns502AndPersistsNothing() throws Exception {

        stubStatus = 429;
        stubBody = "{\"error\":{\"message\":\"quota exceeded org-SECRET\"}}";

        mockMvc.perform(post("/api/chat")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"message\":\"嗨\"}"))
            .andExpect(status().isBadGateway())
            .andExpect(jsonPath("$.status").value(502))
            .andExpect(jsonPath("$.message")
                .value("Upstream chat model is unavailable. Please try again."))
            // 上游 body 不能外洩給前端
            .andExpect(content().string(
                org.hamcrest.Matchers.not(
                    org.hamcrest.Matchers.containsString("org-SECRET"))));

        // 模型失敗不該留下半套資料
        assertThat(repository.count()).isZero();
    }

    @Test
    void blankMessageReturnsStructuredBadRequest() throws Exception {

        mockMvc.perform(post("/api/chat")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"message\":\"   \"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.status").value(400));

        assertThat(repository.count()).isZero();
    }

    @Test
    void deleteHistoryRemovesEverything() throws Exception {

        mockMvc.perform(post("/api/chat")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"message\":\"嗨\"}"))
            .andExpect(status().isOk());

        assertThat(repository.count()).isEqualTo(2);

        mockMvc.perform(delete("/api/chat/history"))
            .andExpect(status().isOk());

        /*
         * 清除要是真的清乾淨 —— 舊版還會在 chat_history 留一份
         */
        assertThat(repository.count()).isZero();

        mockMvc.perform(get("/api/chat/history"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$").isEmpty());
    }
}
