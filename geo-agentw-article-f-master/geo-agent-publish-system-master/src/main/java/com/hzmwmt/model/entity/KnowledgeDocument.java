package com.hzmwmt.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName(value = "knowledge_document", autoResultMap = true)
public class KnowledgeDocument {

    @TableId(type = IdType.INPUT)
    private Long id;

    /**
     * 1: hospital, 2: department, 3: doctor.
     */
    private Integer agentType;

    private Long hospitalId;

    private String hospitalName;

    private Long departmentId;

    private String departmentName;

    private Long doctorId;

    private String doctorName;

    private String fileName;

    /**
     * 来源类型：1-手动上传
     */
    private Integer sourceType;

    @JsonIgnore
    private String filePath;

    private Long fileSize;

    private String contentType;

    /**
     * 切分后的块数量，默认0
     */
    private Integer chunkCount;

    /**
     * 状态：0-待处理，1-成功，2-失败，3-处理中
     */
    private Integer status;

    private String errorMessage;

    private String remark;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedTime;
}
