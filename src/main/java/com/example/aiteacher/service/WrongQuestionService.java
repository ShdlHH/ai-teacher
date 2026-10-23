package com.example.aiteacher.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.aiteacher.common.BizException;
import com.example.aiteacher.common.ResultCode;
import com.example.aiteacher.common.UserContext;
import com.example.aiteacher.dto.PageResult;
import com.example.aiteacher.dto.WrongQuestionVO;
import com.example.aiteacher.entity.Question;
import com.example.aiteacher.entity.WrongQuestion;
import com.example.aiteacher.mapper.QuestionMapper;
import com.example.aiteacher.mapper.WrongQuestionMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 错题本：答错自动 upsert，支持按学科筛选、标记掌握、删除
 */
@Slf4j
@Service
public class WrongQuestionService {

    private final WrongQuestionMapper wrongQuestionMapper;
    private final QuestionMapper questionMapper;
    private final ObjectMapper objectMapper;

    public WrongQuestionService(WrongQuestionMapper wrongQuestionMapper,
                                QuestionMapper questionMapper,
                                ObjectMapper objectMapper) {
        this.wrongQuestionMapper = wrongQuestionMapper;
        this.questionMapper = questionMapper;
        this.objectMapper = objectMapper;
    }

    public void recordWrong(Question question, String userAnswer) {
        WrongQuestion existing = wrongQuestionMapper.selectOne(
                new LambdaQueryWrapper<WrongQuestion>()
                        .eq(WrongQuestion::getUserId, UserContext.get())
                        .eq(WrongQuestion::getQuestionId, question.getId())
                        .last("LIMIT 1"));
        LocalDateTime now = LocalDateTime.now();
        if (existing == null) {
            WrongQuestion row = new WrongQuestion();
            row.setUserId(UserContext.get());
            row.setQuestionId(question.getId());
            row.setUserAnswer(userAnswer);
            row.setWrongCount(1);
            row.setMastered(0);
            row.setCreatedAt(now);
            row.setUpdatedAt(now);
            wrongQuestionMapper.insert(row);
            return;
        }
        existing.setUserAnswer(userAnswer);
        existing.setWrongCount(existing.getWrongCount() == null ? 1 : existing.getWrongCount() + 1);
        existing.setMastered(0);
        existing.setUpdatedAt(now);
        wrongQuestionMapper.updateById(existing);
    }

    public PageResult<WrongQuestionVO> listMine(String subject, Boolean mastered, long page, long size) {
        return listMineFor(UserContext.get(), subject, mastered, page, size);
    }

    /**
     * 指定用户的错题分页（供 HTTP 接口与 AI 工具共用）
     */
    public PageResult<WrongQuestionVO> listMineFor(Long userId, String subject, Boolean mastered,
                                                   long page, long size) {
        long current = Math.max(page, 1);
        long limit = Math.min(Math.max(size, 1), 100);

        LambdaQueryWrapper<WrongQuestion> wrapper = new LambdaQueryWrapper<WrongQuestion>()
                .eq(WrongQuestion::getUserId, userId)
                .eq(mastered != null, WrongQuestion::getMastered, mastered == null ? null : (mastered ? 1 : 0))
                .orderByDesc(WrongQuestion::getUpdatedAt);

        // 学科条件在 question 表上，先取出该用户的同学科题目 ID，再分页查询错题
        if (StringUtils.hasText(subject)) {
            List<Long> questionIds = questionMapper.selectList(new LambdaQueryWrapper<Question>()
                            .select(Question::getId)
                            .eq(Question::getUserId, userId)
                            .eq(Question::getSubject, subject))
                    .stream().map(Question::getId).toList();
            if (questionIds.isEmpty()) {
                return PageResult.empty(current, limit);
            }
            wrapper.in(WrongQuestion::getQuestionId, questionIds);
        }

        IPage<WrongQuestion> result = wrongQuestionMapper.selectPage(new Page<>(current, limit), wrapper);
        if (result.getRecords().isEmpty()) {
            return PageResult.empty(current, limit);
        }

        List<Long> pageQuestionIds = result.getRecords().stream().map(WrongQuestion::getQuestionId).toList();
        Map<Long, Question> questionMap = new HashMap<>();
        for (Question question : questionMapper.selectBatchIds(pageQuestionIds)) {
            questionMap.put(question.getId(), question);
        }

        List<WrongQuestionVO> records = new ArrayList<>();
        for (WrongQuestion row : result.getRecords()) {
            Question question = questionMap.get(row.getQuestionId());
            if (question != null) {
                records.add(toVO(row, question));
            }
        }
        PageResult<WrongQuestionVO> pageResult = new PageResult<>();
        pageResult.setRecords(records);
        pageResult.setTotal(result.getTotal());
        pageResult.setPage(result.getCurrent());
        pageResult.setSize(result.getSize());
        return pageResult;
    }

    public void markMastered(Long id, boolean mastered) {
        WrongQuestion row = getOwned(id);
        row.setMastered(mastered ? 1 : 0);
        row.setUpdatedAt(LocalDateTime.now());
        wrongQuestionMapper.updateById(row);
    }

    public void delete(Long id) {
        WrongQuestion row = getOwned(id);
        wrongQuestionMapper.deleteById(row.getId());
    }

    private WrongQuestion getOwned(Long id) {
        WrongQuestion row = wrongQuestionMapper.selectById(id);
        if (row == null) {
            throw new BizException(ResultCode.NOT_FOUND.getCode(), "错题记录不存在");
        }
        if (!row.getUserId().equals(UserContext.get())) {
            throw new BizException(ResultCode.FORBIDDEN);
        }
        return row;
    }

    private WrongQuestionVO toVO(WrongQuestion row, Question question) {
        WrongQuestionVO vo = new WrongQuestionVO();
        vo.setId(row.getId());
        vo.setQuestionId(question.getId());
        vo.setSubject(question.getSubject());
        vo.setKnowledgePoint(question.getKnowledgePoint());
        vo.setType(question.getType());
        vo.setContent(question.getContent());
        vo.setOptions(parseOptions(question.getOptions()));
        vo.setAnswer(question.getAnswer());
        vo.setAnalysis(question.getAnalysis());
        vo.setUserAnswer(row.getUserAnswer());
        vo.setWrongCount(row.getWrongCount());
        vo.setMastered(row.getMastered() != null && row.getMastered() == 1);
        vo.setUpdatedAt(row.getUpdatedAt());
        return vo;
    }

    private List<String> parseOptions(String options) {
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
