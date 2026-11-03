package com.hzmwmt.domain.user.service;

import com.hzmwmt.domain.user.model.entity.*;

/**
 * @author huihui
 * @version v1.0
 * @Description: TODO
 * @ClassName IUserManagerService
 * @Date: 2025/9/9 10:50
 */
public interface IUserManagerService {
    void sendUpdateShortMessage(String phoneNumber);

    void updatePassword(UpdatePasswordCommandEntity updatePasswordCommandEntity);

    void updateNickName(UpdateNickNameCommandEntity updateNickNameCommandEntity);

    void updateAvatar(UpdateAvatarCommandEntity build);

    void updateSpecialty(UpdateSpecialtyCommandEntity updateSpecialtyCommandEntity);

    /**
     * 根据用户ID获取用户信息
     *
     * @param userId 用户ID
     * @return 用户实体
     */
    UserEntity getUserById(Long userId);

    Boolean queryIsSetSpecialty(Long userId);
}
