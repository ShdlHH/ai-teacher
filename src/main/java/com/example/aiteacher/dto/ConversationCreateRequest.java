package com.example.aiteacher.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ConversationCreateRequest {

    @Size(max = 32, message = "学科最长 32 字")
    private String subject;
}
