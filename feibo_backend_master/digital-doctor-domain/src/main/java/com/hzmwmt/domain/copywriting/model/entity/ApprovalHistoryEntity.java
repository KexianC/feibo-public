package com.hzmwmt.domain.copywriting.model.entity;

import com.hzmwmt.domain.copywriting.model.valobj.ApprovalStatusEnumVO;
import com.hzmwmt.domain.copywriting.model.valobj.OperationTypeEnumVO;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

/**
 * @author huihui
 * @version v1.0
 * @Description: 审批历史实体 - 充血模型
 * @ClassName ApprovalHistoryEntity
 * @Date: 2025/1/20
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ApprovalHistoryEntity {

    /**
     * 自增主键ID（数据库自增，插入时不需要设置）
     */
    private Long id;

    /**
     * 审批历史业务ID（MyBatis-Plus自动生成，插入时不需要设置）
     */
    private Long copywritingApprovalHistoryId;

    /**
     * 文案业务ID
     */
    private Long copywritingId;

    /**
     * 操作人用户业务ID
     */
    private Long operatorId;

    /**
     * 操作类型
     */
    private OperationTypeEnumVO operation;

    /**
     * 修改意见或备注
     */
    private String comment;

    /**
     * 操作前状态
     */
    private ApprovalStatusEnumVO fromStatus;

    /**
     * 操作后状态
     */
    private ApprovalStatusEnumVO toStatus;

    /**
     * 操作时间
     */
    private Date operationTime;
}