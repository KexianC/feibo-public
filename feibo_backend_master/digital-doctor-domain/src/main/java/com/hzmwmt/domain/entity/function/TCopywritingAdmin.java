package com.hzmwmt.domain.entity.function;

import com.baomidou.mybatisplus.annotation.*;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@TableName("t_copywriting_admin")
@Schema(name = "TCopywritingAdmin", description = "文案管理实体")
public class TCopywritingAdmin implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "主键ID", example = "68")
    @TableId(type = IdType.AUTO)
    private Long id;

    @Schema(description = "文案标题", example = "突发心绞痛是首选硝酸甘油还是速效救心丸 →")
    private String title;

    @Schema(description = "文案分类ID", example = "2")
    private Long copywritingCategoryId;

    @Schema(description = "排序值", example = "0")
    private Integer sort;

    @Schema(description = "创建时间", example = "2024-03-18T14:27:11")
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @Schema(description = "更新时间", example = "2024-03-18T14:27:11")
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @Schema(description = "是否显示（1：是；0：否）", example = "1")
    private Integer isShow;

    @Schema(description = "逻辑删除标识（1：已删除；0：正常）", example = "0")
    @TableLogic
    private Integer isDeleted;
}