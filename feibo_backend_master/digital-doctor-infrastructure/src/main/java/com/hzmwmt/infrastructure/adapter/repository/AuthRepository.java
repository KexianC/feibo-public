package com.hzmwmt.infrastructure.adapter.repository;

import com.google.common.eventbus.EventBus;
import com.hzmwmt.domain.auth.adapter.repository.IAuthRepository;
import com.hzmwmt.domain.auth.model.aggregate.AuthUserAggregate;
import com.hzmwmt.domain.auth.model.entity.AuthUserEntity;
import com.hzmwmt.domain.auth.model.entity.RoleEntity;
import com.hzmwmt.infrastructure.dao.RoleMapper;
import com.hzmwmt.infrastructure.dao.UserMapper;
import com.hzmwmt.infrastructure.dao.UserRoleMapMapper;
import com.hzmwmt.infrastructure.dao.po.Role;
import com.hzmwmt.infrastructure.dao.po.User;
import com.hzmwmt.infrastructure.dao.po.UserRoleMap;
import com.hzmwmt.types.common.Constants;
import com.hzmwmt.types.enums.GenderEnumVO;
import com.hzmwmt.types.enums.ResponseCode;
import com.hzmwmt.types.exception.AppException;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.RandomStringUtils;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * @author huihui
 * @version v1.0
 * @Description: TODO
 * @ClassName AuthRepository
 * @Date: 2025/9/5 10:40
 */
@Slf4j
@Repository
public class AuthRepository extends AbstractRepository implements IAuthRepository {
    @Resource
    protected EventBus eventBus;
    @Resource
    PasswordEncoder passwordEncoder;
    @Resource
    private UserMapper userMapper;
    @Resource
    private UserRoleMapMapper userRoleMapMapper;
    @Resource
    private RoleMapper roleMapper;

    @Override
    public AuthUserAggregate authUserByPassword(String phoneNumber, String password) {
        User user = userMapper.selectUserByPhoneNumber(phoneNumber);
        if (user == null) {
            throw new AppException(ResponseCode.E0005);
        }

        if (user.getPassword() == null) {
            throw new AppException(ResponseCode.E0003.getCode(), "账号未设置密码");
        }
        if (!passwordEncoder.matches(password, user.getPassword())) {
            throw new AppException(ResponseCode.E0003);
        }
        List<Long> roleIds = userRoleMapMapper.selectUserRoleIdsByUserId(user.getUserId());
        List<Role> roles = roleMapper.selectRoleListByRoleIds(roleIds);
        return AuthUserAggregate.builder()
                .authUserEntity(AuthUserEntity.builder()
                        .userId(user.getUserId())
                        .nickName(user.getNickName())
                        .avatar(user.getAvatar())
                        .phoneNumber(user.getPhoneNumber())
                        .genderEnumVO(GenderEnumVO.fromCode(user.getGender()))
                        .status(user.getStatus())
                        .build())
                .roleEntityList(roles.stream()
                        .map(
                                role -> RoleEntity.builder()
                                        .roleId(role.getRoleId())
                                        .name(role.getName())
                                        .description(role.getDescription())
                                        .build()
                        )
                        .toList()
                )
                .build();
    }

    @Override
    @Transactional(timeout = 500)
    public void registerUser(String phoneNumber) {
        try {
            User user = User.builder()
                    .nickName(Constants.DEFAULT_NICKNAME + RandomStringUtils.randomNumeric(4))
                    .avatar(Constants.DEFAULT_AVATAR)
                    .phoneNumber(phoneNumber)
                    .build();
            userMapper.insert(user);
            UserRoleMap build = UserRoleMap.builder()
                    .userId(user.getUserId())
                    .roleId(Constants.DEFAULT_ROLE_ID)
                    .build();
            //角色绑定
            userRoleMapMapper.insert(
                    build
            );
            // 用户注册成功，无需发送事件
        } catch (Exception e) {
            log.error("注册用户失败: type={}", e.getClass().getSimpleName());
            throw new AppException(ResponseCode.SERVICE_ERROR.getCode(), "注册用户失败");
        }

    }


    @Override
    public AuthUserAggregate queryUserConfig(String phoneNumber) {
        User user = userMapper.selectUserByPhoneNumber(phoneNumber);
        if (user == null) {
            return null;
        }
        List<Long> roleIds = userRoleMapMapper.selectUserRoleIdsByUserId(user.getUserId());
        List<Role> roles = roleMapper.selectRoleListByRoleIds(roleIds);
        return AuthUserAggregate.builder()
                .authUserEntity(AuthUserEntity.builder()
                        .userId(user.getUserId())
                        .nickName(user.getNickName())
                        .avatar(user.getAvatar())
                        .phoneNumber(user.getPhoneNumber())
                        .genderEnumVO(GenderEnumVO.fromCode(user.getGender()))
                        .status(user.getStatus())
                        .build())
                .roleEntityList(roles.stream()
                        .map(
                                role -> RoleEntity.builder()
                                        .roleId(role.getRoleId())
                                        .name(role.getName())
                                        .description(role.getDescription())
                                        .build()
                        )
                        .toList()
                )
                .build();
    }

    @Override
    public AuthUserAggregate queryUserConfig(Long userId) {
        User user = userMapper.selectUserByUserId(userId);
        if (user == null) {
            return null;
        }
        List<Long> roleIds = userRoleMapMapper.selectUserRoleIdsByUserId(user.getUserId());
        List<Role> roles = roleMapper.selectRoleListByRoleIds(roleIds);
        return AuthUserAggregate.builder()
                .authUserEntity(AuthUserEntity.builder()
                        .userId(user.getUserId())
                        .nickName(user.getNickName())
                        .avatar(user.getAvatar())
                        .phoneNumber(user.getPhoneNumber())
                        .genderEnumVO(GenderEnumVO.fromCode(user.getGender()))
                        .status(user.getStatus())
                        .build())
                .roleEntityList(
                        roles.stream()
                                .map(
                                        role -> RoleEntity.builder()
                                                .roleId(role.getRoleId())
                                                .name(role.getName())
                                                .description(role.getDescription())
                                                .build()
                                )
                                .toList()
                )
                .build();
    }


}
