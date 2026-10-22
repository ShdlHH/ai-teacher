package com.example.aiteacher.controller;

import com.example.aiteacher.common.Result;
import com.example.aiteacher.dto.PageResult;
import com.example.aiteacher.entity.KnowledgeDocument;
import com.example.aiteacher.entity.KnowledgeSegment;
import com.example.aiteacher.service.KnowledgeService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 知识库接口：上传文档、列表、删除、查看片段
 */
@RestController
@RequestMapping("/api/knowledge")
public class KnowledgeController {

    private final KnowledgeService knowledgeService;

    public KnowledgeController(KnowledgeService knowledgeService) {
        this.knowledgeService = knowledgeService;
    }

    @PostMapping(value = "/documents", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<KnowledgeDocument> upload(@RequestParam("file") MultipartFile file) {
        return Result.ok(knowledgeService.upload(file));
    }

    @GetMapping("/documents")
    public Result<PageResult<KnowledgeDocument>> list(@RequestParam(defaultValue = "1") long page,
                                                      @RequestParam(defaultValue = "10") long size) {
        return Result.ok(knowledgeService.listMine(page, size));
    }

    @DeleteMapping("/documents/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        knowledgeService.delete(id);
        return Result.ok();
    }

    @GetMapping("/documents/{id}/segments")
    public Result<List<KnowledgeSegment>> segments(@PathVariable Long id) {
        return Result.ok(knowledgeService.listSegments(id));
    }
}
