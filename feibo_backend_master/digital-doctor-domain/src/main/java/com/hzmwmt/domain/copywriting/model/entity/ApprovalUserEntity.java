package com.hzmwmt.domain.copywriting.model.entity;

import com.hzmwmt.types.enums.GenderEnumVO;
import lombok.Builder;
import lombok.Data;

/**
 * @author huihui
 * @version v1.0
 * @Description: TODO
 * @ClassName ApprovalUserEntity
 * @Date: 2025/9/10 12:55
 */
@Builder
@Data
public class ApprovalUserEntity {
    /**
     * 业务ID (雪花算法生成)
     */
    private Long userId;
    /**
     * 昵称
     */
    private String nickName;

    /**
     * 头像地址
     */
    private String avatar;

    /**
     * 性别 (0:未知, 1:男, 2:女)
     */
    private GenderEnumVO genderEnumVO;
}
