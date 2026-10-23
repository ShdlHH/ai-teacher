package com.example.aiteacher.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.File;
import java.util.List;

/**
 * 向量库读写统一入口：入库、按文档删除都经过这里。
 * 面向 VectorStore 接口，simple 模式额外把内存向量落盘到 JSON；pgvector 模式由数据库持久化。
 */
@Slf4j
@Component
public class VectorStorePersistence {

    private final VectorStore vectorStore;
    private final ObjectProvider<SimpleVectorStore> simpleVectorStore;

    @Value("${ai.teacher.vector-store.path}")
    private String storePath;

    public VectorStorePersistence(VectorStore vectorStore,
                                  ObjectProvider<SimpleVectorStore> simpleVectorStore) {
        this.vectorStore = vectorStore;
        this.simpleVectorStore = simpleVectorStore;
    }

    public void add(List<Document> documents) {
        vectorStore.add(documents);
        saveIfSimple();
    }

    public void deleteByDocumentId(Long documentId) {
        vectorStore.delete(new Filter.Expression(Filter.ExpressionType.EQ,
                new Filter.Key("documentId"), new Filter.Value(documentId)));
        saveIfSimple();
    }

    private void saveIfSimple() {
        SimpleVectorStore store = simpleVectorStore.getIfAvailable();
        if (store == null) {
            return;
        }
        File file = new File(storePath);
        File parent = file.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            log.warn("无法创建向量库目录：{}", parent.getAbsolutePath());
        }
        store.save(file);
    }
}
