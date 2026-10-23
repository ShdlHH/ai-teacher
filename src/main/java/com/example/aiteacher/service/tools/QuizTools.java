package com.example.aiteacher.service.tools;

import com.example.aiteacher.dto.GenerateQuestionRequest;
import com.example.aiteacher.dto.QuestionVO;
import com.example.aiteacher.service.QuestionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 出题工具：复用低温度 quizChatClient，生成结果直接落库
 */
@Slf4j
public class QuizTools {

    private final Long userId;
    private final QuestionService questionService;

    public QuizTools(Long userId, QuestionService questionService) {
        this.userId = userId;
        this.questionService = questionService;
    }

    @Tool(name = "generateQuiz",
            description = "为当前学生生成练习题并保存到题库。学生明确要求“出题/练习/考考我”时调用；"
                    + "生成通常需要十几秒，调用前先告知学生正在出题。")
    public Map<String, Object> generateQuiz(
            @ToolParam(description = "学科，必填，例如 数学/物理/英语") String subject,
            @ToolParam(description = "知识点，可为空", required = false) String knowledgePoint,
            @ToolParam(description = "题目数量 1-5，默认 3", required = false) Integer count,
            @ToolParam(description = "题型：choice/blank/short_answer，可为空表示混合出题", required = false)
            String type) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (!StringUtils.hasText(subject)) {
            result.put("success", false);
            result.put("message", "缺少学科，无法出题");
            return result;
        }
        GenerateQuestionRequest request = new GenerateQuestionRequest();
        request.setSubject(subject.trim());
        request.setKnowledgePoint(knowledgePoint);
        request.setCount(count);
        request.setType(type);
        log.info("[Tool] generateQuiz userId={} subject={} knowledgePoint={} count={} type={}",
                userId, subject, knowledgePoint, count, type);
        try {
            List<QuestionVO> questions = questionService.generateFor(userId, request);
            List<Map<String, Object>> items = new ArrayList<>();
            for (QuestionVO question : questions) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("id", question.getId());
                item.put("subject", question.getSubject());
                item.put("type", question.getType());
                item.put("content", question.getContent());
                item.put("options", question.getOptions());
                item.put("answer", question.getAnswer());
                item.put("analysis", abbreviate(question.getAnalysis(), 200));
                items.add(item);
            }
            result.put("success", true);
            result.put("questions", items);
            return result;
        } catch (Exception e) {
            log.warn("[Tool] generateQuiz 失败：{}", e.getMessage());
            result.put("success", false);
            result.put("message", "出题服务暂时不可用，请稍后重试");
            return result;
        }
    }

    private String abbreviate(String text, int max) {
        if (!StringUtils.hasText(text) || text.length() <= max) {
            return text;
        }
        return text.substring(0, max) + "...";
    }
}
