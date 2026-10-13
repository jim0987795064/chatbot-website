package com.example.gptchat.service;

import com.example.gptchat.dto.ChatResponse;
import com.example.gptchat.entity.ChatMessage;
import com.example.gptchat.repository.ChatMessageRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Service
public class ChatService {

    private final ChatMessageRepository repository;
    private final ChatModelClient chatModelClient;
    private final int historyLimit;
    private final int historyMaxChars;

    public ChatService(
        ChatMessageRepository repository,
        ChatModelClient chatModelClient,
        @Value("${chat.history-limit:20}") int historyLimit,
        @Value("${chat.history-max-chars:12000}") int historyMaxChars
    ) {
        this.repository = repository;
        this.chatModelClient = chatModelClient;
        this.historyLimit = historyLimit;
        this.historyMaxChars = historyMaxChars;
    }

    public ChatResponse chat(String message) {

        if (message == null || message.isBlank()) {
            throw new IllegalArgumentException(
                "Message cannot be empty."
            );
        }

        /*
         * 1. 讀既有歷史，組成送給模型的上下文
         *
         * 注意：新訊息此時「還沒」落庫。模型呼叫失敗時不能留下
         * 一則沒有回覆的使用者訊息 —— 那會污染下一次的上下文。
         */
        ChatMessage userMessage =
            new ChatMessage("user", message);

        List<ChatMessage> context = loadContext();
        context.add(userMessage);

        /*
         * 2. 呼叫模型（在交易之外，不讓 HTTP 等待占住 DB 連線）
         */
        String answer = chatModelClient.chat(context);

        /*
         * 3. 成功後才把這一輪的兩則訊息一起寫入
         *
         * saveAll 本身就是單一交易，兩則訊息一起成立或一起失敗，
         * 不會出現只有問句沒有回覆的半套資料。
         */
        repository.saveAll(List.of(
            userMessage,
            new ChatMessage("assistant", answer)
        ));

        return new ChatResponse(answer);
    }

    /**
     * 取最近 historyLimit 則訊息，轉成 oldest → newest，
     * 並從最舊的開始丟棄直到總字元數在 historyMaxChars 以內。
     */
    private List<ChatMessage> loadContext() {

        /*
         * Repository 給的是 newest → oldest，模型要 oldest → newest
         */
        List<ChatMessage> history = new ArrayList<>(
            repository.findByOrderByIdDesc(Limit.of(historyLimit))
        );

        Collections.reverse(history);

        int chars = history.stream()
            .mapToInt(m -> m.getContent().length())
            .sum();

        /*
         * 超出預算就砍最舊的，避免長對話把 token 成本推上去
         * 或直接撞到模型的 context limit
         */
        while (chars > historyMaxChars && !history.isEmpty()) {
            chars -= history.remove(0).getContent().length();
        }

        return history;
    }

    public List<ChatMessage> getHistory() {

        /*
         * oldest → newest，和前端顯示順序一致
         */
        return repository.findAllByOrderByIdAsc();
    }

    @Transactional
    public void clearHistory() {
        repository.deleteAll();
    }
}
