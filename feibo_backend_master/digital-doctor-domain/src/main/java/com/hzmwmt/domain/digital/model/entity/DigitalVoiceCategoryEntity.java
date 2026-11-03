package com.hzmwmt.domain.digital.model.entity;

import com.hzmwmt.domain.digital.model.valobj.DigitalVoiceCategoryTypeEnumVO;
import lombok.Builder;
import lombok.Data;

/**
 * @author huihui
 * @version v1.0
 * @Description: TODO
 * @ClassName DigitalVoiceCategoryEntity
 * @Date: 2025/8/25 17:44
 */
@Builder
@Data
public class DigitalVoiceCategoryEntity {

    /**
     * 分类业务ID (雪花算法)
     */
    private Long digitalVoiceCategoryId;

    /**
     * 名称
     */
    private String name;

    /**
     * 类型
     */
    private DigitalVoiceCategoryTypeEnumVO digitalVoiceCategoryTypeEnumVO;


}
