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
@TableName(value = "title_info", autoResultMap = true)
public class TitleInfo {

    @TableId(type = IdType.INPUT)
    private Long id;

    private String titleName;

    private Integer titleLevel;

    private String description;

    private Integer enableStatus;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedTime;
}