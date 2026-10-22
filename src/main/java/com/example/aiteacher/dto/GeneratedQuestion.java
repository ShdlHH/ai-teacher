package com.example.aiteacher.dto;

import lombok.Data;

import java.util.List;

/**
 * AI 返回的单道题目（解析后的中间结构）
 */
@Data
public class GeneratedQuestion {

    private String type;

    private String content;

    private List<String> options;

    private String answer;

    private String analysis;

    private String knowledgePoint;
}
