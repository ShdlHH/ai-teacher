package com.example.aiteacher.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 题目展示对象：options 由数据库 JSON 字符串还原为数组
 */
@Data
public class QuestionVO {

    private Long id;

    private String subject;

    private String knowledgePoint;

    private String type;

    private String content;

    private List<String> options;

    private String answer;

    private String analysis;

    private LocalDateTime createdAt;
}
