package com.hzmwmt.domain.gateway.vedio.dto;

import lombok.Data;

@Data
public class UpdateUserInfoRequestDto {
    Integer id;
    Integer type;
    String userName;
}
