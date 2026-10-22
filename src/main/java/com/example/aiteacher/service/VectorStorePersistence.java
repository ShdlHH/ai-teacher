package com.example.aiteacher.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.File;
import java.util.List;

/**
 * 向量库读写与 JSON 落盘的统一入口：入库、按文档删除都经过这里，避免两处重复处理目录创建
 */
@Slf4j
@Component
public class VectorStorePersistence {

    private final SimpleVectorStore vectorStore;

    @Value("${ai.teacher.vector-store-path}")
    private String storePath;

    public VectorStorePersistence(SimpleVectorStore vectorStore) {
        this.vectorStore = vectorStore;
    }

    public void add(List<Document> documents) {
        vectorStore.add(documents);
        save();
    }

    public void deleteByDocumentId(Long documentId) {
        vectorStore.delete(new Filter.Expression(Filter.ExpressionType.EQ,
                new Filter.Key("documentId"), new Filter.Value(documentId)));
        save();
    }

    private void save() {
        File file = new File(storePath);
        File parent = file.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            log.warn("无法创建向量库目录：{}", parent.getAbsolutePath());
        }
        vectorStore.save(file);
    }
}
