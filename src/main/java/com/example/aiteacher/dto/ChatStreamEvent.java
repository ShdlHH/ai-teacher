package com.example.aiteacher.dto;

/**
 * SSE 流式事件：event 为事件名（token/citations/done），data 为事件数据
 */
public record ChatStreamEvent(String event, String data) {

    public static ChatStreamEvent token(String content) {
        return new ChatStreamEvent("token", content);
    }

    public static ChatStreamEvent citations(String json) {
        return new ChatStreamEvent("citations", json);
    }

    public static ChatStreamEvent done() {
        return new ChatStreamEvent("done", "[DONE]");
    }
}
