package com.example.gptchat.service;

import com.example.gptchat.dto.ChatCompletionResponse;
import com.example.gptchat.entity.ChatMessage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.List;
import java.util.Map;

@Service
public class OpenAIService implements ChatModelClient {

    private final RestClient restClient;
    private final String model;

    public OpenAIService(
        @Value("${openai.api-key}") String apiKey,
        @Value("${openai.model}") String model,
        @Value("${openai.api-url}") String apiUrl,
        @Value("${openai.connect-timeout-seconds:10}") long connectTimeoutSeconds,
        @Value("${openai.read-timeout-seconds:60}") long readTimeoutSeconds) {

        this.model = model;

        /*
         * 沒有 timeout 的話，上游一卡住 servlet thread 就無限期被占住，
         * 幾個請求就能把 thread pool 吃光讓整個服務無回應。
         */
        SimpleClientHttpRequestFactory requestFactory =
            new SimpleClientHttpRequestFactory();

        requestFactory.setConnectTimeout(
            Duration.ofSeconds(connectTimeoutSeconds));
        requestFactory.setReadTimeout(
            Duration.ofSeconds(readTimeoutSeconds));

        this.restClient = RestClient.builder()
            .baseUrl(apiUrl)
            .requestFactory(requestFactory)
            .defaultHeader(
                "Authorization",
                "Bearer " + apiKey
            )
            .build();
    }

    @Override
    public String chat(List<ChatMessage> history) {

        List<Map<String, Object>> messages = history.stream()
            .map(m -> Map.<String, Object>of(
                "role", m.getRole(),
                "content", m.getContent()
            ))
            .toList();

        Map<String, Object> request = Map.of(
            "model", model,
            "messages", messages,
            "stream", false
        );

        ChatCompletionResponse response;

        try {
            response = restClient.post()
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                /*
                 * 只保留狀態碼。body 可能含帳務或組織資訊，
                 * 不該順著錯誤回應流到前端。
                 */
                .onStatus(status -> status.isError(), (req, res) -> {
                    throw new ChatModelException(
                        "OpenAI API returned status "
                            + res.getStatusCode().value()
                    );
                })
                .body(ChatCompletionResponse.class);
        } catch (ResourceAccessException exception) {
            // 連線失敗或 timeout
            throw new ChatModelException(
                "Could not reach OpenAI API.", exception
            );
        }

        if (response == null
            || response.choices() == null
            || response.choices().isEmpty()
            || response.choices().get(0).message() == null) {

            throw new ChatModelException(
                "OpenAI API 回傳無效結果"
            );
        }

        return response.choices()
            .get(0)
            .message()
            .content();
    }
}
