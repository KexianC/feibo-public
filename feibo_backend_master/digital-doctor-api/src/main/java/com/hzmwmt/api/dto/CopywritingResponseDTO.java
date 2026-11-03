package com.hzmwmt.api.dto;

import lombok.Builder;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * @author huihui
 * @version v1.0
 * @Description: TODO
 * @ClassName CopywritingResponseDTO
 * @Date: 2025/9/10 14:10
 */
@Builder
@Data
public class CopywritingResponseDTO implements Serializable {
    private CopywritingDTO copywriting;
    private String categoryName;
    private String sourceName;

    /**
     * 文案信息
     */
    @Data
    @Builder
    public static class CopywritingDTO implements Serializable {
        private Long copywritingId;
        private String title;
        private String summary;
        private String status; // 枚举转为字符串描述
        private Date createTime;
    }

}
