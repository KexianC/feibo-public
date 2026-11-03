package com.hzmwmt.domain.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class AdminCopywritingOrganizationAdminAssignmentVO {
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;
    @JsonSerialize(using = ToStringSerializer.class)
    private Long accountUserId;
    private String accountNickName;
    @JsonSerialize(using = ToStringSerializer.class)
    private Long adminUserId;
    private String adminNickName;
    private LocalDateTime createTime;
}
