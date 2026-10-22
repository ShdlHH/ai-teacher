package com.example.aiteacher.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 出题请求
 */
@Data
public class GenerateQuestionRequest {

    @NotBlank(message = "学科不能为空")
    private String subject;

    /** 知识点，可为空（为空则按学科随机出题） */
    private String knowledgePoint;

    /** 题目数量 1-5 */
    @Min(value = 1, message = "至少生成 1 道题")
    @Max(value = 5, message = "一次最多生成 5 道题")
    private Integer count = 3;

    /** 题型：choice/blank/short_answer，为空表示混合出题 */
    private String type;
}
