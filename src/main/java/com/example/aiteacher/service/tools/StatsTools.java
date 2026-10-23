package com.example.aiteacher.service.tools;

import com.example.aiteacher.service.StatsService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;

import java.util.Map;

/**
 * 学习统计工具：仅查询构造时绑定的当前用户，不接受模型传入 userId
 */
@Slf4j
public class StatsTools {

    private final Long userId;
    private final StatsService statsService;

    public StatsTools(Long userId, StatsService statsService) {
        this.userId = userId;
        this.statsService = statsService;
    }

    @Tool(name = "getLearningStats",
            description = "查询当前学生的学习统计：题目总数、作答次数、答对次数、正确率、错题总数、"
                    + "未掌握错题数、已掌握错题数。学生询问学习情况、正确率、错题数量、学习进度时调用。")
    public Map<String, Object> getLearningStats() {
        log.info("[Tool] getLearningStats userId={}", userId);
        return statsService.overviewFor(userId);
    }
}
