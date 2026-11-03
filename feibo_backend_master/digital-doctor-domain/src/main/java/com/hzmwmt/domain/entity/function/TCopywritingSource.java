package com.hzmwmt.domain.entity.function;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@TableName("t_copywriting_source")
@Schema(name = "TCopywritingSource", description = "文案来源实体")
public class TCopywritingSource implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "主键ID")
    @TableId(type = IdType.INPUT)
    private Long id;

    @Schema(description = "文案来源ID")
    private Long copywritingSourceId;

    @Schema(description = "来源名称")
    private String name;

    @Schema(description = "状态（1-启用，0-禁用）")
    private Boolean status;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;

    @Schema(description = "更新时间")
    private LocalDateTime updateTime;
}