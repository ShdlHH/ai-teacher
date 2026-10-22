package com.example.aiteacher.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 错题本条目（错题记录 + 题目详情）
 */
@Data
public class WrongQuestionVO {

    private Long id;

    private Long questionId;

    private String subject;

    private String knowledgePoint;

    private String type;

    private String content;

    private List<String> options;

    private String answer;

    private String analysis;

    private String userAnswer;

    private Integer wrongCount;

    private Boolean mastered;

    private LocalDateTime updatedAt;
}
