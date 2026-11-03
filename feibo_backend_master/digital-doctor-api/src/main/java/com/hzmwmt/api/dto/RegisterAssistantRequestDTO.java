package com.hzmwmt.api.dto;

import jakarta.validation.constraints.*;
import lombok.Data;

/**
 * @author huihui
 * @version v1.0
 * @Description: TODO
 * @ClassName RegisterAssistantRequestDTO
 * @Date: 2025/9/10 15:05
 */
@Data
public class RegisterAssistantRequestDTO {

    @NotBlank(message = "昵称不能为空")
    @Size(min = 2, max = 20, message = "昵称长度必须在2到20个字符之间")
    private String nickName;

    @NotBlank(message = "手机号码不能为空")
    // 在实际项目中，这里通常会使用自定义的 @Phone 注解进行更精确的格式校验
    private String phoneNumber;

    @NotBlank(message = "密码不能为空")
    @Size(min = 6, max = 20, message = "密码长度必须在6到20个字符之间")
    private String password;

    @NotNull(message = "性别不能为空")
    @Min(value = 0, message = "无效的性别代码")
    @Max(value = 2, message = "无效的性别代码")
    private Integer genderCode;
}
