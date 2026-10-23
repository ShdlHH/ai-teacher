package com.example.aiteacher.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.aiteacher.common.BizException;
import com.example.aiteacher.common.ResultCode;
import com.example.aiteacher.common.UserContext;
import com.example.aiteacher.entity.ChatMessage;
import com.example.aiteacher.entity.Conversation;
import com.example.aiteacher.mapper.ChatMessageMapper;
import com.example.aiteacher.mapper.ConversationMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class ConversationService {

    private final ConversationMapper conversationMapper;
    private final ChatMessageMapper chatMessageMapper;

    public ConversationService(ConversationMapper conversationMapper, ChatMessageMapper chatMessageMapper) {
        this.conversationMapper = conversationMapper;
        this.chatMessageMapper = chatMessageMapper;
    }

    public List<Conversation> listMine() {
        return listMineFor(UserContext.get());
    }

    /**
     * 指定用户的会话列表（供 AI 工具调用，避免依赖 ThreadLocal）
     */
    public List<Conversation> listMineFor(Long userId) {
        return conversationMapper.selectList(
                new LambdaQueryWrapper<Conversation>()
                        .eq(Conversation::getUserId, userId)
                        .orderByDesc(Conversation::getUpdatedAt));
    }

    public Conversation create(String subject) {
        Conversation conversation = new Conversation();
        conversation.setUserId(UserContext.get());
        conversation.setTitle("新对话");
        conversation.setSubject(subject == null || subject.isBlank() ? "通用" : subject);
        conversation.setCreatedAt(LocalDateTime.now());
        conversation.setUpdatedAt(LocalDateTime.now());
        conversationMapper.insert(conversation);
        return conversation;
    }

    @Transactional
    public void delete(Long conversationId) {
        Conversation conversation = getOwned(conversationId);
        conversationMapper.deleteById(conversation.getId());
        chatMessageMapper.delete(
                new LambdaQueryWrapper<ChatMessage>().eq(ChatMessage::getConversationId, conversationId));
    }

    public List<ChatMessage> listMessages(Long conversationId) {
        getOwned(conversationId);
        return chatMessageMapper.selectList(
                new LambdaQueryWrapper<ChatMessage>()
                        .eq(ChatMessage::getConversationId, conversationId)
                        .orderByAsc(ChatMessage::getId));
    }

    /**
     * 校验会话存在且属于当前登录用户
     */
    public Conversation getOwned(Long conversationId) {
        Conversation conversation = conversationMapper.selectById(conversationId);
        if (conversation == null) {
            throw new BizException(ResultCode.NOT_FOUND.getCode(), "会话不存在");
        }
        if (!conversation.getUserId().equals(UserContext.get())) {
            throw new BizException(ResultCode.FORBIDDEN);
        }
        return conversation;
    }
}
