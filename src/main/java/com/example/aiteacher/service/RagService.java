package com.example.aiteacher.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.aiteacher.dto.Citation;
import com.example.aiteacher.dto.RagContext;
import com.example.aiteacher.entity.KnowledgeDocument;
import com.example.aiteacher.mapper.KnowledgeDocumentMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * RAG 检索：从当前用户的知识库片段中召回 topK，拼成提示词资料并返回引用列表。
 */
@Slf4j
@Service
public class RagService {

    private final VectorStore vectorStore;
    private final KnowledgeDocumentMapper documentMapper;

    @Value("${ai.teacher.rag.top-k}")
    private int topK;

    @Value("${ai.teacher.rag.similarity-threshold}")
    private double similarityThreshold;

    @Value("${ai.teacher.rag.max-context-chars}")
    private int maxContextChars;

    public RagService(VectorStore vectorStore, KnowledgeDocumentMapper documentMapper) {
        this.vectorStore = vectorStore;
        this.documentMapper = documentMapper;
    }

    public RagContext retrieve(Long userId, String query) {
        if (query == null || query.isBlank()) {
            return RagContext.empty();
        }
        Long readyCount = documentMapper.selectCount(
                new LambdaQueryWrapper<KnowledgeDocument>()
                        .eq(KnowledgeDocument::getUserId, userId)
                        .eq(KnowledgeDocument::getStatus, KnowledgeDocument.STATUS_READY));
        if (readyCount == null || readyCount == 0) {
            return RagContext.empty();
        }

        try {
            SearchRequest request = SearchRequest.builder()
                    .query(query)
                    .topK(topK)
                    .similarityThreshold(similarityThreshold)
                    .filterExpression(new Filter.Expression(Filter.ExpressionType.EQ,
                            new Filter.Key("userId"), new Filter.Value(userId)))
                    .build();
            List<Document> documents = vectorStore.similaritySearch(request);
            if (documents == null || documents.isEmpty()) {
                return RagContext.empty();
            }
            return buildContext(documents);
        } catch (Exception e) {
            // 检索失败不阻断对话，按无资料回答
            log.warn("知识库检索失败，降级为无资料回答：{}", e.getMessage());
            return RagContext.empty();
        }
    }

    private RagContext buildContext(List<Document> documents) {
        StringBuilder context = new StringBuilder("【参考资料】\n");
        List<Citation> citations = new ArrayList<>();
        int index = 0;
        for (Document document : documents) {
            String content = document.getText();
            if (content == null || content.isBlank()) {
                continue;
            }
            if (context.length() + content.length() > maxContextChars) {
                int remain = maxContextChars - context.length();
                if (remain > 100) {
                    content = content.substring(0, remain);
                } else {
                    break;
                }
            }
            index++;
            String fileName = metadataString(document, "fileName");
            Integer chunkIndex = metadataInt(document, "chunkIndex");
            String documentId = metadataString(document, "documentId");

            context.append("[资料").append(index).append("] 来源《").append(fileName)
                    .append("》 第").append(chunkIndex + 1).append("段\n")
                    .append(content).append("\n\n");
            citations.add(new Citation(documentId, fileName, chunkIndex, content, document.getScore()));
        }
        if (citations.isEmpty()) {
            return RagContext.empty();
        }
        return new RagContext(context.toString(), citations);
    }

    private String metadataString(Document document, String key) {
        Object value = document.getMetadata().get(key);
        return value == null ? "" : String.valueOf(value);
    }

    private Integer metadataInt(Document document, String key) {
        Object value = document.getMetadata().get(key);
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return value == null ? 0 : Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
