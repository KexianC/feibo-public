-- Schema-neutral GEO baseline. Select the target database before executing this file.
-- The unified Feibo deployment applies these tables to the existing Feibo schema.

CREATE TABLE IF NOT EXISTS `doctor_info`
(
    `id`                    BIGINT       NOT NULL COMMENT '医生ID',
    `doctor_name`           VARCHAR(100) NOT NULL COMMENT '医生姓名',

    `hospital_id`           BIGINT       NOT NULL COMMENT '医院ID',
    `hospital_name`         VARCHAR(255) NOT NULL COMMENT '医院名称',

    `department_id`         BIGINT       NOT NULL COMMENT '科室ID',
    `department_name`       VARCHAR(255) NOT NULL COMMENT '科室名称',

    `title_id`              BIGINT       DEFAULT NULL COMMENT '职称ID',
    `title_name`            VARCHAR(100) DEFAULT NULL COMMENT '职称名称',

    `gender`                TINYINT      DEFAULT 0 COMMENT '性别，0-未知，1-男，2-女',

    `avatar_url`            VARCHAR(1024) DEFAULT NULL COMMENT '医生头像URL',

    `specialty`             TEXT COMMENT '专业特长',

    `knowledge_status` TINYINT    NOT NULL DEFAULT 0 COMMENT '知识库状态，0-未上传，1-可用，2-存在异常',

    `enable_status`         TINYINT      NOT NULL DEFAULT 1 COMMENT '启用状态，0-禁用，1-启用',

    `remark`                VARCHAR(500) DEFAULT NULL COMMENT '备注',

    `created_time`          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',

    `updated_time`          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP
    ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',

    PRIMARY KEY (`id`),

    KEY `idx_doctor_name` (`doctor_name`),
    KEY `idx_hospital_id` (`hospital_id`),
    KEY `idx_department_id` (`department_id`),
    KEY `idx_title_id` (`title_id`),
    KEY `idx_enable_status` (`enable_status`),
    KEY `idx_knowledge_status` (`knowledge_status`)

    ) ENGINE = InnoDB
    DEFAULT CHARSET = utf8mb4
    COLLATE = utf8mb4_unicode_ci
    COMMENT ='医生信息表';

CREATE TABLE IF NOT EXISTS `hospital_info`
(
    `id`                BIGINT       NOT NULL COMMENT '医院ID',

    `hospital_name`     VARCHAR(255) NOT NULL COMMENT '医院名称',

    `hospital_level`    VARCHAR(50)  DEFAULT NULL COMMENT '医院等级，例如三甲、三级',

    `hospital_type`     VARCHAR(100) DEFAULT NULL COMMENT '医院类型，例如综合医院、中医院',

    `province`          VARCHAR(100) DEFAULT NULL COMMENT '省份',

    `city`              VARCHAR(100) DEFAULT NULL COMMENT '城市',

    `district`          VARCHAR(100) DEFAULT NULL COMMENT '区县',

    `address`           VARCHAR(500) DEFAULT NULL COMMENT '详细地址',

    `phone`             VARCHAR(50)  DEFAULT NULL COMMENT '联系电话',

    `logo_url`          VARCHAR(1024) DEFAULT NULL COMMENT '医院Logo URL',

    `description`       TEXT COMMENT '医院简介',

    `enable_status`     TINYINT      NOT NULL DEFAULT 1 COMMENT '启用状态，0-禁用，1-启用',

    `created_time`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',

    `updated_time`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP
    ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',

    PRIMARY KEY (`id`),

    KEY `idx_hospital_name` (`hospital_name`),
    KEY `idx_city` (`city`),
    KEY `idx_enable_status` (`enable_status`)

    ) ENGINE = InnoDB
    DEFAULT CHARSET = utf8mb4
    COLLATE = utf8mb4_unicode_ci
    COMMENT ='医院信息表';

CREATE TABLE IF NOT EXISTS `department_info`
(
    `id`                BIGINT       NOT NULL COMMENT '科室ID',

    `hospital_id`       BIGINT                DEFAULT NULL COMMENT '所属医院ID',

    `hospital_name`     VARCHAR(255)          DEFAULT NULL COMMENT '所属医院名称',

    `department_name`   VARCHAR(255) NOT NULL COMMENT '科室名称',

    `department_desc`   TEXT COMMENT '科室简介',

    `enable_status`     TINYINT      NOT NULL DEFAULT 1 COMMENT '启用状态，0-禁用，1-启用',

    `created_time`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',

    `updated_time`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP
    ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',

    PRIMARY KEY (`id`),

    KEY `idx_department_name` (`department_name`),

    KEY `idx_department_hospital` (`hospital_id`)

    ) ENGINE = InnoDB
    DEFAULT CHARSET = utf8mb4
    COLLATE = utf8mb4_unicode_ci
    COMMENT ='科室信息表';

CREATE TABLE IF NOT EXISTS `title_info`
(
    `id`                BIGINT       NOT NULL COMMENT '职称ID',

    `title_name`        VARCHAR(100) NOT NULL COMMENT '职称名称',

    `title_level`       INT DEFAULT 0 COMMENT '职称级别',

    `description`       VARCHAR(500) DEFAULT NULL COMMENT '职称说明',

    `enable_status`     TINYINT      NOT NULL DEFAULT 1 COMMENT '启用状态',

    `created_time`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',

    `updated_time`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP
    ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',

    PRIMARY KEY (`id`),

    UNIQUE KEY `uk_title_name` (`title_name`)

    ) ENGINE = InnoDB
    DEFAULT CHARSET = utf8mb4
    COLLATE = utf8mb4_unicode_ci
    COMMENT ='医生职称表';

CREATE TABLE IF NOT EXISTS `system_user`
(
    `id`              BIGINT       NOT NULL COMMENT '用户ID',
    `username`        VARCHAR(100) NOT NULL COMMENT '登录用户名',
    `display_name`    VARCHAR(100) DEFAULT NULL COMMENT '显示名称',
    `role`            VARCHAR(32)  NOT NULL DEFAULT 'OPERATOR' COMMENT '角色：ADMIN/OPERATOR',
    `password_hash`   VARCHAR(255) NOT NULL COMMENT 'BCrypt密码哈希',
    `enable_status`   TINYINT      NOT NULL DEFAULT 1 COMMENT '启用状态，0-禁用，1-启用',
    `last_login_time` DATETIME     DEFAULT NULL COMMENT '最近登录时间',
    `created_time`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_time`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_system_user_username` (`username`),
    KEY `idx_system_user_role` (`role`),
    KEY `idx_system_user_enable` (`enable_status`)
    ) ENGINE = InnoDB
    DEFAULT CHARSET = utf8mb4
    COLLATE = utf8mb4_unicode_ci COMMENT ='系统用户表';

CREATE TABLE IF NOT EXISTS `system_setting`
(
    `id`              BIGINT       NOT NULL COMMENT '设置ID',
    `setting_key`     VARCHAR(128) NOT NULL COMMENT '设置键',
    `setting_value`   TEXT         DEFAULT NULL COMMENT '设置值',
    `setting_group`   VARCHAR(64)  DEFAULT NULL COMMENT '设置分组',
    `description`     VARCHAR(500) DEFAULT NULL COMMENT '说明',
    `sensitive`       TINYINT      NOT NULL DEFAULT 0 COMMENT '是否敏感',
    `created_time`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_time`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_system_setting_key` (`setting_key`),
    KEY `idx_system_setting_group` (`setting_group`)
    ) ENGINE = InnoDB
    DEFAULT CHARSET = utf8mb4
    COLLATE = utf8mb4_unicode_ci COMMENT ='系统设置表';

CREATE TABLE IF NOT EXISTS `system_operation_log`
(
    `id`            BIGINT       NOT NULL COMMENT '日志ID',
    `operator_id`   BIGINT       DEFAULT NULL COMMENT '操作人ID',
    `operator_name` VARCHAR(100) DEFAULT NULL COMMENT '操作人',
    `role`          VARCHAR(32)  DEFAULT NULL COMMENT '角色',
    `module_name`   VARCHAR(100) NOT NULL COMMENT '模块',
    `action_name`   VARCHAR(100) NOT NULL COMMENT '动作',
    `method`        VARCHAR(16)  NOT NULL COMMENT 'HTTP方法',
    `path`          VARCHAR(255) NOT NULL COMMENT '请求路径',
    `status`        INT          DEFAULT NULL COMMENT '响应状态',
    `duration_ms`   BIGINT       DEFAULT NULL COMMENT '耗时',
    `remote_addr`   VARCHAR(100) DEFAULT NULL COMMENT '客户端地址',
    `details`       VARCHAR(1000) DEFAULT NULL COMMENT '详情',
    `created_time`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_operation_log_time` (`created_time`),
    KEY `idx_operation_log_operator` (`operator_name`),
    KEY `idx_operation_log_module` (`module_name`),
    KEY `idx_operation_log_status` (`status`)
    ) ENGINE = InnoDB
    DEFAULT CHARSET = utf8mb4
    COLLATE = utf8mb4_unicode_ci COMMENT ='核心操作日志表';

CREATE TABLE IF NOT EXISTS `knowledge_document`
(
    `id`               BIGINT       NOT NULL COMMENT '主键 id',
    `agent_type`       TINYINT      NOT NULL DEFAULT 3 COMMENT '智能体类型：1-医院，2-科室，3-医生',
    `hospital_id`      BIGINT                DEFAULT NULL COMMENT '医院 id',
    `hospital_name`    VARCHAR(255)          DEFAULT NULL COMMENT '医院名称',
    `department_id`    BIGINT                DEFAULT NULL COMMENT '科室 id',
    `department_name`  VARCHAR(255)          DEFAULT NULL COMMENT '科室名称',
    `doctor_id`        BIGINT                DEFAULT NULL COMMENT '医生 id',
    `doctor_name`      VARCHAR(100)          DEFAULT NULL COMMENT '医生姓名',
    `file_name`        VARCHAR(255) NOT NULL COMMENT '原始文件名',
    `source_type`      TINYINT      NOT NULL COMMENT '来源类型，1-手动上传',
    `file_path`        VARCHAR(1024) COMMENT '文件路径',
    `file_size`        BIGINT COMMENT '文件大小，单位字节',
    `content_type`     VARCHAR(128) COMMENT '文件内容类型',
    `chunk_count`      INT                   DEFAULT 0 COMMENT '切分后的块数量',
    `status`           TINYINT      NOT NULL DEFAULT 0 COMMENT '状态，0-待处理，1-成功，2-失败',
    `error_message`    VARCHAR(1000) COMMENT '失败原因',
    `remark`           VARCHAR(1000) DEFAULT NULL COMMENT '备注',
    `created_time`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_time`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_agent_scope` (`agent_type`, `hospital_id`, `department_id`, `doctor_id`),
    KEY `idx_hospital_id` (`hospital_id`),
    KEY `idx_department_id` (`department_id`),
    KEY `idx_doctor_id` (`doctor_id`),
    KEY `idx_source_type` (`source_type`),
    KEY `idx_status` (`status`),
    KEY `idx_file_path` (`file_path`(255))
    ) ENGINE = InnoDB
    DEFAULT CHARSET = utf8mb4
    COLLATE = utf8mb4_unicode_ci COMMENT ='知识库文档表';

CREATE TABLE IF NOT EXISTS `knowledge_vector_conversion`
(
    `id`              BIGINT        NOT NULL COMMENT '主键 id',
    `document_id`     BIGINT        NOT NULL COMMENT 'knowledge_document.id',
    `agent_type`      TINYINT       NOT NULL DEFAULT 3 COMMENT '智能体类型：1-医院，2-科室，3-医生',
    `hospital_id`     BIGINT                 DEFAULT NULL COMMENT '医院 id',
    `hospital_name`   VARCHAR(255)           DEFAULT NULL COMMENT '医院名称',
    `department_id`   BIGINT                 DEFAULT NULL COMMENT '科室 id',
    `department_name` VARCHAR(255)           DEFAULT NULL COMMENT '科室名称',
    `doctor_id`       BIGINT                 DEFAULT NULL COMMENT '医生 id',
    `doctor_name`     VARCHAR(100)           DEFAULT NULL COMMENT '医生姓名',
    `model`           VARCHAR(255)           DEFAULT NULL COMMENT '向量模型',
    `vector_count`    INT                    DEFAULT 0 COMMENT '向量数量',
    `status`          TINYINT       NOT NULL DEFAULT 3 COMMENT '0-待处理，1-成功，2-失败，3-处理中',
    `error_message`   VARCHAR(1000)          DEFAULT NULL COMMENT '失败原因',
    `convert_time`    DATETIME               DEFAULT NULL COMMENT '转换时间',
    `file_name`       VARCHAR(255)            DEFAULT NULL COMMENT '原始文件名',
    PRIMARY KEY (`id`),
    KEY `idx_conversion_document` (`document_id`),
    KEY `idx_conversion_status` (`status`),
    KEY `idx_conversion_agent_scope` (`agent_type`, `hospital_id`, `department_id`, `doctor_id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci COMMENT ='知识库向量转换记录表';
