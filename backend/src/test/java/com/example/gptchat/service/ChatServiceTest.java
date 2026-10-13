package com.example.gptchat.service;

import com.example.gptchat.dto.ChatResponse;
import com.example.gptchat.entity.ChatMessage;
import com.example.gptchat.repository.ChatMessageRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.data.domain.Limit;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

class ChatServiceTest {

    private static final int HISTORY_LIMIT = 20;
    private static final int MAX_CHARS = 12000;

    private final ChatMessageRepository repository =
        mock(ChatMessageRepository.class);
    private final ChatModelClient modelClient =
        mock(ChatModelClient.class);

    private ChatService serviceWith(int maxChars) {
        return new ChatService(
            repository, modelClient, HISTORY_LIMIT, maxChars);
    }

    private final ChatService service = serviceWith(MAX_CHARS);

    @Test
    void passesHistoryToModelInOldestFirstOrder() {

        // repository 回傳既有歷史，newest → oldest
        given(repository.findByOrderByIdDesc(any(Limit.class)))
            .willReturn(List.of(
                new ChatMessage("assistant", "第二"),
                new ChatMessage("user", "第一")
            ));
        given(modelClient.chat(anyList())).willReturn("回覆");

        service.chat("第三");

        ArgumentCaptor<List<ChatMessage>> captor = ArgumentCaptor.captor();
        verify(modelClient).chat(captor.capture());

        // model 應該收到 oldest → newest，且包含這次的新訊息
        assertThat(captor.getValue())
            .extracting(ChatMessage::getContent)
            .containsExactly("第一", "第二", "第三");
    }

    @Test
    void persistsBothMessagesOnlyAfterModelSucceeds() {

        given(repository.findByOrderByIdDesc(any(Limit.class)))
            .willReturn(List.of());
        given(modelClient.chat(anyList())).willReturn("模型回覆");

        ChatResponse response = service.chat("嗨");

        assertThat(response.answer()).isEqualTo("模型回覆");

        // 先問模型，成功才落庫
        InOrder inOrder = inOrder(modelClient, repository);
        inOrder.verify(modelClient).chat(anyList());
        inOrder.verify(repository).saveAll(anyList());

        ArgumentCaptor<List<ChatMessage>> captor = ArgumentCaptor.captor();
        verify(repository).saveAll(captor.capture());

        assertThat(captor.getValue())
            .extracting(ChatMessage::getRole, ChatMessage::getContent)
            .containsExactly(
                tuple("user", "嗨"),
                tuple("assistant", "模型回覆")
            );
    }

    @Test
    void persistsNothingWhenModelCallFails() {

        given(repository.findByOrderByIdDesc(any(Limit.class)))
            .willReturn(List.of());
        given(modelClient.chat(anyList()))
            .willThrow(new ChatModelException("upstream down"));

        assertThatThrownBy(() -> service.chat("嗨"))
            .isInstanceOf(ChatModelException.class);

        // 不能留下一則沒有回覆的使用者訊息去污染下一輪上下文
        verify(repository, never()).save(any());
        verify(repository, never()).saveAll(anyList());
    }

    @Test
    void rejectsBlankMessageWithoutTouchingRepositoryOrModel() {

        assertThatThrownBy(() -> service.chat("   "))
            .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> service.chat(null))
            .isInstanceOf(IllegalArgumentException.class);

        verify(repository, never()).save(any());
        verify(repository, never()).saveAll(anyList());
        verifyNoInteractions(modelClient);
    }

    @Test
    void dropsOldestMessagesBeyondCharBudget() {

        given(repository.findByOrderByIdDesc(any(Limit.class)))
            .willReturn(List.of(
                new ChatMessage("user", "ccc"),
                new ChatMessage("assistant", "bbb"),
                new ChatMessage("user", "aaa")
            ));
        given(modelClient.chat(anyList())).willReturn("ok");

        // 預算 7 字元：9 字元的歷史要砍掉最舊的 "aaa"
        serviceWith(7).chat("new");

        ArgumentCaptor<List<ChatMessage>> captor = ArgumentCaptor.captor();
        verify(modelClient).chat(captor.capture());

        assertThat(captor.getValue())
            .extracting(ChatMessage::getContent)
            .containsExactly("bbb", "ccc", "new");
    }

    @Test
    void getHistoryUsesOrderedQuery() {

        given(repository.findAllByOrderByIdAsc()).willReturn(List.of(
            new ChatMessage("user", "a"),
            new ChatMessage("assistant", "b")
        ));

        assertThat(service.getHistory())
            .extracting(ChatMessage::getContent)
            .containsExactly("a", "b");

        verify(repository, never()).findAll();
    }
}
