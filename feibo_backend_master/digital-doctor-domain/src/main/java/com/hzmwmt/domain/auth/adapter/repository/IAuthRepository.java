package com.hzmwmt.domain.auth.adapter.repository;

import com.hzmwmt.domain.auth.model.aggregate.AuthUserAggregate;

/**
 * @author huihui
 * @version v1.0
 * @Description: TODO
 * @ClassName IAuthRepository
 * @Date: 2025/9/5 10:40
 */
public interface IAuthRepository {
    AuthUserAggregate authUserByPassword(String phoneNumber, String password);

    void registerUser(String phoneNumber);

    AuthUserAggregate queryUserConfig(String phoneNumber);

    AuthUserAggregate queryUserConfig(Long userId);
}
