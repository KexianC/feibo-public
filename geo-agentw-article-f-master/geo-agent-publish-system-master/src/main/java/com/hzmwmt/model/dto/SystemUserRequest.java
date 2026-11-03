package com.hzmwmt.model.dto;

import lombok.Data;

@Data
public class SystemUserRequest {

    private Long id;

    private String username;

    private String displayName;

    private String role;

    private String password;

    private Integer enableStatus;
}
