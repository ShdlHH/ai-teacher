package com.example.aiteacher.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.aiteacher.common.BizException;
import com.example.aiteacher.common.ResultCode;
import com.example.aiteacher.common.UserContext;
import com.example.aiteacher.dto.AnswerRequest;
import com.example.aiteacher.dto.AnswerResult;
import com.example.aiteacher.dto.GenerateQuestionRequest;
import com.example.aiteacher.dto.GeneratedQuestion;
import com.example.aiteacher.dto.PageResult;
import com.example.aiteacher.dto.QuestionVO;
import com.example.aiteacher.entity.Question;
import com.example.aiteacher.entity.QuestionAttempt;
import com.example.aiteacher.mapper.QuestionAttemptMapper;
import com.example.aiteacher.mapper.QuestionMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 教学闭环：AI 出题 → 作答批改 → 错题沉淀
 */
@Slf4j
@Service
public class QuestionService {

    private final ChatClient quizChatClient;
    private final QuestionMapper questionMapper;
    private final QuestionAttemptMapper attemptMapper;
    private final WrongQuestionService wrongQuestionService;
    private final ObjectMapper objectMapper;

    public QuestionService(@Qualifier("quizChatClient") ChatClient quizChatClient,
                           QuestionMapper questionMapper,
                           QuestionAttemptMapper attemptMapper,
                           WrongQuestionService wrongQuestionService,
                           ObjectMapper objectMapper) {
        this.quizChatClient = quizChatClient;
        this.questionMapper = questionMapper;
        this.attemptMapper = attemptMapper;
        this.wrongQuestionService = wrongQuestionService;
        this.objectMapper = objectMapper;
    }

    /**
     * 调用低温度模型生成题目并落库
     */
    public List<QuestionVO> generate(GenerateQuestionRequest request) {
        return generateFor(UserContext.get(), request);
    }

    /**
     * 指定用户出题（供 HTTP 接口与 AI 工具共用，工具线程不依赖 ThreadLocal）
     */
    public List<QuestionVO> generateFor(Long userId, GenerateQuestionRequest request) {
        int count = request.getCount() == null ? 3 : Math.min(Math.max(request.getCount(), 1), 5);
        String userPrompt = buildGeneratePrompt(request, count);

        String raw;
        try {
            raw = quizChatClient.prompt().user(userPrompt).call().content();
        } catch (Exception e) {
            log.error("调用出题模型失败", e);
            throw new BizException(ResultCode.SERVER_ERROR.getCode(), "出题服务暂不可用，请稍后重试");
        }

        List<GeneratedQuestion> generated = parseQuestions(raw);
        if (generated.isEmpty()) {
            log.warn("出题结果解析为空，原始返回：{}", raw);
            throw new BizException(ResultCode.SERVER_ERROR.getCode(), "题目生成失败，请重试");
        }

        List<QuestionVO> result = new ArrayList<>();
        for (GeneratedQuestion item : generated) {
            if (result.size() >= count) {
                break;
            }
            Question question = toEntity(item, request.getSubject(), userId);
            questionMapper.insert(question);
            result.add(toVO(question));
        }
        return result;
    }

    public PageResult<QuestionVO> listMine(String subject, long page, long size) {
        long current = Math.max(page, 1);
        long limit = Math.min(Math.max(size, 1), 100);
        IPage<Question> result = questionMapper.selectPage(new Page<>(current, limit),
                new LambdaQueryWrapper<Question>()
                        .eq(Question::getUserId, UserContext.get())
                        .eq(StringUtils.hasText(subject), Question::getSubject, subject)
                        .orderByDesc(Question::getId));
        return PageResult.map(result, this::toVO);
    }

    public QuestionVO detail(Long questionId) {
        return toVO(getOwned(questionId));
    }

    /**
     * 批改作答：选择题/填空题规则判分，简答题交给 AI 评分；答错自动加入错题本
     * 作答记录与错题登记在同一事务内，避免只写一半
     */
    @Transactional
    public AnswerResult submit(AnswerRequest request) {
        Question question = getOwned(request.getQuestionId());
        String userAnswer = request.getUserAnswer().trim();

        boolean correct;
        int score;
        String comment;
        if (Question.TYPE_CHOICE.equals(question.getType())) {
            correct = gradeChoice(question, userAnswer);
            score = correct ? 100 : 0;
            comment = correct ? "回答正确，继续保持！" : "答案不对，再想想题目中的关键条件。";
        } else if (Question.TYPE_BLANK.equals(question.getType())) {
            correct = normalize(question.getAnswer()).equals(normalize(userAnswer));
            score = correct ? 100 : 0;
            comment = correct ? "填空正确！" : "填空内容与标准答案不一致，注意单位和表述。";
        } else {
            AnswerResult graded = gradeByAi(question, userAnswer);
            correct = graded.isCorrect();
            score = graded.getScore();
            comment = graded.getComment();
        }

        QuestionAttempt attempt = new QuestionAttempt();
        attempt.setUserId(UserContext.get());
        attempt.setQuestionId(question.getId());
        attempt.setUserAnswer(userAnswer);
        attempt.setCorrect(correct ? 1 : 0);
        attempt.setScore(score);
        attempt.setComment(comment);
        attempt.setCreatedAt(LocalDateTime.now());
        attemptMapper.insert(attempt);

        boolean addedToWrongBook = false;
        if (!correct) {
            wrongQuestionService.recordWrong(question, userAnswer);
            addedToWrongBook = true;
        }

        AnswerResult result = new AnswerResult();
        result.setCorrect(correct);
        result.setScore(score);
        result.setComment(comment);
        result.setStandardAnswer(question.getAnswer());
        result.setAnalysis(question.getAnalysis());
        result.setAddedToWrongBook(addedToWrongBook);
        return result;
    }

    public Question getOwned(Long questionId) {
        Question question = questionMapper.selectById(questionId);
        if (question == null) {
            throw new BizException(ResultCode.NOT_FOUND.getCode(), "题目不存在");
        }
        if (!question.getUserId().equals(UserContext.get())) {
            throw new BizException(ResultCode.FORBIDDEN);
        }
        return question;
    }

    private String buildGeneratePrompt(GenerateQuestionRequest request, int count) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("请生成 ").append(count).append(" 道「").append(request.getSubject()).append("」练习题。");
        if (StringUtils.hasText(request.getKnowledgePoint())) {
            prompt.append("知识点限定为：").append(request.getKnowledgePoint()).append("。");
        }
        if (StringUtils.hasText(request.getType())) {
            prompt.append("题型限定为：").append(request.getType()).append("。");
        } else {
            prompt.append("题型可以混合，但选择题不少于一半。");
        }
        prompt.append("只输出 JSON，不要任何多余文字。");
        return prompt.toString();
    }

    private List<GeneratedQuestion> parseQuestions(String raw) {
        List<GeneratedQuestion> result = new ArrayList<>();
        if (!StringUtils.hasText(raw)) {
            return result;
        }
        try {
            String json = extractJson(raw);
            JsonNode root = objectMapper.readTree(json);
            JsonNode array = root.isArray() ? root : root.path("questions");
            if (!array.isArray()) {
                return result;
            }
            for (JsonNode node : array) {
                GeneratedQuestion item = objectMapper.treeToValue(node, GeneratedQuestion.class);
                String normalizedType = normalizeType(item.getType());
                if (!StringUtils.hasText(item.getContent()) || !StringUtils.hasText(item.getAnswer())) {
                    continue;
                }
                item.setType(normalizedType);
                if (item.getOptions() == null) {
                    item.setOptions(List.of());
                }
                result.add(item);
            }
        } catch (Exception e) {
            log.warn("解析出题 JSON 失败：{}", e.getMessage());
        }
        return result;
    }

    private String extractJson(String raw) {
        String text = raw.trim();
        if (text.startsWith("```")) {
            int firstLineEnd = text.indexOf('\n');
            if (firstLineEnd > 0) {
                text = text.substring(firstLineEnd + 1);
            }
            int fenceEnd = text.lastIndexOf("```");
            if (fenceEnd >= 0) {
                text = text.substring(0, fenceEnd);
            }
        }
        int objectStart = text.indexOf('{');
        int arrayStart = text.indexOf('[');
        int start;
        if (objectStart < 0) {
            start = arrayStart;
        } else if (arrayStart < 0) {
            start = objectStart;
        } else {
            start = Math.min(objectStart, arrayStart);
        }
        int end = Math.max(text.lastIndexOf('}'), text.lastIndexOf(']'));
        if (start < 0 || end <= start) {
            return text;
        }
        return text.substring(start, end + 1);
    }

    private AnswerResult gradeByAi(Question question, String userAnswer) {
        String prompt = """
                你是一位严格的批改老师。请根据题目和参考答案判断学生作答是否正确。
                题目：%s
                参考答案：%s
                学生作答：%s
                只输出 JSON：{"correct":true 或 false,"score":0 到 100 的整数,"comment":"不超过 80 字的评语，指出对错原因"}
                """.formatted(question.getContent(), question.getAnswer(), userAnswer);
        try {
            String raw = quizChatClient.prompt().user(prompt).call().content();
            JsonNode node = objectMapper.readTree(extractJson(raw));
            AnswerResult result = new AnswerResult();
            result.setCorrect(node.path("correct").asBoolean(false));
            result.setScore(Math.min(Math.max(node.path("score").asInt(0), 0), 100));
            result.setComment(node.path("comment").asText(""));
            return result;
        } catch (Exception e) {
            log.warn("AI 批改失败，按未通过处理：{}", e.getMessage());
            AnswerResult fallback = new AnswerResult();
            fallback.setCorrect(false);
            fallback.setScore(0);
            fallback.setComment("批改服务暂时不可用，请对照参考答案自行检查。");
            return fallback;
        }
    }

    private boolean gradeChoice(Question question, String userAnswer) {
        String standard = question.getAnswer() == null ? "" : question.getAnswer().trim();
        String upperStandard = standard.toUpperCase(Locale.ROOT);
        String upperUser = userAnswer.toUpperCase(Locale.ROOT);

        String standardLetter = firstOptionLetter(upperStandard);
        String userLetter = firstOptionLetter(upperUser);
        if (standardLetter != null && userLetter != null) {
            return standardLetter.equals(userLetter);
        }
        return normalize(standard).equals(normalize(userAnswer));
    }

    private String firstOptionLetter(String text) {
        String trimmed = text.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        char first = trimmed.charAt(0);
        if (first >= 'A' && first <= 'D') {
            return String.valueOf(first);
        }
        return null;
    }

    private String normalize(String text) {
        if (text == null) {
            return "";
        }
        return text.toLowerCase(Locale.ROOT).replaceAll("[\\s，。；、,.;:'\"（）()]", "");
    }

    private String normalizeType(String type) {
        if (!StringUtils.hasText(type)) {
            return Question.TYPE_SHORT_ANSWER;
        }
        String value = type.toLowerCase(Locale.ROOT);
        if (value.contains("choice") || value.contains("选择")) {
            return Question.TYPE_CHOICE;
        }
        if (value.contains("blank") || value.contains("填空")) {
            return Question.TYPE_BLANK;
        }
        return Question.TYPE_SHORT_ANSWER;
    }

    private Question toEntity(GeneratedQuestion item, String subject, Long userId) {
        Question question = new Question();
        question.setUserId(userId);
        question.setSubject(subject);
        question.setKnowledgePoint(item.getKnowledgePoint());
        question.setType(item.getType());
        question.setContent(item.getContent());
        question.setOptions(toJson(item.getOptions()));
        question.setAnswer(item.getAnswer());
        question.setAnalysis(item.getAnalysis());
        question.setCreatedAt(LocalDateTime.now());
        return question;
    }

    private String toJson(List<String> options) {
        try {
            return objectMapper.writeValueAsString(options == null ? List.of() : options);
        } catch (Exception e) {
            return "[]";
        }
    }

    private QuestionVO toVO(Question question) {
        QuestionVO vo = new QuestionVO();
        vo.setId(question.getId());
        vo.setSubject(question.getSubject());
        vo.setKnowledgePoint(question.getKnowledgePoint());
        vo.setType(question.getType());
        vo.setContent(question.getContent());
        vo.setOptions(parseOptions(question.getOptions()));
        vo.setAnswer(question.getAnswer());
        vo.setAnalysis(question.getAnalysis());
        vo.setCreatedAt(question.getCreatedAt());
        return vo;
    }

    public List<String> parseOptions(String options) {
        if (!StringUtils.hasText(options)) {
            return List.of();
        }
        try {
            return objectMapper.readValue(options, new TypeReference<List<String>>() {
            });
        } catch (Exception e) {
            return List.of();
        }
    }
}
