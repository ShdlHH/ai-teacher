package com.example.aiteacher.controller;

import com.example.aiteacher.common.Result;
import com.example.aiteacher.dto.AnswerRequest;
import com.example.aiteacher.dto.AnswerResult;
import com.example.aiteacher.dto.GenerateQuestionRequest;
import com.example.aiteacher.dto.PageResult;
import com.example.aiteacher.dto.QuestionVO;
import com.example.aiteacher.service.QuestionService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 练习接口：AI 出题、题目列表/详情、提交作答
 */
@RestController
@RequestMapping("/api/quiz")
public class QuizController {

    private final QuestionService questionService;

    public QuizController(QuestionService questionService) {
        this.questionService = questionService;
    }

    @PostMapping("/generate")
    public Result<List<QuestionVO>> generate(@Valid @RequestBody GenerateQuestionRequest request) {
        return Result.ok(questionService.generate(request));
    }

    @GetMapping("/questions")
    public Result<PageResult<QuestionVO>> questions(@RequestParam(required = false) String subject,
                                                    @RequestParam(defaultValue = "1") long page,
                                                    @RequestParam(defaultValue = "20") long size) {
        return Result.ok(questionService.listMine(subject, page, size));
    }

    @GetMapping("/questions/{id}")
    public Result<QuestionVO> detail(@PathVariable Long id) {
        return Result.ok(questionService.detail(id));
    }

    @PostMapping("/answer")
    public Result<AnswerResult> answer(@Valid @RequestBody AnswerRequest request) {
        return Result.ok(questionService.submit(request));
    }
}
