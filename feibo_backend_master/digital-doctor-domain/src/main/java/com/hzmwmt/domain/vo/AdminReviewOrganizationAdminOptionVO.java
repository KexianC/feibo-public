package com.hzmwmt.domain.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

@Data
public class AdminReviewOrganizationAdminOptionVO {
    @JsonSerialize(using = ToStringSerializer.class)
    private Long adminUserId;
    private String adminNickName;
    private String phoneNumber;
}
