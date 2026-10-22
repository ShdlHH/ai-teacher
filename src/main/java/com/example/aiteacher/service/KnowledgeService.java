package com.example.aiteacher.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.aiteacher.common.BizException;
import com.example.aiteacher.common.ResultCode;
import com.example.aiteacher.common.UserContext;
import com.example.aiteacher.dto.PageResult;
import com.example.aiteacher.entity.KnowledgeDocument;
import com.example.aiteacher.entity.KnowledgeSegment;
import com.example.aiteacher.mapper.KnowledgeDocumentMapper;
import com.example.aiteacher.mapper.KnowledgeSegmentMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

/**
 * 知识库：上传只做校验与建记录，解析/切分/向量化交给 KnowledgeIngestService 异步执行；
 * 列表分页、删除、片段查看保持同步。
 */
@Slf4j
@Service
public class KnowledgeService {

    private static final Set<String> SUPPORTED_TYPES = Set.of("pdf", "docx", "md", "txt");

    private final KnowledgeDocumentMapper documentMapper;
    private final KnowledgeSegmentMapper segmentMapper;
    private final KnowledgeIngestService ingestService;
    private final VectorStorePersistence vectorStorePersistence;

    public KnowledgeService(KnowledgeDocumentMapper documentMapper,
                            KnowledgeSegmentMapper segmentMapper,
                            KnowledgeIngestService ingestService,
                            VectorStorePersistence vectorStorePersistence) {
        this.documentMapper = documentMapper;
        this.segmentMapper = segmentMapper;
        this.ingestService = ingestService;
        this.vectorStorePersistence = vectorStorePersistence;
    }

    /**
     * 上传后立即返回 PROCESSING 记录，后台线程完成解析与向量化
     */
    public KnowledgeDocument upload(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BizException(ResultCode.PARAM_ERROR.getCode(), "请选择要上传的文件");
        }
        String fileName = sanitizeFileName(file.getOriginalFilename());
        String fileType = fileType(fileName);
        if (fileType == null) {
            throw new BizException(ResultCode.PARAM_ERROR.getCode(), "仅支持 pdf/docx/md/txt 文件");
        }

        Long userId = UserContext.get();
        KnowledgeDocument document = new KnowledgeDocument();
        document.setUserId(userId);
        document.setFileName(fileName);
        document.setFileType(fileType);
        document.setFileSize(file.getSize());
        document.setChunkCount(0);
        document.setStatus(KnowledgeDocument.STATUS_PROCESSING);
        document.setCreatedAt(LocalDateTime.now());
        documentMapper.insert(document);

        try {
            ingestService.process(document.getId(), userId, fileName, fileType, file.getBytes());
        } catch (IOException e) {
            markFailed(document.getId(), "读取上传文件失败");
            throw new BizException(ResultCode.SERVER_ERROR.getCode(), "读取上传文件失败");
        }
        return document;
    }

    public PageResult<KnowledgeDocument> listMine(long page, long size) {
        long current = Math.max(page, 1);
        long limit = Math.min(Math.max(size, 1), 100);
        IPage<KnowledgeDocument> result = documentMapper.selectPage(new Page<>(current, limit),
                new LambdaQueryWrapper<KnowledgeDocument>()
                        .eq(KnowledgeDocument::getUserId, UserContext.get())
                        .orderByDesc(KnowledgeDocument::getId));
        return PageResult.of(result);
    }

    public void delete(Long documentId) {
        KnowledgeDocument document = getOwned(documentId);
        try {
            vectorStorePersistence.deleteByDocumentId(document.getId());
        } catch (Exception e) {
            log.warn("删除文档向量失败：{}", e.getMessage());
        }
        segmentMapper.delete(new LambdaQueryWrapper<KnowledgeSegment>()
                .eq(KnowledgeSegment::getDocumentId, document.getId()));
        documentMapper.deleteById(document.getId());
    }

    public List<KnowledgeSegment> listSegments(Long documentId) {
        getOwned(documentId);
        return segmentMapper.selectList(new LambdaQueryWrapper<KnowledgeSegment>()
                .eq(KnowledgeSegment::getDocumentId, documentId)
                .orderByAsc(KnowledgeSegment::getChunkIndex));
    }

    private void markFailed(Long documentId, String message) {
        KnowledgeDocument update = new KnowledgeDocument();
        update.setId(documentId);
        update.setStatus(KnowledgeDocument.STATUS_FAILED);
        update.setErrorMsg(message);
        documentMapper.updateById(update);
    }

    private KnowledgeDocument getOwned(Long documentId) {
        KnowledgeDocument document = documentMapper.selectById(documentId);
        if (document == null) {
            throw new BizException(ResultCode.NOT_FOUND.getCode(), "文档不存在");
        }
        if (!document.getUserId().equals(UserContext.get())) {
            throw new BizException(ResultCode.FORBIDDEN);
        }
        return document;
    }

    private String sanitizeFileName(String original) {
        String name = StringUtils.hasText(original) ? original : "未命名文件";
        try {
            return Paths.get(name).getFileName().toString();
        } catch (Exception e) {
            return name;
        }
    }

    private String fileType(String fileName) {
        int dot = fileName.lastIndexOf('.');
        if (dot < 0 || dot == fileName.length() - 1) {
            return null;
        }
        String type = fileName.substring(dot + 1).toLowerCase();
        return SUPPORTED_TYPES.contains(type) ? type : null;
    }
}
