package com.example.gptchat.service;

import com.example.gptchat.entity.ChatMessage;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 用本機 stub server 跑完整路徑：
 * 送出的 JSON 格式、Authorization header、回應解析。
 */
class OpenAIServiceHttpTest {

    private HttpServer server;
    private final AtomicReference<String> capturedBody = new AtomicReference<>();
    private final AtomicReference<String> capturedAuth = new AtomicReference<>();
    private String responseJson;
    private int responseStatus = 200;

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/chat/completions", exchange -> {
            capturedAuth.set(exchange.getRequestHeaders().getFirst("Authorization"));
            capturedBody.set(new String(
                exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));

            byte[] out = responseJson.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(responseStatus, out.length);
            exchange.getResponseBody().write(out);
            exchange.close();
        });
        server.start();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    private OpenAIService serviceUnderTest() {
        String url = "http://127.0.0.1:" + server.getAddress().getPort()
            + "/v1/chat/completions";
        return new OpenAIService("sk-test-key", "gpt-4o-mini", url, 5, 5);
    }

    @Test
    void sendsHistoryAndParsesReply() {
        responseJson = """
            {"choices":[{"message":{"role":"assistant","content":"你好！"}}]}
            """;

        String reply = serviceUnderTest().chat(List.of(
            new ChatMessage("user", "嗨"),
            new ChatMessage("assistant", "你好"),
            new ChatMessage("user", "再說一次")
        ));

        assertThat(reply).isEqualTo("你好！");

        assertThat(capturedAuth.get()).isEqualTo("Bearer sk-test-key");

        String body = capturedBody.get();
        assertThat(body).contains("\"model\":\"gpt-4o-mini\"");
        assertThat(body).contains("\"stream\":false");
        // 三則訊息、順序與 role 都要保留
        assertThat(body).contains("\"role\":\"user\"");
        assertThat(body).contains("\"role\":\"assistant\"");
        assertThat(body.indexOf("嗨"))
            .isLessThan(body.indexOf("再說一次"));
    }

    @Test
    void rejectsEmptyChoices() {
        responseJson = "{\"choices\":[]}";

        OpenAIService service = serviceUnderTest();
        List<ChatMessage> history = List.of(new ChatMessage("user", "嗨"));

        assertThatThrownBy(() -> service.chat(history))
            .isInstanceOf(ChatModelException.class)
            .hasMessageContaining("OpenAI");
    }

    @Test
    void wrapsUpstreamErrorStatusWithoutLeakingResponseBody() {
        responseStatus = 429;
        responseJson =
            "{\"error\":{\"message\":\"quota exceeded for org-SECRET\"}}";

        OpenAIService service = serviceUnderTest();
        List<ChatMessage> history = List.of(new ChatMessage("user", "嗨"));

        assertThatThrownBy(() -> service.chat(history))
            .isInstanceOf(ChatModelException.class)
            .hasMessageContaining("429")
            // 上游的 body 可能含帳務/組織資訊，不能出現在例外訊息裡
            .hasMessageNotContaining("org-SECRET");
    }
}
