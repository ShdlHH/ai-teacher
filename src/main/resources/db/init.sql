-- =====================================================
-- AI 智能老师 初始化脚本（MySQL 8）
-- 执行：mysql -uroot -p < db/init.sql
-- =====================================================

CREATE DATABASE IF NOT EXISTS ai_teacher DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE ai_teacher;

-- 用户表
CREATE TABLE IF NOT EXISTS `user` (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    username    VARCHAR(50)  NOT NULL COMMENT '登录名',
    password    VARCHAR(100) NOT NULL COMMENT 'BCrypt 密文',
    nickname    VARCHAR(50)  NULL COMMENT '昵称',
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    UNIQUE KEY uk_username (username)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '用户表';

-- 会话表
CREATE TABLE IF NOT EXISTS conversation (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id    BIGINT       NOT NULL COMMENT '所属用户',
    title      VARCHAR(100) NOT NULL DEFAULT '新对话' COMMENT '会话标题',
    subject    VARCHAR(32)  NOT NULL DEFAULT '通用' COMMENT '学科：通用/数学/语文/英语/物理/化学',
    created_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    KEY idx_user (user_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '会话表';

-- 消息表
CREATE TABLE IF NOT EXISTS chat_message (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    conversation_id BIGINT       NOT NULL COMMENT '所属会话',
    role            VARCHAR(16)  NOT NULL COMMENT '角色：user/assistant/system',
    content         MEDIUMTEXT   NOT NULL COMMENT '消息内容',
    citations       TEXT         NULL COMMENT '引用资料（JSON 数组）',
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    KEY idx_conv (conversation_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '对话消息表';

-- =====================================================
-- 以下为阶段 3-4（RAG 知识库 / 教学功能）表
-- =====================================================

-- 知识库文档表
CREATE TABLE IF NOT EXISTS knowledge_document (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id     BIGINT       NOT NULL COMMENT '上传用户',
    file_name   VARCHAR(255) NOT NULL COMMENT '原始文件名',
    file_type   VARCHAR(20)  NOT NULL COMMENT '文件类型：pdf/docx/md/txt',
    file_size   BIGINT       NOT NULL DEFAULT 0 COMMENT '文件字节数',
    chunk_count INT          NOT NULL DEFAULT 0 COMMENT '切分片段数',
    status      VARCHAR(20)  NOT NULL DEFAULT 'PROCESSING' COMMENT '状态：PROCESSING/READY/FAILED',
    error_msg   VARCHAR(500) NULL COMMENT '失败原因',
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    KEY idx_user (user_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '知识库文档表';

-- 知识库片段表
CREATE TABLE IF NOT EXISTS knowledge_segment (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    document_id BIGINT      NOT NULL COMMENT '所属文档',
    user_id     BIGINT      NOT NULL COMMENT '所属用户（检索隔离）',
    chunk_index INT         NOT NULL COMMENT '片段序号（从 0 开始）',
    content     TEXT        NOT NULL COMMENT '片段文本',
    vector_id   VARCHAR(64) NOT NULL COMMENT '向量库中的 ID',
    created_at  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    KEY idx_doc (document_id),
    KEY idx_user (user_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '知识库片段表';

-- 题目表
CREATE TABLE IF NOT EXISTS question (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id         BIGINT       NOT NULL COMMENT '出题对象用户',
    subject         VARCHAR(32)  NOT NULL COMMENT '学科',
    knowledge_point VARCHAR(100) NULL COMMENT '知识点',
    type            VARCHAR(20)  NOT NULL COMMENT '题型：choice/blank/short_answer',
    content         TEXT         NOT NULL COMMENT '题干',
    options         TEXT         NULL COMMENT '选择题选项（JSON 数组）',
    answer          TEXT         NULL COMMENT '参考答案',
    analysis        TEXT         NULL COMMENT '解析',
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    KEY idx_user_subject (user_id, subject)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '题目表';

-- 作答记录表
CREATE TABLE IF NOT EXISTS question_attempt (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id     BIGINT   NOT NULL COMMENT '用户',
    question_id BIGINT   NOT NULL COMMENT '题目',
    user_answer TEXT     NULL COMMENT '用户作答',
    correct     TINYINT  NOT NULL DEFAULT 0 COMMENT '是否正确：0/1',
    score       INT      NOT NULL DEFAULT 0 COMMENT '得分（0-100）',
    comment     VARCHAR(1000) NULL COMMENT '批改评语',
    created_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '作答时间',
    KEY idx_user (user_id),
    KEY idx_question (question_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '作答记录表';

-- 错题表
CREATE TABLE IF NOT EXISTS wrong_question (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id       BIGINT   NOT NULL COMMENT '用户',
    question_id   BIGINT   NOT NULL COMMENT '题目',
    user_answer   TEXT     NULL COMMENT '用户作答',
    wrong_count   INT      NOT NULL DEFAULT 1 COMMENT '错误次数',
    mastered      TINYINT  NOT NULL DEFAULT 0 COMMENT '是否已掌握：0/1',
    created_at    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY uk_user_question (user_id, question_id),
    KEY idx_user (user_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '错题表';

-- 学习记录表
CREATE TABLE IF NOT EXISTS study_record (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id         BIGINT       NOT NULL COMMENT '用户',
    conversation_id BIGINT       NULL COMMENT '关联会话',
    subject         VARCHAR(32)  NOT NULL DEFAULT '通用' COMMENT '学科',
    summary         VARCHAR(500) NULL COMMENT '本次学习内容摘要',
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    KEY idx_user (user_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '学习记录表';
