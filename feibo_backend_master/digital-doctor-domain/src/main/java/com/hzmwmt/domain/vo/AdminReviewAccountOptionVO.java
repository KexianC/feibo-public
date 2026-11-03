package com.hzmwmt.domain.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class AdminReviewAccountOptionVO {
    @JsonSerialize(using = ToStringSerializer.class)
    private Long accountUserId;
    private String nickName;
    private String phoneNumber;
    private Boolean status;
    private LocalDateTime createTime;
}
