package com.hzmwmt.infrastructure.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hzmwmt.infrastructure.dao.po.User;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * @author huihui
 * @version v1.0
 * @Description: TODO
 * @ClassName UserMapper
 * @Date: 2025/9/3 17:15
 */
public interface UserMapper extends BaseMapper<User> {
    User selectUserByPhoneNumber(String phoneNumber);

    User selectUserByUserId(Long userId);

    List<User> selectUserListByUserIds(@Param("userIds") List<Long> userIds);
}
