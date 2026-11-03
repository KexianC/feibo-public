package com.hzmwmt.domain.service.function.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hzmwmt.domain.entity.function.TCopywriting;
import com.hzmwmt.domain.entity.function.TCopywritingApprovalFlow;
import com.hzmwmt.domain.entity.function.TCopywritingApprovalHistory;
import com.hzmwmt.domain.entity.function.TCopywritingCategory;
import com.hzmwmt.domain.entity.user.TUser;
import com.hzmwmt.domain.mapper.function.AdminCopywritingApprovalFlowMapper;
import com.hzmwmt.domain.mapper.function.AdminCopywritingApprovalHistoryMapper;
import com.hzmwmt.domain.mapper.function.AdminCopywritingMapper;
import com.hzmwmt.domain.mapper.function.TCopywritingCategoryMapper;
import com.hzmwmt.domain.mapper.user.AdminUserMapper;
import com.hzmwmt.domain.service.function.AdminCopywritingService;
import com.hzmwmt.domain.vo.AdminCopywritingApprovalHistoryVO;
import com.hzmwmt.domain.vo.AdminCopywritingVO;
import com.hzmwmt.types.enums.ResponseCode;
import com.hzmwmt.types.exception.AppException;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;


@Slf4j
@Service
public class AdminCopywritingServiceImpl extends ServiceImpl<AdminCopywritingMapper, TCopywriting> implements AdminCopywritingService {

    @Autowired
    private AdminCopywritingApprovalFlowMapper adminCopywritingApprovalFlowMapper;

    @Autowired
    private AdminCopywritingApprovalHistoryMapper adminCopywritingApprovalHistoryMapper;

    @Resource
    private AdminUserMapper adminUserMapper;

    @Resource
    private TCopywritingCategoryMapper tCopywritingCategoryMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean createCopywritingSafely(TCopywriting copywriting) {
        if (copywriting == null
                || copywriting.getUserId() == null || copywriting.getUserId() <= 0
                || copywriting.getCopywritingCategoryId() == null || copywriting.getCopywritingCategoryId() <= 0
                || copywriting.getCopywritingSourceId() == null || copywriting.getCopywritingSourceId() <= 0
                || copywriting.getTitle() == null || copywriting.getTitle().isBlank()
                || copywriting.getContent() == null || copywriting.getContent().isBlank()
                || copywriting.getSummary() == null || copywriting.getSummary().isBlank()) {
            throw new AppException(ResponseCode.ILLEGAL_PARAMETER);
        }
        copywriting.setId(null);
        copywriting.setCopywritingId(IdWorker.getId());
        copywriting.setStatus(0);
        copywriting.setCreateTime(null);
        copywriting.setUpdateTime(null);
        return super.save(copywriting);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean updateCopywritingSafely(TCopywriting copywriting) {
        if (copywriting == null || copywriting.getId() == null) {
            throw new AppException(ResponseCode.ILLEGAL_PARAMETER);
        }
        TCopywriting existing = super.getById(copywriting.getId());
        if (existing == null) {
            return false;
        }
        rejectMutationWhenApprovalFlowExists(existing.getCopywritingId());
        // Ownership, workflow status, and creation metadata are never accepted from
        // this broad admin DTO, even for a record that has not entered approval.
        copywriting.setCopywritingId(null);
        copywriting.setUserId(null);
        copywriting.setStatus(null);
        copywriting.setCreateTime(null);
        copywriting.setUpdateTime(null);
        return super.updateById(copywriting);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean removeCopywritingSafely(Long id) {
        if (id == null) {
            throw new AppException(ResponseCode.ILLEGAL_PARAMETER);
        }
        TCopywriting existing = super.getById(id);
        if (existing == null) {
            return false;
        }
        rejectMutationWhenApprovalFlowExists(existing.getCopywritingId());
        return super.removeById(id);
    }

    private void rejectMutationWhenApprovalFlowExists(Long copywritingId) {
        if (copywritingId == null) {
            return;
        }
        Long flowCount = adminCopywritingApprovalFlowMapper.selectCount(
                new LambdaQueryWrapper<TCopywritingApprovalFlow>()
                        .eq(TCopywritingApprovalFlow::getCopywritingId, copywritingId));
        if (flowCount != null && flowCount > 0) {
            throw new AppException(
                    ResponseCode.ILLEGAL_PARAMETER.getCode(),
                    "Copywriting with an approval record cannot be edited or deleted; create a new submission instead");
        }
    }

    /**
     * 将TCopywriting实体转换为AdminCopywritingVO
     */
    private AdminCopywritingVO convertToVO(TCopywriting copywriting) {
        if (copywriting == null) {
            log.debug("copywriting为null，直接返回null");
            return null;
        }

        AdminCopywritingVO vo = new AdminCopywritingVO();
        BeanUtils.copyProperties(copywriting, vo);
        log.debug("基础属性复制完成，copywritingId: {}, userId: {}, categoryId: {}",
                copywriting.getId(), copywriting.getUserId(), copywriting.getCopywritingCategoryId());

        // 设置用户姓名
        log.debug("开始设置用户姓名，userId: {}", copywriting.getUserId());
        if (copywriting.getUserId() != null) {
            log.debug("userId不为null，查询用户信息");
            TUser user = adminUserMapper.selectById(copywriting.getUserId());
            if (user != null) {
                String nickName = user.getNickName() != null ? user.getNickName() : "未知用户";
                vo.setUserName(nickName);
                log.debug("用户查询成功，用户ID: {}", copywriting.getUserId());
            } else {
                vo.setUserName("未知用户");
                log.debug("用户不存在，设置为'未知用户', userId: {}", copywriting.getUserId());
            }
        } else {
            vo.setUserName("未知用户");
            log.debug("userId为null，设置为'未知用户'");
        }

        // 设置分类名称
        log.debug("开始设置分类名称，categoryId: {}", copywriting.getCopywritingCategoryId());
        if (copywriting.getCopywritingCategoryId() != null) {
            log.debug("categoryId不为null，查询分类信息");
            LambdaQueryWrapper<TCopywritingCategory> categoryWrapper = new LambdaQueryWrapper<>();
            categoryWrapper.eq(TCopywritingCategory::getCopywritingCategoryId, copywriting.getCopywritingCategoryId());
            TCopywritingCategory category = tCopywritingCategoryMapper.selectOne(categoryWrapper);
            if (category != null && !category.getStatus()) { // 只显示状态为启用(0)的分类
                vo.setCategoryName(category.getName());
                log.debug("分类查询成功且状态启用，分类ID: {}, 分类状态: {}",
                        copywriting.getCopywritingCategoryId(), category.getStatus());
            } else {
                vo.setCategoryName("未知分类");
                log.debug("分类不存在或状态未启用，设置为'未知分类', categoryId: {}, 分类状态: {}",
                        copywriting.getCopywritingCategoryId(), category != null ? category.getStatus() : null);
            }
        } else {
            vo.setCategoryName("未知分类");
            log.debug("categoryId为null，设置为'未知分类'");
        }

        log.debug("转换完成，返回AdminCopywritingVO: {}", vo.getId());
        return vo;
    }

    /**
     * 批量将TCopywriting实体列表转换为AdminCopywritingVO列表
     */
    private List<AdminCopywritingVO> batchConvertToVO(List<TCopywriting> copywritingList) {
        log.debug("开始批量转换TCopywriting为AdminCopywritingVO，copywritingList大小: {}",
                copywritingList != null ? copywritingList.size() : null);

        if (copywritingList == null || copywritingList.isEmpty()) {
            log.debug("copywritingList为null或空，直接返回空列表");
            return List.of();
        }

        // 获取所有用户ID和分类ID
        log.debug("开始收集用户ID和分类ID");
        List<Long> userIds = copywritingList.stream()
                .map(TCopywriting::getUserId)
                .filter(id -> id != null)
                .distinct()
                .collect(Collectors.toList());
        log.debug("收集到的用户ID数量: {}", userIds.size());

        List<Long> categoryIds = copywritingList.stream()
                .map(TCopywriting::getCopywritingCategoryId)
                .filter(id -> id != null)
                .distinct()
                .collect(Collectors.toList());
        log.debug("收集到的分类ID数量: {}", categoryIds.size());

        // 批量查询用户信息
        log.debug("开始批量查询用户信息");
        Map<Long, String> userMap = new HashMap<>();
        if (!userIds.isEmpty()) {
            log.debug("用户ID列表不为空，执行批量查询，数量: {}", userIds.size());
            LambdaQueryWrapper<TUser> userWrapper = new LambdaQueryWrapper<>();
            userWrapper.in(TUser::getUserId, userIds);
            List<TUser> users = adminUserMapper.selectList(userWrapper);
            log.debug("批量查询用户结果数量: {}", users != null ? users.size() : 0);

            for (TUser user : users) {
                String nickName = user.getNickName() != null ? user.getNickName() : "未知用户";
                userMap.put(user.getUserId(), nickName);
                log.debug("用户信息添加到map，用户ID: {}", user.getUserId());
            }
        } else {
            log.debug("用户ID列表为空，无需查询用户信息");
        }
        // 设置默认值
        userMap.putIfAbsent(null, "未知用户");
        log.debug("用户信息map构建完成，大小: {}", userMap.size());

        // 批量查询分类信息
        log.debug("开始批量查询分类信息");
        Map<Long, String> categoryMap = new HashMap<>();
        if (!categoryIds.isEmpty()) {
            log.debug("分类ID列表不为空，执行批量查询，数量: {}", categoryIds.size());
            // 查询所有分类后过滤状态为启用的
            LambdaQueryWrapper<TCopywritingCategory> categoryWrapper = new LambdaQueryWrapper<>();
            categoryWrapper.in(TCopywritingCategory::getCopywritingCategoryId, categoryIds);
            List<TCopywritingCategory> categories = tCopywritingCategoryMapper.selectList(categoryWrapper);
            log.debug("批量查询分类结果数量: {}", categories != null ? categories.size() : 0);

            for (TCopywritingCategory category : categories) {
                log.debug("处理分类: ID={}, 状态={}",
                        category.getCopywritingCategoryId(), category.getStatus());
                if (category.getStatus() == false) { // 只显示状态为启用(0)的分类
                    categoryMap.put(category.getCopywritingCategoryId(), category.getName());
                    log.debug("分类状态符合条件，添加到map，分类ID: {}",
                            category.getCopywritingCategoryId());
                } else {
                    log.debug("分类状态不符合条件，跳过，分类ID: {}, 分类状态: {}",
                            category.getCopywritingCategoryId(), category.getStatus());
                }
            }
        } else {
            log.debug("分类ID列表为空，无需查询分类信息");
        }
        // 设置默认值
        categoryMap.putIfAbsent(null, "未知分类");
        log.debug("分类信息map构建完成，大小: {}", categoryMap.size());

        // 转换为VO列表
        log.debug("开始转换为VO列表");
        List<AdminCopywritingVO> voList = copywritingList.stream().map(copywriting -> {
            AdminCopywritingVO vo = new AdminCopywritingVO();
            BeanUtils.copyProperties(copywriting, vo);

            // 设置用户姓名
            String userName = userMap.getOrDefault(copywriting.getUserId(), "未知用户");
            vo.setUserName(userName);

            // 设置分类名称
            String categoryName = categoryMap.getOrDefault(copywriting.getCopywritingCategoryId(), "未知分类");
            vo.setCategoryName(categoryName);

            // 通过文案id查询流程id和审批历史
            TCopywritingApprovalFlow flow = getFlowByCopywritingId(copywriting.getCopywritingId());
            if (flow != null) {
                vo.setFlowId(flow.getCopywritingApprovalFlowId());

                // 通过流程id查询审批历史评论
                List<TCopywritingApprovalHistory> historyList = adminCopywritingApprovalHistoryMapper.selectList(
                        new LambdaQueryWrapper<TCopywritingApprovalHistory>()
                                .eq(TCopywritingApprovalHistory::getCopywritingApprovalFlowId, flow.getCopywritingApprovalFlowId())
                                .orderByAsc(TCopywritingApprovalHistory::getCreateTime)
                );

                // 将审批历史评论转换为VO列表
                List<AdminCopywritingApprovalHistoryVO> historyVOList = new ArrayList<>();
                for (TCopywritingApprovalHistory history : historyList) {
                    AdminCopywritingApprovalHistoryVO historyVO = new AdminCopywritingApprovalHistoryVO();
                    historyVO.setComment(history.getComment());
                    historyVO.setOperatorId(history.getOperatorId());
                    historyVO.setOperationStatus(history.getOperationStatus());
                    historyVO.setCreateTime(history.getCreateTime());
                    historyVO.setFromStatus(history.getFromStatus());
                    historyVO.setToStatus(history.getToStatus());

                    // 通过operatorId查询用户昵称
                    if (history.getOperatorId() != null) {
                        TUser user = adminUserMapper.selectOne(
                                new LambdaQueryWrapper<TUser>()
                                        .eq(TUser::getUserId, history.getOperatorId())
                                        .select(TUser::getNickName)
                        );
                        if (user != null && user.getNickName() != null) {
                            historyVO.setOperatorName(user.getNickName());
                        } else {
                            historyVO.setOperatorName("未知用户");
                        }
                    } else {
                        historyVO.setOperatorName("未知用户");
                    }
                    historyVOList.add(historyVO);
                }
                vo.setApprovalHistories(historyVOList);
            }

            log.debug("单个文案转换完成，copywritingId: {}", copywriting.getCopywritingId());
            return vo;
        }).collect(Collectors.toList());

        log.debug("批量转换完成，返回VO列表大小: {}", voList.size());
        return voList;
    }

    @Override
    public IPage<AdminCopywritingVO> pageCopywritings(Integer pageNum, Integer pageSize, String title, Long categoryId) {
        Page<TCopywriting> page = new Page<>(pageNum, pageSize);
        QueryWrapper<TCopywriting> wrapper = new QueryWrapper<>();
        if (title != null) wrapper.like("title", title);
        if (categoryId != null) wrapper.eq("copywriting_category_id", categoryId);
        wrapper.orderByDesc("create_time");

        IPage<TCopywriting> resultPage = baseMapper.selectPage(page, wrapper);
        List<AdminCopywritingVO> voList = batchConvertToVO(resultPage.getRecords());

        // 创建新的IPage对象，保持原有的分页信息
        Page<AdminCopywritingVO> voPage = new Page<>(pageNum, pageSize);
        voPage.setRecords(voList);
        voPage.setTotal(resultPage.getTotal());
        voPage.setCurrent(resultPage.getCurrent());
        voPage.setSize(resultPage.getSize());
        voPage.setPages(resultPage.getPages());

        return voPage;
    }

    @Override
    public IPage<AdminCopywritingVO> pageExpertApprovalCopywritings(Integer pageNum, Integer pageSize, String title, Long categoryId) {
        log.debug("执行专家审核文案分页查询，页码:{}, 每页大小:{}, 分类ID:{}", pageNum, pageSize, categoryId);

        // 构建查询条件，直接在TCopywriting中关联查询
        Page<TCopywriting> page = new Page<>(pageNum, pageSize);
        QueryWrapper<TCopywriting> wrapper = new QueryWrapper<>();

        // 子查询：获取current_status=1的审批流程对应的copywriting_id
        wrapper.inSql("copywriting_id", "SELECT copywriting_id FROM t_copywriting_approval_flow WHERE current_status = 1");

        // 添加标题搜索条件
        if (title != null && !title.isEmpty()) {
            wrapper.like("title", title);
        }

        // 添加分类筛选条件
        if (categoryId != null) {
            wrapper.eq("copywriting_category_id", categoryId);
        }

        // 按创建时间降序排序
        wrapper.orderByDesc("create_time");

        // 执行查询
        IPage<TCopywriting> resultPage = baseMapper.selectPage(page, wrapper);
        log.debug("专家审核文案查询结果：总数:{}, 列表大小:{}", resultPage.getTotal(), resultPage.getRecords().size());

        // 转换为VO列表
        List<AdminCopywritingVO> voList = batchConvertToVO(resultPage.getRecords());

        // 创建新的IPage对象，保持原有的分页信息
        Page<AdminCopywritingVO> voPage = new Page<>(pageNum, pageSize);
        voPage.setRecords(voList);
        voPage.setTotal(resultPage.getTotal());
        voPage.setCurrent(resultPage.getCurrent());
        voPage.setSize(resultPage.getSize());
        voPage.setPages(resultPage.getPages());

        return voPage;
    }

    @Override
    public IPage<AdminCopywritingVO> pagePlatformApprovalCopywritings(Integer pageNum, Integer pageSize, String title, Long categoryId) {
        log.debug("执行所属医院/机构终审文案分页查询，页码:{}, 每页大小:{}, 分类ID:{}", pageNum, pageSize, categoryId);

        // 构建查询条件，直接在TCopywriting中关联查询
        Page<TCopywriting> page = new Page<>(pageNum, pageSize);
        QueryWrapper<TCopywriting> wrapper = new QueryWrapper<>();

        // 子查询：获取所属医院/机构待终审（current_status=3）的审批流程对应的copywriting_id
        wrapper.inSql("copywriting_id", "SELECT copywriting_id FROM t_copywriting_approval_flow WHERE current_status = 3");

        // 添加标题搜索条件
        if (title != null && !title.isEmpty()) {
            wrapper.like("title", title);
        }

        // 添加分类筛选条件
        if (categoryId != null) {
            wrapper.eq("copywriting_category_id", categoryId);
        }

        // 按创建时间降序排序
        wrapper.orderByDesc("create_time");

        // 执行查询
        IPage<TCopywriting> resultPage = baseMapper.selectPage(page, wrapper);
        log.debug("所属医院/机构终审文案查询结果：总数:{}, 列表大小:{}", resultPage.getTotal(), resultPage.getRecords().size());

        // 转换为VO列表
        List<AdminCopywritingVO> voList = batchConvertToVO(resultPage.getRecords());

        // 创建新的IPage对象，保持原有的分页信息
        Page<AdminCopywritingVO> voPage = new Page<>(pageNum, pageSize);
        voPage.setRecords(voList);
        voPage.setTotal(resultPage.getTotal());
        voPage.setCurrent(resultPage.getCurrent());
        voPage.setSize(resultPage.getSize());
        voPage.setPages(resultPage.getPages());

        return voPage;
    }

    @Override
    public IPage<AdminCopywritingVO> pagePlatformApprovedCopywritings(Integer pageNum, Integer pageSize, String title, Long categoryId) {
        log.debug("执行机构终审通过文案分页查询，页码:{}, 每页大小:{}, 分类ID:{}", pageNum, pageSize, categoryId);

        // 构建查询条件，直接在TCopywriting中关联查询
        Page<TCopywriting> page = new Page<>(pageNum, pageSize);
        QueryWrapper<TCopywriting> wrapper = new QueryWrapper<>();

        // 子查询：获取审批完成（current_status=4）的审批流程对应的copywriting_id
        wrapper.inSql("copywriting_id", "SELECT copywriting_id FROM t_copywriting_approval_flow WHERE current_status = 4");

        // 添加标题搜索条件
        if (title != null && !title.isEmpty()) {
            wrapper.like("title", title);
        }

        // 添加分类筛选条件
        if (categoryId != null) {
            wrapper.eq("copywriting_category_id", categoryId);
        }

        // 按创建时间降序排序
        wrapper.orderByDesc("create_time");

        // 执行查询
        IPage<TCopywriting> resultPage = baseMapper.selectPage(page, wrapper);
        log.debug("机构终审通过文案查询结果：总数:{}, 列表大小:{}", resultPage.getTotal(), resultPage.getRecords().size());

        // 转换为VO列表，确保正确处理当前操作人为null的情况
        List<AdminCopywritingVO> voList = resultPage.getRecords().stream().map(copywriting -> {
            AdminCopywritingVO vo = convertToVO(copywriting);
            if (vo != null) {
                // 获取审批流程信息，处理current_processor_id可能为null的情况
                TCopywritingApprovalFlow flow = getFlowByCopywritingId(copywriting.getCopywritingId());
                if (flow != null) {
                    vo.setFlowId(flow.getCopywritingApprovalFlowId());
                    // 处理当前操作人，允许为null
                    if (flow.getCurrentProcessorId() != null) {
                        try {
                            TUser user = adminUserMapper.selectById(flow.getCurrentProcessorId());
                            if (user != null) {
                                vo.setCurrentProcessorName(user.getNickName() != null ? user.getNickName() : "未知用户");
                            } else {
                                vo.setCurrentProcessorName("未知用户");
                            }
                        } catch (Exception e) {
                            log.error("查询当前操作人信息失败: processorId={}, code={}, type={}",
                                    flow.getCurrentProcessorId(), ResponseCode.SERVICE_ERROR.getCode(),
                                    exceptionType(e));
                            vo.setCurrentProcessorName("未知用户");
                        }
                    } else {
                        // 当前操作人为null时，设置为默认值
                        vo.setCurrentProcessorName(null);
                    }
                }
            }
            return vo;
        }).filter(vo -> vo != null).collect(Collectors.toList());

        // 创建新的IPage对象，保持原有的分页信息
        Page<AdminCopywritingVO> voPage = new Page<>(pageNum, pageSize);
        voPage.setRecords(voList);
        voPage.setTotal(resultPage.getTotal());
        voPage.setCurrent(resultPage.getCurrent());
        voPage.setSize(resultPage.getSize());
        voPage.setPages(resultPage.getPages());

        return voPage;
    }

    @Override
    public String getCategoryName(Long categoryId) {
        if (categoryId == null) {
            return "未知分类";
        }
        TCopywritingCategory category = tCopywritingCategoryMapper.selectById(categoryId);
        return category != null && category.getStatus() ? category.getName() : "未知分类";
    }

    /**
     * 通过文案id查询流程信息
     *
     * @param copywritingId 文案id
     * @return 流程实体
     */
    private TCopywritingApprovalFlow getFlowByCopywritingId(Long copywritingId) {
        return adminCopywritingApprovalFlowMapper.selectOne(
                new LambdaQueryWrapper<TCopywritingApprovalFlow>()
                        .eq(TCopywritingApprovalFlow::getCopywritingId, copywritingId)
        );
    }

    private static String exceptionType(Throwable exception) {
        if (exception == null) {
            return "Unknown";
        }
        String type = exception.getClass().getSimpleName();
        return type == null || type.isBlank() ? "Unknown" : type;
    }

    @Override
    public AdminCopywritingVO getById(Long id) {
        TCopywriting copywriting = baseMapper.selectById(id);
        if (copywriting == null) {
            return null;
        }
        AdminCopywritingVO adminCopywritingVO = convertToVO(copywriting);

        // 通过文案id查询流程id
        TCopywritingApprovalFlow flow = getFlowByCopywritingId(copywriting.getCopywritingId());
        if (flow != null) {
            adminCopywritingVO.setFlowId(flow.getCopywritingApprovalFlowId());

            // 通过流程id查询审批历史评论
            List<TCopywritingApprovalHistory> historyList = adminCopywritingApprovalHistoryMapper.selectList(
                    new LambdaQueryWrapper<TCopywritingApprovalHistory>()
                            .eq(TCopywritingApprovalHistory::getCopywritingApprovalFlowId, flow.getCopywritingApprovalFlowId())
                            .orderByAsc(TCopywritingApprovalHistory::getCreateTime)
            );

            // 将审批历史评论转换为VO列表
            List<AdminCopywritingApprovalHistoryVO> historyVOList = new ArrayList<>();
            for (TCopywritingApprovalHistory history : historyList) {
                AdminCopywritingApprovalHistoryVO historyVO = new AdminCopywritingApprovalHistoryVO();
                historyVO.setComment(history.getComment());
                historyVO.setOperatorId(history.getOperatorId());
                historyVO.setOperationStatus(history.getOperationStatus());
                historyVO.setCreateTime(history.getCreateTime());
                historyVO.setFromStatus(history.getFromStatus());
                historyVO.setToStatus(history.getToStatus());
                historyVOList.add(historyVO);
            }
            adminCopywritingVO.setApprovalHistories(historyVOList);
        }

        return adminCopywritingVO;
    }
}
