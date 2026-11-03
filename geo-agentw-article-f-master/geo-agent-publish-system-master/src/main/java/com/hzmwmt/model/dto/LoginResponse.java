package com.hzmwmt.model.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class LoginResponse {

    private boolean authenticated;

    private String csrfToken;

    private Long userId;

    private String username;

    private String displayName;

    private String role;
}
