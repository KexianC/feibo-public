package com.hzmwmt.domain.mapper.user;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hzmwmt.domain.entity.user.TAiConversation;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface AdminConversationMapper extends BaseMapper<TAiConversation> {

//    /**
//     * 分页查询会话列表，返回 VO 对象
//     * @param page 分页对象
//     * @param queryWrapper 查询条件
//     * @return 分页结果
//     */
//    // 为 LambdaQueryWrapper 添加 @Param(Constants.WRAPPER) 注解，指定参数名为 "ew"
//    IPage<AdminConversationVO> selectPageVo(
//            Page<AdminConversationVO> page,  // 第一个参数必须是 IPage/Page 类型（分页插件识别）
//            @Param(Constants.WRAPPER) LambdaQueryWrapper<TAiConversation> queryWrapper  // 添加注解
//    )；
    // 涉及到多表操作直接用xml映射vo分页较为复杂
}
