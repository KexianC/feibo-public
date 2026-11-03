package com.hzmwmt.domain.service.user;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.hzmwmt.domain.entity.user.TAiConversation;
import com.hzmwmt.domain.vo.AdminConversationDetailVO;
import com.hzmwmt.domain.vo.AdminConversationVO;

import java.util.List;

public interface AdminConversationService extends IService<TAiConversation> {
    /**
     * 分页查询对话列表
     *
     * @param pageNum  页码
     * @param pageSize 每页条数
     * @param username 用户名（搜索参数，可为空）
     * @param content  问题内容（搜索参数，可为空）
     * @param status   状态（搜索参数，可为空）
     * @param model    模型（搜索参数，可为空）
     * @return 分页的对话列表
     */
    IPage<AdminConversationVO> getConversationList(Integer pageNum, Integer pageSize, String username, String content, String status, Long model);

    /**
     * 查看对话详情
     *
     * @param conversationId 对话ID
     * @return 对话详情
     */
    AdminConversationDetailVO getConversationDetail(String conversationId);

    /**
     * 删除对话
     *
     * @param conversationId 对话ID
     * @return 删除结果（true 表示成功，false 表示失败）
     */
    Boolean deleteConversation(String conversationId);

    /**
     * 通过模型id获取对话id 通过模型id查询
     *
     * @param moderConfigId
     * @return
     */
    List<Long> getConversationIdsByModerConfigId(Long moderConfigId);

//    // 分页查询会话列表并构建VO
//    IPage<AdminConversationVO> getConversationList(Long pageNum, Long pageSize);
}
