package com.hzmwmt.domain.user.model.entity;

import lombok.Builder;
import lombok.Data;

/**
 * @author huihui
 * @version v1.0
 * @Description: TODO
 * @ClassName updatePasswordCommandEntity
 * @Date: 2025/9/17 12:06
 */
@Builder
@Data
public class UpdatePasswordCommandEntity {
    private String phoneNumber;
    private String password;
    private String code;
}
