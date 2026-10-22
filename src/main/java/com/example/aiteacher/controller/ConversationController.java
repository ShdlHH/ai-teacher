package com.example.aiteacher.controller;

import com.example.aiteacher.common.Result;
import com.example.aiteacher.dto.ConversationCreateRequest;
import com.example.aiteacher.entity.ChatMessage;
import com.example.aiteacher.entity.Conversation;
import com.example.aiteacher.service.ConversationService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/conversations")
public class ConversationController {

    private final ConversationService conversationService;

    public ConversationController(ConversationService conversationService) {
        this.conversationService = conversationService;
    }

    @GetMapping
    public Result<List<Conversation>> list() {
        return Result.ok(conversationService.listMine());
    }

    @PostMapping
    public Result<Conversation> create(@Valid @RequestBody(required = false) ConversationCreateRequest req) {
        return Result.ok(conversationService.create(req == null ? null : req.getSubject()));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        conversationService.delete(id);
        return Result.ok();
    }

    @GetMapping("/{id}/messages")
    public Result<List<ChatMessage>> messages(@PathVariable Long id) {
        return Result.ok(conversationService.listMessages(id));
    }
}
