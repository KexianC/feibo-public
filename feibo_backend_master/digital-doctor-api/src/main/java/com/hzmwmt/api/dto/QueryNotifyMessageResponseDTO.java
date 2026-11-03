package com.hzmwmt.api.dto;

import lombok.Builder;
import lombok.Data;

import java.io.Serializable;

/**
 * @author huihui
 * @version v1.0
 * @Description: TODO
 * @ClassName QueryNotifyMessageResponseDTO
 * @Date: 2025/9/12 9:25
 */
@Data
@Builder
public class QueryNotifyMessageResponseDTO implements Serializable {
    private String title;
    private String content;
    private String sendTime;

}
