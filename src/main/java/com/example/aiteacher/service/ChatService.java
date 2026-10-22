package com.example.aiteacher.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.aiteacher.common.UserContext;
import com.example.aiteacher.dto.ChatStreamEvent;
import com.example.aiteacher.dto.RagContext;
import com.example.aiteacher.entity.ChatMessage;
import com.example.aiteacher.entity.Conversation;
import com.example.aiteacher.mapper.ChatMessageMapper;
import com.example.aiteacher.mapper.ConversationMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.LocalDateTime;
import java.util.List;
import java.util.function.Consumer;

/**
 * 对话服务：
 * 1. 会话记忆交给 MessageChatMemoryAdvisor + DbChatMemory 自动读写
 * 2. RAG 检索结果以额外 UserMessage 注入提示词（不写入历史）
 * 3. SSE 事件化输出：citations → token* → done
 */
@Slf4j
@Service
public class ChatService {

    private final ChatClient chatClient;
    private final ConversationService conversationService;
    private final ChatMessageMapper chatMessageMapper;
    private final ConversationMapper conversationMapper;
    private final RagService ragService;
    private final ObjectMapper objectMapper;

    public ChatService(ChatClient chatClient,
                       ConversationService conversationService,
                       ChatMessageMapper chatMessageMapper,
                       ConversationMapper conversationMapper,
                       RagService ragService,
                       ObjectMapper objectMapper) {
        this.chatClient = chatClient;
        this.conversationService = conversationService;
        this.chatMessageMapper = chatMessageMapper;
        this.conversationMapper = conversationMapper;
        this.ragService = ragService;
        this.objectMapper = objectMapper;
    }

    public Flux<ChatStreamEvent> stream(Long conversationId, String message) {
        Conversation conversation = conversationService.getOwned(conversationId);
        updateTitleIfNeeded(conversation, message);

        Long userId = UserContext.get();
        Mono<RagContext> ragContext = Mono.fromCallable(() -> ragService.retrieve(userId, message))
                .subscribeOn(Schedulers.boundedElastic())
                .onErrorReturn(RagContext.empty());

        return ragContext.flatMapMany(context -> {
            String citationsJson = toJson(context.citations());
            Flux<ChatStreamEvent> head = Flux.just(ChatStreamEvent.citations(citationsJson));

            ChatClient.ChatClientRequestSpec spec = chatClient.prompt();
            if (!context.isEmpty()) {
                spec = spec.messages(new UserMessage(context.contextText()));
            }
            Flux<ChatStreamEvent> tokens = spec
                    .advisors(withConversationId(conversationId))
                    .user(message)
                    .stream()
                    .content()
                    .map(ChatStreamEvent::token);

            return Flux.concat(head, tokens)
                    .concatWith(Flux.defer(() -> {
                        saveCitations(conversationId, citationsJson);
                        return Flux.just(ChatStreamEvent.done());
                    }));
        });
    }

    private Consumer<ChatClient.AdvisorSpec> withConversationId(Long conversationId) {
        return advisorSpec -> advisorSpec.param(ChatMemory.CONVERSATION_ID, conversationId);
    }

    private void updateTitleIfNeeded(Conversation conversation, String message) {
        if (!"新对话".equals(conversation.getTitle())) {
            return;
        }
        Conversation update = new Conversation();
        update.setId(conversation.getId());
        update.setTitle(message.length() > 20 ? message.substring(0, 20) : message);
        conversationMapper.updateById(update);
    }

    /**
     * 流结束后，把本次引用信息补写到刚生成的 assistant 消息上（历史记录可溯源）
     */
    private void saveCitations(Long conversationId, String citationsJson) {
        try {
            ChatMessage last = chatMessageMapper.selectOne(
                    new LambdaQueryWrapper<ChatMessage>()
                            .eq(ChatMessage::getConversationId, conversationId)
                            .eq(ChatMessage::getRole, ChatMessage.ROLE_ASSISTANT)
                            .orderByDesc(ChatMessage::getId)
                            .last("LIMIT 1"));
            if (last == null) {
                return;
            }
            ChatMessage update = new ChatMessage();
            update.setId(last.getId());
            update.setCitations(citationsJson);
            chatMessageMapper.updateById(update);
        } catch (Exception e) {
            log.warn("写入引用信息失败：{}", e.getMessage());
        }
    }

    private String toJson(List<?> citations) {
        try {
            return objectMapper.writeValueAsString(citations);
        } catch (JsonProcessingException e) {
            return "[]";
        }
    }
}
