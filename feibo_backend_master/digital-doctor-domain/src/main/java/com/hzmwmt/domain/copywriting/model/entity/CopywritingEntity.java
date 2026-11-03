package com.hzmwmt.domain.copywriting.model.entity;

import com.hzmwmt.domain.copywriting.model.valobj.CopywritingStatusEnumVO;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

/**
 * @author huihui
 * @version v1.0
 * @Description: 文案实体 - 充血模型
 * @ClassName CopywritingEntity
 * @Date: 2025/1/20
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class CopywritingEntity {

    /**
     * 文案业务ID
     */
    private Long copywritingId;

    /**
     * 文案标题
     */
    private String title;

    /**
     * 文案摘要
     */
    private String summary;

    /**
     * 文案内容
     */
    private String content;

    /**
     * 文案状态
     */
    private CopywritingStatusEnumVO status;

    /**
     * 创建时间
     */
    private Date createTime;


}