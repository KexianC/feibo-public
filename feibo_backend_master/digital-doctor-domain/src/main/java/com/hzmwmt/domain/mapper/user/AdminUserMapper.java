package com.hzmwmt.domain.mapper.user;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hzmwmt.domain.entity.user.TUser;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface AdminUserMapper extends BaseMapper<TUser> {

    /**
     * 根据用户ID查询用户昵称
     *
     * @param userId 用户ID
     * @return 用户昵称
     */
    @Select("SELECT nick_name FROM t_user WHERE user_id = #{userId}")
    String selectUserNickNameByUserId(Long userId);
    @Select("SELECT DISTINCT r.name FROM t_user_role_map m JOIN t_role r ON r.role_id=m.role_id WHERE m.user_id=#{userId} ORDER BY r.name")
    java.util.List<String> selectRoleNamesByUserId(Long userId);
}
