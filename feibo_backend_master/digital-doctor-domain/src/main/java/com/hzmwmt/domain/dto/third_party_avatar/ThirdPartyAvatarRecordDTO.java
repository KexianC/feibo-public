package com.hzmwmt.domain.dto.third_party_avatar;

import lombok.Data;

/**
 * 第三方API单条记录DTO
 */
@Data
public class ThirdPartyAvatarRecordDTO {
    private String id;
    private String speakerName;
    private String speakerId;
    private String speakerType;
    private Integer speakerWidth;
    private Integer speakerHeight;
    private String speakerUrl;
    private String speakerModelType;
    private String gender;
    private String magicAudioId;
    private Integer headx;
    private Integer heady;
    private Integer headr;
    private String headUrl;
    private Integer headWidth;
    private Integer headHeight;
    private String customType;
    private String created;
    private String status;
    private String lastUpdateTime;
    private String figureUrl;
    private String recommend;
    private String wwid;
    private String sortNum;
    private String phone;
    private String vipType;
    private String published;
    private String product;
    private String gifUrl;
    private String alphaSourceOrigin;
    private String speakerDemoUrl;
}
