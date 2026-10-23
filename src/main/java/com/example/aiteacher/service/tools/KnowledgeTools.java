package com.example.aiteacher.service.tools;

import com.example.aiteacher.dto.Citation;
import com.example.aiteacher.dto.RagContext;
import com.example.aiteacher.service.RagService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 知识库工具：在当前用户上传的资料中检索，避免模型凭空回答
 */
@Slf4j
public class KnowledgeTools {

    private final Long userId;
    private final RagService ragService;

    public KnowledgeTools(Long userId, RagService ragService) {
        this.userId = userId;
        this.ragService = ragService;
    }

    @Tool(name = "searchKnowledge",
            description = "在当前学生已上传的知识库资料中检索相关片段。学生询问教材、讲义或已上传资料里的内容，"
                    + "或你需要引用知识库作答时调用。返回片段原文与来源文件。")
    public Map<String, Object> searchKnowledge(
            @ToolParam(description = "检索关键词或学生的问题") String query) {
        log.info("[Tool] searchKnowledge userId={} query={}", userId, query);
        RagContext context = ragService.retrieve(userId, query);
        Map<String, Object> result = new LinkedHashMap<>();
        if (context.isEmpty()) {
            result.put("found", false);
            result.put("message", "当前用户的知识库中没有检索到相关资料");
            return result;
        }
        List<Map<String, Object>> items = new ArrayList<>();
        for (Citation citation : context.citations()) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("documentId", citation.documentId());
            item.put("fileName", citation.fileName());
            item.put("chunkIndex", citation.chunkIndex());
            item.put("content", abbreviate(citation.content(), 300));
            item.put("score", citation.score());
            items.add(item);
        }
        result.put("found", true);
        result.put("results", items);
        return result;
    }

    private String abbreviate(String text, int max) {
        if (!StringUtils.hasText(text) || text.length() <= max) {
            return text;
        }
        return text.substring(0, max) + "...";
    }
}
