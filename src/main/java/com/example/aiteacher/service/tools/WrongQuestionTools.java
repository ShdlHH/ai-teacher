package com.example.aiteacher.service.tools;

import com.example.aiteacher.dto.PageResult;
import com.example.aiteacher.dto.WrongQuestionVO;
import com.example.aiteacher.service.WrongQuestionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 错题本工具：查询当前学生的错题，按最近更新时间倒序
 */
@Slf4j
public class WrongQuestionTools {

    private final Long userId;
    private final WrongQuestionService wrongQuestionService;

    public WrongQuestionTools(Long userId, WrongQuestionService wrongQuestionService) {
        this.userId = userId;
        this.wrongQuestionService = wrongQuestionService;
    }

    @Tool(name = "listWrongQuestions",
            description = "查询当前学生的错题列表（最近优先），可按学科筛选。学生问“我有哪些错题”“物理错题”时调用。")
    public List<Map<String, Object>> listWrongQuestions(
            @ToolParam(description = "学科，例如 数学/物理/英语；不筛选时传空字符串", required = false) String subject,
            @ToolParam(description = "返回条数，1-10，默认 5", required = false) Integer limit) {
        int size = limit == null ? 5 : Math.min(Math.max(limit, 1), 10);
        String subjectFilter = StringUtils.hasText(subject) ? subject.trim() : null;
        log.info("[Tool] listWrongQuestions userId={} subject={} limit={}", userId, subjectFilter, size);

        PageResult<WrongQuestionVO> page = wrongQuestionService.listMineFor(userId, subjectFilter, null, 1, size);
        List<Map<String, Object>> result = new ArrayList<>();
        for (WrongQuestionVO vo : page.getRecords()) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", vo.getId());
            item.put("subject", vo.getSubject());
            item.put("knowledgePoint", vo.getKnowledgePoint());
            item.put("type", vo.getType());
            item.put("content", abbreviate(vo.getContent(), 120));
            item.put("userAnswer", vo.getUserAnswer());
            item.put("standardAnswer", vo.getAnswer());
            item.put("wrongCount", vo.getWrongCount());
            item.put("mastered", Boolean.TRUE.equals(vo.getMastered()));
            result.add(item);
        }
        return result;
    }

    private String abbreviate(String text, int max) {
        if (!StringUtils.hasText(text) || text.length() <= max) {
            return text;
        }
        return text.substring(0, max) + "...";
    }
}
