package com.hzmwmt.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;

/**
 * 数字人模型VO类
 *
 * @author system
 */
@Data
@Schema(name = "DigitalModelVO", description = "数字人模型视图对象")
public class DigitalModelVO implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "ID", example = "1")
    private String id;

    @Schema(description = "数字人模型名称", example = "都健")
    private String modelName;

    @Schema(description = "数字人模型唯一ID", example = "Dujian_2437_no_alpha_512")
    private String modelId;

    @Schema(description = "模型图片OSS链接")
    private String modelUrl;

    @Schema(description = "医院")
    private String hospital;
}