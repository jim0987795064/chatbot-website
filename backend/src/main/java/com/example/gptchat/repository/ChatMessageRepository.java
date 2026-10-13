package com.example.gptchat.repository;

import com.example.gptchat.entity.ChatMessage;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ChatMessageRepository
    extends JpaRepository<ChatMessage, Long> {

    /**
     * 最近的訊息，newest → oldest。筆數由呼叫端決定，
     * 不把視窗大小寫死在方法名稱裡。
     */
    List<ChatMessage> findByOrderByIdDesc(Limit limit);

    List<ChatMessage> findAllByOrderByIdAsc();
}
