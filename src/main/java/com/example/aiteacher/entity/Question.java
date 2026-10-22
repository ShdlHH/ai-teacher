package com.example.aiteacher.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("question")
public class Question {

    public static final String TYPE_CHOICE = "choice";
    public static final String TYPE_BLANK = "blank";
    public static final String TYPE_SHORT_ANSWER = "short_answer";

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private String subject;

    private String knowledgePoint;

    private String type;

    private String content;

    /** 选择题选项（JSON 数组字符串） */
    private String options;

    private String answer;

    private String analysis;

    private LocalDateTime createdAt;
}
