package com.hzmwmt.api.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Builder;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * @author huihui
 * @version v1.0
 * @Description: TODO
 * @ClassName AIModelConfigIndexResponseDTO
 * @Date: 2025/9/23 11:02
 */
@Builder
@Data
public class AIModelConfigIndexResponseDTO implements Serializable {
    List<AIModelConfigIndexResponseDTO> childrenList;
    /**
     * 业务ID
     */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long aiModelConfigId;
    /**
     * 用于发起请求的唯一名称
     */
    private String name;
    /**
     * 关于此配置的描述
     */
    private String description;
    /**
     * 模型类型 0: 通用模型 1: 高级模型
     */
    private Boolean type;
    private String image;
}
