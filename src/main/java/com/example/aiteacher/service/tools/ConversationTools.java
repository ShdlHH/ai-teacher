package com.example.aiteacher.service.tools;

import com.example.aiteacher.entity.Conversation;
import com.example.aiteacher.service.ConversationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 会话工具：查询当前学生最近的对话记录
 */
@Slf4j
public class ConversationTools {

    private final Long userId;
    private final ConversationService conversationService;

    public ConversationTools(Long userId, ConversationService conversationService) {
        this.userId = userId;
        this.conversationService = conversationService;
    }

    @Tool(name = "listMyConversations",
            description = "查询当前学生最近的会话列表（标题、学科、更新时间）。学生问“我以前聊过什么”“我的会话”时调用。")
    public List<Map<String, Object>> listMyConversations() {
        log.info("[Tool] listMyConversations userId={}", userId);
        List<Conversation> conversations = conversationService.listMineFor(userId);
        List<Map<String, Object>> result = new ArrayList<>();
        for (Conversation conversation : conversations) {
            if (result.size() >= 5) {
                break;
            }
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", conversation.getId());
            item.put("title", conversation.getTitle());
            item.put("subject", conversation.getSubject());
            item.put("updatedAt", String.valueOf(conversation.getUpdatedAt()));
            result.add(item);
        }
        return result;
    }
}
