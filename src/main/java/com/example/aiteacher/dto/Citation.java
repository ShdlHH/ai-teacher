package com.example.aiteacher.dto;

/**
 * 回答引用的知识库片段
 */
public record Citation(
        String documentId,
        String fileName,
        Integer chunkIndex,
        String content,
        Double score
) {
}
