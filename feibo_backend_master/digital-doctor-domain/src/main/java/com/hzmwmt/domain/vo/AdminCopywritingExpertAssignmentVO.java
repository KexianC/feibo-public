package com.hzmwmt.domain.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class AdminCopywritingExpertAssignmentVO {
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;
    @JsonSerialize(using = ToStringSerializer.class)
    private Long accountUserId;
    private String accountNickName;
    @JsonSerialize(using = ToStringSerializer.class)
    private Long expertUserId;
    private String expertNickName;
    private LocalDateTime createTime;
}
