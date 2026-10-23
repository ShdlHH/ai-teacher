package com.example.aiteacher.config;

import com.example.aiteacher.service.memory.DbChatMemory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.ollama.api.OllamaOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;

/**
 * ChatClient 配置：
 * 1. 对话 ChatClient：挂载"AI 老师"系统提示词 + 数据库会话记忆 Advisor
 * 2. 出题/批改 ChatClient：低温度 + JSON 输出，不挂会话记忆
 */
@Configuration
public class ChatClientConfig {

    @Bean
    public ChatClient chatClient(ChatClient.Builder builder,
                                 DbChatMemory chatMemory,
                                 @Value("classpath:/prompts/teacher-system.st") Resource systemPrompt) {
        return builder
                .defaultSystem(systemPrompt)
                .defaultAdvisors(MessageChatMemoryAdvisor.builder(chatMemory).build())
                .build();
    }

    @Bean
    public ChatClient quizChatClient(ChatClient.Builder builder,
                                     @Value("classpath:/prompts/quiz-system.st") Resource quizPrompt,
                                     @Value("${ai.teacher.quiz.model}") String model,
                                     @Value("${ai.teacher.quiz.temperature}") double temperature) {
        return builder
                .defaultSystem(quizPrompt)
                .defaultOptions(OllamaOptions.builder()
                        .model(model)
                        .temperature(temperature)
                        // 与对话共用同一上下文窗口，避免工具内调用出题时 Ollama 反复重载模型
                        .numCtx(8192)
                        .format("json")
                        .build())
                .build();
    }
}
