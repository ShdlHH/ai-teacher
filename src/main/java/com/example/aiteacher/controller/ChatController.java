package com.example.aiteacher.controller;

import com.example.aiteacher.dto.ChatStreamEvent;
import com.example.aiteacher.service.ChatService;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

/**
 * SSE 流式对话接口：
 * GET /api/chat/stream?token=xxx&conversationId=1&message=你好
 * 事件类型：citations（引用列表 JSON）→ token（增量文本）→ done（结束标记）
 */
@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private final ChatService chatService;

    public ChatController(ChatService chatService) {
        this.chatService = chatService;
    }

    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> stream(@RequestParam Long conversationId,
                                                @RequestParam String message) {
        return chatService.stream(conversationId, message)
                .map(event -> toSse(event));
    }

    private ServerSentEvent<String> toSse(ChatStreamEvent event) {
        return ServerSentEvent.<String>builder(event.data())
                .event(event.event())
                .build();
    }
}
