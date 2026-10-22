package com.example.aiteacher.dto;

import java.util.List;

/**
 * RAG 检索结果：拼接好的资料上下文 + 可溯源的引用列表
 */
public record RagContext(String contextText, List<Citation> citations) {

    public static RagContext empty() {
        return new RagContext("", List.of());
    }

    public boolean isEmpty() {
        return contextText == null || contextText.isBlank();
    }
}
