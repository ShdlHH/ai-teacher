package com.example.aiteacher.service.tools;

import com.example.aiteacher.service.ConversationService;
import com.example.aiteacher.service.QuestionService;
import com.example.aiteacher.service.RagService;
import com.example.aiteacher.service.StatsService;
import com.example.aiteacher.service.WrongQuestionService;
import org.springframework.stereotype.Component;

/**
 * 按请求构建工具集：userId 在构造时绑定，模型无法通过参数越权访问其他用户数据
 */
@Component
public class StudyToolFactory {

    private final StatsService statsService;
    private final WrongQuestionService wrongQuestionService;
    private final RagService ragService;
    private final QuestionService questionService;
    private final ConversationService conversationService;

    public StudyToolFactory(StatsService statsService,
                            WrongQuestionService wrongQuestionService,
                            RagService ragService,
                            QuestionService questionService,
                            ConversationService conversationService) {
        this.statsService = statsService;
        this.wrongQuestionService = wrongQuestionService;
        this.ragService = ragService;
        this.questionService = questionService;
        this.conversationService = conversationService;
    }

    public Object[] build(Long userId) {
        return new Object[]{
                new StatsTools(userId, statsService),
                new WrongQuestionTools(userId, wrongQuestionService),
                new KnowledgeTools(userId, ragService),
                new QuizTools(userId, questionService),
                new ConversationTools(userId, conversationService)
        };
    }
}
