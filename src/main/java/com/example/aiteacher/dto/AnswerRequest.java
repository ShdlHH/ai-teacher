package com.example.aiteacher.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 作答请求
 */
@Data
public class AnswerRequest {

    @NotNull(message = "题目 ID 不能为空")
    private Long questionId;

    @NotBlank(message = "作答内容不能为空")
    private String userAnswer;
}
