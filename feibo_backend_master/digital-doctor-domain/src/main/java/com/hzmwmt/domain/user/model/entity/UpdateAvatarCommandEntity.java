package com.hzmwmt.domain.user.model.entity;

import lombok.Builder;
import lombok.Data;
import org.springframework.web.multipart.MultipartFile;

/**
 * @author huihui
 * @version v1.0
 * @Description: TODO
 * @ClassName UpdateAvatarCommandEntity
 * @Date: 2025/9/17 12:11
 */
@Builder
@Data
public class UpdateAvatarCommandEntity {
    private Long userId;
    private String url;
    private MultipartFile avatar;
}
