package com.hzmwmt.domain.trade.model.valobj;

import lombok.Builder;
import lombok.Data;

/**
 * @author huihui
 * @version v1.0
 * @Description: TODO
 * @ClassName AIFeatureTemplateVO
 * @Date: 2025/8/27 14:08
 */
@Builder
@Data
public class AIFeatureTemplateVO {
    /**
     * 权益模板业务代码, 程序识别用
     */
    private String templateCode;

    /**
     * 权益模板名称
     */
    private String name;

    /**
     * 权益模板详细描述
     */
    private String description;
}
