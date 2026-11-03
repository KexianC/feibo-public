package com.hzmwmt.domain.digital.service.File.factory;


import com.hzmwmt.domain.digital.service.File.strategy.AudioUploadStrategy;
import com.hzmwmt.domain.digital.service.File.strategy.IUploadStrategy;
import com.hzmwmt.domain.digital.service.File.strategy.ImageUploadStrategy;
import com.hzmwmt.domain.digital.service.File.strategy.VideoUploadStrategy;
import com.hzmwmt.types.enums.ResponseCode;
import com.hzmwmt.types.exception.AppException;
import jakarta.annotation.PostConstruct;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.EnumMap;
import java.util.Map;

/**
 * @author huihui
 * @version v1.0
 * @Description: TODO
 * @ClassName UploadStrategyFactory
 * @Date: 2025/8/22 11:35
 */
@Service
public class UploadStrategyFactory {

    private final Map<UploadTypeEnumVO, IUploadStrategy> strategyMap = new EnumMap<>(UploadTypeEnumVO.class);
    @Autowired
    private ImageUploadStrategy imageUploadStrategy;
    @Autowired
    private AudioUploadStrategy audioUploadStrategy;
    @Autowired
    private VideoUploadStrategy videoUploadStrategy;

    @PostConstruct
    public void init() {
        strategyMap.put(UploadTypeEnumVO.BACKGROUND_IMAGE, imageUploadStrategy);
        strategyMap.put(UploadTypeEnumVO.ILLUSTRATION, imageUploadStrategy);
        strategyMap.put(UploadTypeEnumVO.RECORDING, audioUploadStrategy);
        strategyMap.put(UploadTypeEnumVO.MUSIC, audioUploadStrategy);
        strategyMap.put(UploadTypeEnumVO.VIDEO, videoUploadStrategy);
    }

    public IUploadStrategy getStrategy(UploadTypeEnumVO type) {
        IUploadStrategy strategy = strategyMap.get(type);
        if (strategy == null) {
            throw new IllegalArgumentException("不支持的上传类型: " + type);
        }
        return strategy;
    }

    @Getter
    @AllArgsConstructor
    public static enum UploadTypeEnumVO {

        VIDEO(0, "视频"),
        RECORDING(1, "录音"),
        MUSIC(2, "音乐"),
        BACKGROUND_IMAGE(3, "背景图片"),
        ILLUSTRATION(4, "插图");

        private final Integer code;
        private final String desc;


        /**
         * 根据 code 获取对应枚举，找不到返回 null
         */
        public static UploadTypeEnumVO getByCode(Integer code) {
            if (code == null) return null;
            for (UploadTypeEnumVO value : values()) {
                if (value.code.equals(code)) {
                    return value;
                }
            }
            throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), "不支持的上传类型: ");
        }
    }

}
