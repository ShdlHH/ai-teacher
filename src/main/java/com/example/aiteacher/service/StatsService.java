package com.example.aiteacher.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.aiteacher.common.UserContext;
import com.example.aiteacher.entity.Question;
import com.example.aiteacher.entity.QuestionAttempt;
import com.example.aiteacher.entity.WrongQuestion;
import com.example.aiteacher.mapper.QuestionAttemptMapper;
import com.example.aiteacher.mapper.QuestionMapper;
import com.example.aiteacher.mapper.WrongQuestionMapper;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 学习统计：题量、作答、正确率、错题掌握情况
 */
@Service
public class StatsService {

    private final QuestionMapper questionMapper;
    private final QuestionAttemptMapper attemptMapper;
    private final WrongQuestionMapper wrongQuestionMapper;

    public StatsService(QuestionMapper questionMapper,
                        QuestionAttemptMapper attemptMapper,
                        WrongQuestionMapper wrongQuestionMapper) {
        this.questionMapper = questionMapper;
        this.attemptMapper = attemptMapper;
        this.wrongQuestionMapper = wrongQuestionMapper;
    }

    public Map<String, Object> overview() {
        return overviewFor(UserContext.get());
    }

    /**
     * 指定用户的学习统计（供 HTTP 接口与 AI 工具共用，工具线程不依赖 ThreadLocal）
     */
    public Map<String, Object> overviewFor(Long userId) {
        long totalQuestions = count(questionMapper.selectCount(
                new LambdaQueryWrapper<Question>().eq(Question::getUserId, userId)));
        long totalAttempts = count(attemptMapper.selectCount(
                new LambdaQueryWrapper<QuestionAttempt>().eq(QuestionAttempt::getUserId, userId)));
        long correctAttempts = count(attemptMapper.selectCount(
                new LambdaQueryWrapper<QuestionAttempt>()
                        .eq(QuestionAttempt::getUserId, userId)
                        .eq(QuestionAttempt::getCorrect, 1)));
        long wrongTotal = count(wrongQuestionMapper.selectCount(
                new LambdaQueryWrapper<WrongQuestion>().eq(WrongQuestion::getUserId, userId)));
        long wrongUnmastered = count(wrongQuestionMapper.selectCount(
                new LambdaQueryWrapper<WrongQuestion>()
                        .eq(WrongQuestion::getUserId, userId)
                        .eq(WrongQuestion::getMastered, 0)));
        long mastered = wrongTotal - wrongUnmastered;
        double accuracy = totalAttempts == 0 ? 0.0
                : Math.round(correctAttempts * 10000.0 / totalAttempts) / 100.0;

        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("totalQuestions", totalQuestions);
        stats.put("totalAttempts", totalAttempts);
        stats.put("correctAttempts", correctAttempts);
        stats.put("accuracy", accuracy);
        stats.put("wrongTotal", wrongTotal);
        stats.put("wrongUnmastered", wrongUnmastered);
        stats.put("mastered", mastered);
        return stats;
    }

    private long count(Long value) {
        return value == null ? 0 : value;
    }
}
