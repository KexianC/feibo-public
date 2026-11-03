package com.hzmwmt.domain.service.function.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hzmwmt.domain.entity.user.TUser;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hzmwmt.domain.entity.function.TCopywriting;
import com.hzmwmt.domain.entity.function.TCopywritingApprovalFlow;
import com.hzmwmt.domain.entity.function.TCopywritingApprovalHistory;
import com.hzmwmt.domain.mapper.function.AdminCopywritingApprovalFlowMapper;
import com.hzmwmt.domain.mapper.function.AdminCopywritingApprovalHistoryMapper;
import com.hzmwmt.domain.mapper.function.AdminCopywritingApprovalMapper;
import com.hzmwmt.domain.mapper.user.AdminUserMapper;
import com.hzmwmt.domain.service.function.AdminCopywritingApprovalService;
import com.hzmwmt.domain.service.function.dto.AdminCopywritingApprovalResponse;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class AdminCopywritingApprovalServiceImpl extends ServiceImpl<AdminCopywritingApprovalMapper, TCopywriting> implements AdminCopywritingApprovalService {

    @Resource
    private AdminCopywritingApprovalMapper adminCopywritingApprovalMapper;

    @Resource
    private AdminCopywritingApprovalFlowMapper adminCopywritingApprovalFlowMapper;

    @Resource
    private AdminCopywritingApprovalHistoryMapper adminCopywritingApprovalHistoryMapper;

    @Resource
    private AdminUserMapper adminUserMapper;

    @Override
    public IPage<AdminCopywritingApprovalResponse> pageCopywritingApprovals(Integer pageNum, Integer pageSize, String submitterName, Integer status, String title) {
        // 先查询文案数据
        Page<TCopywriting> page = new Page<>(pageNum, pageSize);
        QueryWrapper<TCopywriting> wrapper = new QueryWrapper<>();
        if (title != null && !title.isBlank()) wrapper.like("title", title.trim());
        if (submitterName != null && !submitterName.isBlank()) {
            List<Long> users = adminUserMapper.selectList(new LambdaQueryWrapper<TUser>().like(TUser::getNickName, submitterName.trim()))
                .stream().map(TUser::getUserId).toList();
            if (users.isEmpty()) return new Page<>(pageNum, pageSize, 0);
            wrapper.in("user_id", users);
        }

        // 如果传入了status参数，需要关联审批流程表进行过滤
        if (status != null) {
            // 这里需要使用子查询或者关联查询来根据状态过滤
            // 由于QueryWrapper的限制，这里采用子查询方式
            wrapper.inSql("copywriting_id", "SELECT copywriting_id FROM t_copywriting_approval_flow WHERE current_status = " + status);
        }

        wrapper.orderByDesc("create_time");

        IPage<TCopywriting> copywritingPage = baseMapper.selectPage(page, wrapper);
        List<TCopywriting> copywritingList = copywritingPage.getRecords();

        // 如果没有数据，直接返回空结果
        if (copywritingList.isEmpty()) {
            return new Page<>(pageNum, pageSize, 0);
        }

        // 获取所有文案ID
        List<Long> copywritingIds = copywritingList.stream()
                .map(TCopywriting::getCopywritingId)
                .collect(Collectors.toList());

        // 查询审批流程数据
        QueryWrapper<TCopywritingApprovalFlow> flowWrapper = new QueryWrapper<>();
        flowWrapper.in("copywriting_id", copywritingIds);
        List<TCopywritingApprovalFlow> flowList = adminCopywritingApprovalFlowMapper.selectList(flowWrapper);

        // 构建文案ID到审批流程的映射
        Map<Long, TCopywritingApprovalFlow> flowMap = flowList.stream()
                .collect(Collectors.toMap(TCopywritingApprovalFlow::getCopywritingId, flow -> flow));

        // 转换为响应DTO
        List<AdminCopywritingApprovalResponse> responseList = new ArrayList<>();
        for (TCopywriting copywriting : copywritingList) {
            AdminCopywritingApprovalResponse response = new AdminCopywritingApprovalResponse();
            response.setId(copywriting.getId());
            response.setTitle(copywriting.getTitle());
            response.setTime(copywriting.getCreateTime());
            response.setCopywritingId(copywriting.getCopywritingId());

            // 设置用户信息
            response.setSubmitterName(getUserName(copywriting.getUserId()));
            response.setSubmitterIdentity(getUserRole(copywriting.getUserId()));

            // 设置状态和流程ID
            TCopywritingApprovalFlow flow = flowMap.get(copywriting.getCopywritingId());
            if (flow != null) {
                Integer currentStatus = flow.getCurrentStatus();
                response.setStatus(getStatusText(currentStatus));
                response.setFlowId(flow.getCopywritingApprovalFlowId());
            } else {
                response.setStatus(getStatusText(null));
            }

            // 设置评定信息
            response.setEvaluation(getEvaluation(copywriting.getCopywritingId()));

            responseList.add(response);
        }

        // 构建分页结果
        IPage<AdminCopywritingApprovalResponse> resultPage = new Page<>();
        resultPage.setRecords(responseList);
        resultPage.setTotal(copywritingPage.getTotal());
        resultPage.setSize(copywritingPage.getSize());
        resultPage.setCurrent(copywritingPage.getCurrent());

        return resultPage;
    }

    @Override
    public TCopywriting getCopywritingDetail(Long copywritingId) {
        QueryWrapper<TCopywriting> wrapper = new QueryWrapper<>();
        wrapper.eq("copywriting_id", copywritingId);
        return baseMapper.selectOne(wrapper);
    }

    @Override
    public String getUserName(Long userId) {
        if (userId != null) {
            String nickName = adminUserMapper.selectUserNickNameByUserId(userId);
            if (nickName != null && !nickName.isEmpty()) {
                return nickName;
            }
        }
        return "未知用户";
    }

    @Override
    public String getUserRole(Long userId) {
        if (userId == null) return "未分配角色";
        List<String> roles = adminUserMapper.selectRoleNamesByUserId(userId);
        if (roles == null || roles.isEmpty()) return "未分配角色";
        Map<String,String> labels = Map.of("DOCTOR","医生","EXPERT","专家","ORG_ADMIN","机构管理员","ADMIN","管理员","SUPER_ADMIN","超级管理员");
        return roles.stream().map(role -> labels.getOrDefault(role, role)).distinct().collect(Collectors.joining("、"));
    }

    @Override
    public String getStatusText(Integer status) {
        if (status == null) return "待提交";
        return switch (status) {
            case 0 -> "作者待确认";
            case 1 -> "专家组待专业审核";
            case 2 -> "AI自动审核中";
            case 3 -> "所属医院/机构待终审";
            case 4 -> "审批完成";
            case 5 -> "审批驳回";
            default -> "未知状态";
        };
    }

    @Override
    public String getEvaluation(Long copywritingId) {
        // 查询审批流程
        QueryWrapper<TCopywritingApprovalFlow> flowWrapper = new QueryWrapper<>();
        flowWrapper.eq("copywriting_id", copywritingId);
        TCopywritingApprovalFlow flow = adminCopywritingApprovalFlowMapper.selectOne(flowWrapper);

        if (flow == null) {
            return "";
        }

        // 查询最近的审批历史
        QueryWrapper<TCopywritingApprovalHistory> historyWrapper = new QueryWrapper<>();
        historyWrapper.eq("copywriting_approval_flow_id", flow.getCopywritingApprovalFlowId());
        historyWrapper.orderByDesc("create_time");
        historyWrapper.last("LIMIT 1");
        TCopywritingApprovalHistory history = adminCopywritingApprovalHistoryMapper.selectOne(historyWrapper);

        if (history != null && history.getComment() != null) {
            return history.getComment();
        }

        return "";
    }
}
