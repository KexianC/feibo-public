package com.hzmwmt.domain.service.function;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.hzmwmt.domain.entity.function.TCopywriting;
import com.hzmwmt.domain.service.function.dto.AdminCopywritingApprovalResponse;
import io.swagger.v3.oas.annotations.Hidden;

@Hidden
public interface AdminCopywritingApprovalService extends IService<TCopywriting> {

    /**
     * 分页查询文案审批列表
     *
     * @param pageNum       页码
     * @param pageSize      每页条数
     * @param submitterName 提交人姓名（搜索条件）
     * @param status        审核状态（筛选条件）
     * @return 分页结果
     */
    IPage<AdminCopywritingApprovalResponse> pageCopywritingApprovals(Integer pageNum, Integer pageSize, String submitterName, Integer status, String title);

    /**
     * 根据文案ID查询文案详情
     *
     * @param copywritingId 文案ID
     * @return 文案详情
     */
    TCopywriting getCopywritingDetail(Long copywritingId);

    /**
     * 获取用户姓名
     *
     * @param userId 用户ID
     * @return 用户姓名
     */
    String getUserName(Long userId);

    /**
     * 获取用户身份（角色）
     *
     * @param userId 用户ID
     * @return 用户身份
     */
    String getUserRole(Long userId);

    /**
     * 获取审核状态文本
     *
     * @param status 状态码
     * @return 状态文本
     */
    String getStatusText(Integer status);

    /**
     * 获取审批评定信息
     *
     * @param copywritingId 文案ID
     * @return 评定信息
     */
    String getEvaluation(Long copywritingId);
}