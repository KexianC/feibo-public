package com.hzmwmt.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName(value = "knowledge_vector_conversion", autoResultMap = true)
public class KnowledgeVectorConversion {

    @TableId(type = IdType.INPUT)
    private Long id;

    /**
     * 关联的知识库文档 id，对应 knowledge_document.id
     */
    private Long documentId;

    /**
     * 1: hospital, 2: department, 3: doctor.
     */
    private Integer agentType;

    private Long hospitalId;

    private String hospitalName;

    private Long departmentId;

    private String departmentName;

    /**
     * 医生 id，冗余便于查询，与 knowledge_document.doctor_id 一致
     */
    private Long doctorId;

    private String doctorName;

    /**
     * 向量模型名称，例如 text-embedding-3-large
     */
    private String model;

    /**
     * 分块数（向量数量）
     */
    private Integer vectorCount;

    /**
     * 转换状态：0-失败，1-成功，2-进行中
     */
    private Integer status;

    /**
     * 失败原因
     */
    private String errorMessage;

    /**
     * 转换完成时间（成功或失败时记录）
     */
    private LocalDateTime convertTime;

    /**
     * 文件名称
     */
    private String fileName;
}
