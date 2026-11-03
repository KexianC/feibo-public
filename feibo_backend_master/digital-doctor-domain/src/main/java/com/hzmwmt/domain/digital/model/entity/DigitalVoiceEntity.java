package com.hzmwmt.domain.digital.model.entity;


import com.hzmwmt.domain.digital.model.valobj.VoiceGenderEnumVO;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @author huihui
 * @version v1.0
 * @Description: TODO
 * @ClassName VoiceVO
 * @Date: 2025/8/18 0:19
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class DigitalVoiceEntity {
    /**
     * 业务ID
     */
    private Long voiceId;

    /**
     * 配音名称 (用于UI展示)
     */
    private String name;

    /**
     * 配音风格参数
     */
    private String styleName;


    private VoiceGenderEnumVO voiceGenderEnumVO;


    public String getFilteredName() {
        // 首先判断name是否为null或空，避免出现NullPointerException
        if (this.name == null || this.name.isEmpty()) {
            return this.name;
        }
        // 执行替换操作
        return this.name.replace("魔", "音");
    }


}
