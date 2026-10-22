package com.example.aiteacher.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.aiteacher.entity.KnowledgeDocument;
import com.example.aiteacher.entity.KnowledgeSegment;
import com.example.aiteacher.mapper.KnowledgeDocumentMapper;
import com.example.aiteacher.mapper.KnowledgeSegmentMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.springframework.ai.document.Document;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 知识库异步入库：解析 → 切分 → 片段落库（事务）→ 向量化 → 文档置 READY
 * 注意：异步线程没有 UserContext，userId 必须由调用方显式传入。
 */
@Slf4j
@Service
public class KnowledgeIngestService {

    private final KnowledgeDocumentMapper documentMapper;
    private final KnowledgeSegmentMapper segmentMapper;
    private final VectorStorePersistence vectorStorePersistence;
    private final TransactionTemplate transactionTemplate;

    private final TokenTextSplitter splitter = TokenTextSplitter.builder()
            .withChunkSize(400)
            .withMinChunkSizeChars(100)
            .withMinChunkLengthToEmbed(20)
            .withMaxNumChunks(10000)
            .withKeepSeparator(true)
            .build();

    public KnowledgeIngestService(KnowledgeDocumentMapper documentMapper,
                                  KnowledgeSegmentMapper segmentMapper,
                                  VectorStorePersistence vectorStorePersistence,
                                  TransactionTemplate transactionTemplate) {
        this.documentMapper = documentMapper;
        this.segmentMapper = segmentMapper;
        this.vectorStorePersistence = vectorStorePersistence;
        this.transactionTemplate = transactionTemplate;
    }

    @Async
    public void process(Long documentId, Long userId, String fileName, String fileType, byte[] bytes) {
        try {
            if (documentMapper.selectById(documentId) == null) {
                log.warn("知识库文档 {} 已被删除，跳过处理", documentId);
                return;
            }
            String text = extractText(fileType, bytes);
            if (!StringUtils.hasText(text)) {
                throw new IllegalStateException("未能从文件中提取到有效文字");
            }

            List<Document> chunks = splitter.apply(List.of(new Document(text)));
            List<Document> vectorDocuments = new ArrayList<>();
            List<KnowledgeSegment> segments = new ArrayList<>();
            int index = 0;
            for (Document chunk : chunks) {
                String content = chunk.getText();
                if (!StringUtils.hasText(content)) {
                    continue;
                }
                String vectorId = UUID.randomUUID().toString();
                Map<String, Object> metadata = new HashMap<>();
                metadata.put("documentId", documentId);
                metadata.put("userId", userId);
                metadata.put("fileName", fileName);
                metadata.put("chunkIndex", index);
                vectorDocuments.add(new Document(vectorId, content, metadata));

                KnowledgeSegment segment = new KnowledgeSegment();
                segment.setDocumentId(documentId);
                segment.setUserId(userId);
                segment.setChunkIndex(index);
                segment.setContent(content);
                segment.setVectorId(vectorId);
                segment.setCreatedAt(LocalDateTime.now());
                segments.add(segment);
                index++;
            }
            if (segments.isEmpty()) {
                throw new IllegalStateException("文本切分后没有有效片段");
            }

            // 片段批量落库放在事务里，避免中途失败留下半截数据
            transactionTemplate.executeWithoutResult(status -> segments.forEach(segmentMapper::insert));
            try {
                vectorStorePersistence.add(vectorDocuments);
            } catch (RuntimeException e) {
                cleanupSegments(documentId);
                throw e;
            }

            KnowledgeDocument update = new KnowledgeDocument();
            update.setId(documentId);
            update.setChunkCount(segments.size());
            update.setStatus(KnowledgeDocument.STATUS_READY);
            update.setErrorMsg(null);
            documentMapper.updateById(update);
            log.info("知识库文档处理完成：{}，共 {} 段", fileName, segments.size());
        } catch (Exception e) {
            log.error("知识库文档处理失败：{}", fileName, e);
            cleanupSegments(documentId);
            markFailed(documentId, e.getMessage());
        }
    }

    private void cleanupSegments(Long documentId) {
        try {
            segmentMapper.delete(new LambdaQueryWrapper<KnowledgeSegment>()
                    .eq(KnowledgeSegment::getDocumentId, documentId));
        } catch (Exception e) {
            log.warn("回滚知识库片段失败：{}", e.getMessage());
        }
    }

    private void markFailed(Long documentId, String message) {
        KnowledgeDocument update = new KnowledgeDocument();
        update.setId(documentId);
        update.setStatus(KnowledgeDocument.STATUS_FAILED);
        update.setErrorMsg(abbreviate(message));
        documentMapper.updateById(update);
    }

    private String extractText(String fileType, byte[] bytes) throws IOException {
        return switch (fileType) {
            case "pdf" -> {
                try (PDDocument pdf = Loader.loadPDF(bytes)) {
                    yield new PDFTextStripper().getText(pdf);
                }
            }
            case "docx" -> {
                try (XWPFDocument docx = new XWPFDocument(new ByteArrayInputStream(bytes))) {
                    StringBuilder text = new StringBuilder();
                    docx.getParagraphs().forEach(p -> text.append(p.getText()).append('\n'));
                    yield text.toString();
                }
            }
            default -> new String(bytes, StandardCharsets.UTF_8);
        };
    }

    private String abbreviate(String message) {
        if (!StringUtils.hasText(message)) {
            return "未知错误";
        }
        return message.length() > 400 ? message.substring(0, 400) : message;
    }
}
