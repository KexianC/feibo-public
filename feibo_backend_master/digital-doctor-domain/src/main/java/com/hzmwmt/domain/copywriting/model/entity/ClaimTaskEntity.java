package com.hzmwmt.domain.copywriting.model.entity;

import com.hzmwmt.domain.copywriting.model.valobj.CopywritingDataScope;
import lombok.Builder;
import lombok.Data;

/**
 * @author huihui
 * @version v1.0
 * @Description: TODO
 * @ClassName ClaimTaskEntity
 * @Date: 2025/9/8 17:55
 */
@Data
@Builder
public class ClaimTaskEntity {
    private Long copywritingApprovalFlowId;
    private Long operatorId;
    private CopywritingDataScope dataScope;
}
