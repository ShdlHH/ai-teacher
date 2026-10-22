package com.example.aiteacher.service.memory;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.aiteacher.entity.ChatMessage;
import com.example.aiteacher.mapper.ChatMessageMapper;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 基于 chat_message 表的 ChatMemory 实现：
 * 由 MessageChatMemoryAdvisor 自动读写，实现多轮上下文持久化（重启不丢）。
 */
@Component
public class DbChatMemory implements ChatMemory {

    private final ChatMessageMapper chatMessageMapper;
    private final int maxMessages;

    public DbChatMemory(ChatMessageMapper chatMessageMapper,
                        @Value("${ai.teacher.history-limit}") int maxMessages) {
        this.chatMessageMapper = chatMessageMapper;
        this.maxMessages = maxMessages;
    }

    @Override
    public void add(String conversationId, List<Message> messages) {
        Long cid = parseConversationId(conversationId);
        if (cid == null || messages == null || messages.isEmpty()) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        for (Message message : messages) {
            String role = roleOf(message);
            if (role == null || message.getText() == null || message.getText().isBlank()) {
                continue;
            }
            ChatMessage row = new ChatMessage();
            row.setConversationId(cid);
            row.setRole(role);
            row.setContent(message.getText());
            row.setCreatedAt(now);
            chatMessageMapper.insert(row);
        }
    }

    @Override
    public List<Message> get(String conversationId) {
        Long cid = parseConversationId(conversationId);
        if (cid == null) {
            return List.of();
        }
        List<ChatMessage> rows = chatMessageMapper.selectList(
                new LambdaQueryWrapper<ChatMessage>()
                        .eq(ChatMessage::getConversationId, cid)
                        .orderByDesc(ChatMessage::getId)
                        .last("LIMIT " + maxMessages));
        Collections.reverse(rows);
        List<Message> messages = new ArrayList<>(rows.size());
        for (ChatMessage row : rows) {
            if (ChatMessage.ROLE_USER.equals(row.getRole())) {
                messages.add(new UserMessage(row.getContent()));
            } else if (ChatMessage.ROLE_ASSISTANT.equals(row.getRole())) {
                messages.add(new AssistantMessage(row.getContent()));
            } else if (ChatMessage.ROLE_SYSTEM.equals(row.getRole())) {
                messages.add(new SystemMessage(row.getContent()));
            }
        }
        return messages;
    }

    @Override
    public void clear(String conversationId) {
        Long cid = parseConversationId(conversationId);
        if (cid == null) {
            return;
        }
        chatMessageMapper.delete(new LambdaQueryWrapper<ChatMessage>()
                .eq(ChatMessage::getConversationId, cid));
    }

    private Long parseConversationId(String conversationId) {
        try {
            return Long.valueOf(conversationId);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String roleOf(Message message) {
        if (message instanceof UserMessage) {
            return ChatMessage.ROLE_USER;
        }
        if (message instanceof AssistantMessage) {
            return ChatMessage.ROLE_ASSISTANT;
        }
        if (message instanceof SystemMessage) {
            return ChatMessage.ROLE_SYSTEM;
        }
        return null;
    }
}
