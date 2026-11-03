package com.hzmwmt.geo.model.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName(value = "geo_dialogue_probe", autoResultMap = true)
public class GeoDialogueProbe {

    @TableId(type = IdType.INPUT)
    private Long id;

    private Long queryId;
    private String engine;
    private String queryText;
    private String probeMode;
    private Integer requestedRounds;
    private Integer completedRounds;
    private Integer status;
    private Integer mentioned;
    private Integer cited;
    private Integer visibilityScore;
    private Integer officialSourceCount;
    private String baselineAnswer;
    private String baselineSources;
    private String dialogueJson;
    private String gapType;
    private String gapSummary;
    private String sourceRequirements;
    private String recommendedActions;
    private String errorMessage;
    private LocalDateTime startedTime;
    private LocalDateTime finishedTime;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedTime;
}
