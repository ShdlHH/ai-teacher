package com.example.aiteacher.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.io.File;

/**
 * 向量库配置，通过 ai.teacher.vector-store.type 切换实现：
 * 1. simple：Spring AI 内置 SimpleVectorStore（内存 + JSON 落盘，默认，无需额外中间件）
 * 2. pgvector：PostgreSQL + pgvector（HNSW 索引），使用独立数据源，与业务 MySQL 分离
 */
@Slf4j
@Configuration
public class VectorStoreConfig {

    @Bean
    @ConditionalOnProperty(prefix = "ai.teacher.vector-store", name = "type",
            havingValue = "simple", matchIfMissing = true)
    public SimpleVectorStore simpleVectorStore(EmbeddingModel embeddingModel,
                                               @Value("${ai.teacher.vector-store.path}") String storePath) {
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

    @Bean
    @ConditionalOnProperty(prefix = "ai.teacher.vector-store", name = "type", havingValue = "pgvector")
    public VectorStore pgVectorStore(EmbeddingModel embeddingModel,
                                     @Value("${ai.teacher.vector-store.pg.url}") String url,
                                     @Value("${ai.teacher.vector-store.pg.username}") String username,
                                     @Value("${ai.teacher.vector-store.pg.password}") String password,
                                     @Value("${ai.teacher.vector-store.pg.schema}") String schema,
                                     @Value("${ai.teacher.vector-store.pg.table}") String table,
                                     @Value("${ai.teacher.vector-store.pg.dimensions}") int dimensions) {
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName("org.postgresql.Driver");
        dataSource.setUrl(url);
        dataSource.setUsername(username);
        dataSource.setPassword(password);

        VectorStore vectorStore = PgVectorStore.builder(new JdbcTemplate(dataSource), embeddingModel)
                .schemaName(schema)
                .vectorTableName(table)
                .dimensions(dimensions)
                .distanceType(PgVectorStore.PgDistanceType.COSINE_DISTANCE)
                .indexType(PgVectorStore.PgIndexType.HNSW)
                .initializeSchema(true)
                .build();
        log.info("使用 pgvector 向量库：{} / {}.{}", url, schema, table);
        return vectorStore;
    }
}
