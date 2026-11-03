package com.hzmwmt.domain.gateway.vedio.dto;

import lombok.Data;
import org.springframework.web.multipart.MultipartFile;

@Data
public class UploadCookieRequestDto {
    MultipartFile file;
    Integer id;
    String  platform;
}
