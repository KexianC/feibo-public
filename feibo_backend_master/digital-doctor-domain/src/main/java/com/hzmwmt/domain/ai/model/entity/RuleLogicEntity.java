package com.hzmwmt.domain.ai.model.entity;

import com.hzmwmt.domain.ai.model.valobj.LogicCheckTypeVO;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @author huihui
 * @version v1.0
 * @Description: TODO
 * @ClassName RuleLogicEntity
 * @Date: 2025/9/21 13:56
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RuleLogicEntity<T> {

    private LogicCheckTypeVO type;
    private String info;
    private T data;

}
