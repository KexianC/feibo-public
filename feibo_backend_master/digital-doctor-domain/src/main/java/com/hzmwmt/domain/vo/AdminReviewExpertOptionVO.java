package com.hzmwmt.domain.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

@Data
public class AdminReviewExpertOptionVO {
    @JsonSerialize(using = ToStringSerializer.class)
    private Long expertUserId;
    private String expertNickName;
    private String phoneNumber;
}
