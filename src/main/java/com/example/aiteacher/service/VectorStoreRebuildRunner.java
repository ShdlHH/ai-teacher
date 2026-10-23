package com.example.aiteacher.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.aiteacher.entity.KnowledgeDocument;
import com.example.aiteacher.entity.KnowledgeSegment;
import com.example.aiteacher.mapper.KnowledgeDocumentMapper;
import com.example.aiteacher.mapper.KnowledgeSegmentMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 切换向量库后的一次性重建任务：直接复用 knowledge_segment 中已保存的片段原文重新向量化，
 * 避免让用户重新上传文件。通过 ai.teacher.vector-store.rebuild-on-start=true 开启。
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "ai.teacher.vector-store", name = "rebuild-on-start", havingValue = "true")
public class VectorStoreRebuildRunner implements ApplicationRunner {

    private final KnowledgeDocumentMapper documentMapper;
    private final KnowledgeSegmentMapper segmentMapper;
    private final VectorStorePersistence vectorStorePersistence;

    public VectorStoreRebuildRunner(KnowledgeDocumentMapper documentMapper,
                                    KnowledgeSegmentMapper segmentMapper,
                                    VectorStorePersistence vectorStorePersistence) {
        this.documentMapper = documentMapper;
        this.segmentMapper = segmentMapper;
        this.vectorStorePersistence = vectorStorePersistence;
    }

    @Override
    public void run(ApplicationArguments args) {
        List<KnowledgeDocument> documents = documentMapper.selectList(
                new LambdaQueryWrapper<KnowledgeDocument>()
                        .eq(KnowledgeDocument::getStatus, KnowledgeDocument.STATUS_READY));
        if (documents.isEmpty()) {
            log.info("向量库重建：没有 READY 状态的文档，跳过");
            return;
        }

        int documentCount = 0;
        int segmentCount = 0;
        for (KnowledgeDocument document : documents) {
            try {
                List<KnowledgeSegment> segments = segmentMapper.selectList(
                        new LambdaQueryWrapper<KnowledgeSegment>()
                                .eq(KnowledgeSegment::getDocumentId, document.getId())
                                .orderByAsc(KnowledgeSegment::getChunkIndex));
                if (segments.isEmpty()) {
                    continue;
                }
                vectorStorePersistence.deleteByDocumentId(document.getId());

                List<Document> vectorDocuments = new ArrayList<>();
                for (KnowledgeSegment segment : segments) {
                    Map<String, Object> metadata = new HashMap<>();
                    metadata.put("documentId", document.getId());
                    metadata.put("userId", document.getUserId());
                    metadata.put("fileName", document.getFileName());
                    metadata.put("chunkIndex", segment.getChunkIndex());
                    vectorDocuments.add(new Document(segment.getVectorId(), segment.getContent(), metadata));
                }
                vectorStorePersistence.add(vectorDocuments);
                documentCount++;
                segmentCount += segments.size();
            } catch (Exception e) {
                log.error("向量库重建失败，文档 {}：{}", document.getId(), e.getMessage(), e);
            }
        }
        log.info("向量库重建完成：{} 个文档，{} 个片段", documentCount, segmentCount);
    }
}
