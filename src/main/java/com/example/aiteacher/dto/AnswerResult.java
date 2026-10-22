package com.example.aiteacher.dto;

import lombok.Data;

/**
 * 批改结果
 */
@Data
public class AnswerResult {

    private boolean correct;

    private int score;

    private String comment;

    private String standardAnswer;

    private String analysis;

    /** 是否已加入错题本 */
    private boolean addedToWrongBook;
}
