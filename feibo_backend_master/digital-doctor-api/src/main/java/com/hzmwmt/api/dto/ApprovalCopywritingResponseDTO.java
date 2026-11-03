package com.hzmwmt.api.dto;

import lombok.Builder;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * @author huihui
 * @version v1.0
 * @Description: TODO
 * @ClassName ApprovalCopywritingResponseDTO
 * @Date: 2025/9/10 14:09
 */
@Builder
@Data
public class ApprovalCopywritingResponseDTO {
    private CopywritingDTO copywriting;
    private String categoryName;
    private String sourceName;
    private CopywritingApprovalFlowDTO approvalFlow;
    private ApprovalUserDTO approvalUser;
    private ApprovalUserDTO submitterUser;
    private Boolean expertReviewer;
    private Boolean organizationReviewer;

    /**
     * 文案信息
     */
    @Data
    @Builder
    public static class CopywritingDTO implements Serializable {
        private Long copywritingId;
        private String title;
        private String summary;
        private String content;
        private String status;
        private Date createTime;
    }


    /**
     * 文案审批流信息
     */
    @Data
    @Builder
    public static class CopywritingApprovalFlowDTO implements Serializable {
        private Long copywritingApprovalFlowId;
        private String currentStatus;
        private Integer currentStatusCode;
        private Date createTime;
    }

    /**
     * 审批人信息
     */
    @Data
    @Builder
    public static class ApprovalUserDTO implements Serializable {
        private Long userId;
        private String nickName;
        private String avatar;
        private String gender;
    }
}
