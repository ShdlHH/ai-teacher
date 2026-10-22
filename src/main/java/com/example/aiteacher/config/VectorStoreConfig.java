package com.example.aiteacher.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.File;

/**
 * 向量库配置：一期使用 Spring AI 内置 SimpleVectorStore（内存 + JSON 落盘），
 * 后续可无缝替换为 PgVector/Milvus 等实现（均为 VectorStore 接口）。
 */
@Slf4j
@Configuration
public class VectorStoreConfig {

    @Bean
    public SimpleVectorStore simpleVectorStore(EmbeddingModel embeddingModel,
                                               @Value("${ai.teacher.vector-store-path}") String storePath) {
        SimpleVectorStore vectorStore = SimpleVectorStore.builder(embeddingModel).build();
        File file = new File(storePath);
        if (file.exists()) {
            try {
                vectorStore.load(file);
                log.info("向量库已加载：{}", file.getAbsolutePath());
            } catch (Exception e) {
                log.warn("向量库加载失败，将使用空向量库：{}", e.getMessage());
            }
        }
        return vectorStore;
    }
}
