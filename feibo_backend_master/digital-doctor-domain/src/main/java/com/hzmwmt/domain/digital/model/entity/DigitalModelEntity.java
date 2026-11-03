package com.hzmwmt.domain.digital.model.entity;

import com.hzmwmt.domain.digital.model.valobj.DigitalModelGenderEnumVO;
import lombok.Builder;
import lombok.Data;

/**
 * @author huihui
 * @version v1.0
 * @Description: TODO
 * @ClassName DigitalModelEntity
 * @Date: 2025/9/12 23:54
 */
@Builder
@Data
public class DigitalModelEntity {
    /**
     * 业务ID
     */
    private Long digitalModelId;

    /**
     * 用户ID
     */
    private Long userId;
    /**
     * 声音ID
     */
    private Long voiceId;

    /**
     * 形象ID
     */
    private Long imageId;

    /**
     * 模型名称
     */
    private String name;

    /**
     * 头像
     */
    private String avatar;

    /**
     * 性别: 0-男, 1-女
     */
    private DigitalModelGenderEnumVO digitalModelGenderEnumVO;

}
