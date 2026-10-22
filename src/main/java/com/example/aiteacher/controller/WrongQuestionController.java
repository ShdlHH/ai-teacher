package com.example.aiteacher.controller;

import com.example.aiteacher.common.Result;
import com.example.aiteacher.dto.PageResult;
import com.example.aiteacher.dto.WrongQuestionVO;
import com.example.aiteacher.service.WrongQuestionService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 错题本接口：列表（可按学科/掌握状态筛选）、标记掌握、删除
 */
@RestController
@RequestMapping("/api/wrong-questions")
public class WrongQuestionController {

    private final WrongQuestionService wrongQuestionService;

    public WrongQuestionController(WrongQuestionService wrongQuestionService) {
        this.wrongQuestionService = wrongQuestionService;
    }

    @GetMapping
    public Result<PageResult<WrongQuestionVO>> list(@RequestParam(required = false) String subject,
                                                    @RequestParam(required = false) Boolean mastered,
                                                    @RequestParam(defaultValue = "1") long page,
                                                    @RequestParam(defaultValue = "10") long size) {
        return Result.ok(wrongQuestionService.listMine(subject, mastered, page, size));
    }

    @PutMapping("/{id}/mastered")
    public Result<Void> markMastered(@PathVariable Long id,
                                     @RequestParam(defaultValue = "true") boolean mastered) {
        wrongQuestionService.markMastered(id, mastered);
        return Result.ok();
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        wrongQuestionService.delete(id);
        return Result.ok();
    }
}
