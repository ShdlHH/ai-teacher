package com.example.aiteacher.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.aiteacher.common.UserContext;
import com.example.aiteacher.dto.ChatStreamEvent;
import com.example.aiteacher.dto.RagContext;
import com.example.aiteacher.entity.ChatMessage;
import com.example.aiteacher.entity.Conversation;
import com.example.aiteacher.mapper.ChatMessageMapper;
import com.example.aiteacher.mapper.ConversationMapper;
import com.example.aiteacher.service.tools.StudyToolFactory;
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

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
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
    private final StudyToolFactory studyToolFactory;
    private final ObjectMapper objectMapper;

    public ChatService(ChatClient chatClient,
                       ConversationService conversationService,
                       ChatMessageMapper chatMessageMapper,
                       ConversationMapper conversationMapper,
                       RagService ragService,
                       StudyToolFactory studyToolFactory,
                       ObjectMapper objectMapper) {
        this.chatClient = chatClient;
        this.conversationService = conversationService;
        this.chatMessageMapper = chatMessageMapper;
        this.conversationMapper = conversationMapper;
        this.ragService = ragService;
        this.studyToolFactory = studyToolFactory;
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
            ChatClient.ChatClientRequestSpec requestSpec = spec
                    .advisors(withConversationId(conversationId))
                    .user(message)
                    .tools(studyToolFactory.build(userId));

            // Spring AI 1.0.0 中 Ollama 流式响应 + 工具调用会在聚合阶段抛 NPE（evalDuration 为 null），
            // 因此工具链路改为非流式生成，再把完整回答分片回放，保持前端 SSE 协议不变。
            Flux<ChatStreamEvent> tokens = Mono.fromCallable(() -> {
                        String answer = requestSpec.call().content();
                        return answer == null ? "" : answer;
                    })
                    .subscribeOn(Schedulers.boundedElastic())
                    .flatMapMany(answer -> Flux.fromIterable(splitAnswer(answer))
                            .delayElements(Duration.ofMillis(12))
                            .map(ChatStreamEvent::token));

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

    /**
     * 把完整回答切成小片回放，模拟流式输出
     */
    private List<String> splitAnswer(String answer) {
        List<String> chunks = new ArrayList<>();
        int size = 12;
        for (int i = 0; i < answer.length(); i += size) {
            chunks.add(answer.substring(i, Math.min(answer.length(), i + size)));
        }
        return chunks;
    }
}
