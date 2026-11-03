package com.hzmwmt.domain.ai.model.valobj;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * @author huihui
 * @version v1.0
 * @Description: TODO
 * @ClassName LogicCheckTypeVO
 * @Date: 2025/9/21 13:56
 */
@Getter
@AllArgsConstructor
public enum LogicCheckTypeVO {

    SUCCESS("0000", "校验通过"),
    REFUSE("0001", "校验拒绝"),
    ;

    private final String code;
    private final String info;

}
