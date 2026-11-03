package com.hzmwmt.domain.service.user.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.CollectionUtils;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hzmwmt.domain.entity.user.TAiConversation;
import com.hzmwmt.domain.entity.user.TAiMessage;
import com.hzmwmt.domain.entity.user.TAiModelConfig;
import com.hzmwmt.domain.vo.AdminConversationMessageVO;
import com.hzmwmt.domain.ai.model.entity.ChatUsageRecord;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Objects;
import com.hzmwmt.domain.mapper.user.AdminConversationMapper;
import com.hzmwmt.domain.mapper.user.AdminMessageMapper;
import com.hzmwmt.domain.mapper.user.AdminModelConfigMapper;
import com.hzmwmt.domain.service.user.AdminConversationService;
import com.hzmwmt.domain.service.user.AdminModelConfigService;
import com.hzmwmt.domain.service.user.AdminUserService;
import com.hzmwmt.domain.vo.AdminConversationDetailVO;
import com.hzmwmt.domain.vo.AdminConversationVO;
import com.hzmwmt.types.annotations.OperationLog;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@Slf4j
public class AdminConversationServiceImpl extends ServiceImpl<AdminConversationMapper, TAiConversation> implements AdminConversationService {

    @Resource
    private AdminConversationMapper adminConversationMapper;
    @Resource
    private AdminModelConfigService adminModelConfigService;
    @Resource
    private AdminUserService adminUserService;
    @Resource
    private AdminModelConfigMapper adminModelConfigMapper;
    @Resource
    private AdminMessageMapper adminMessageMapper;


    @Override
    // @OperationLog(operationType = "查询", description = "分页查询会话列表", recordParams = true)
    public IPage<AdminConversationVO> getConversationList(Integer pageNum, Integer pageSize, String username, String content, String status, Long modelId) {
        pageNum = pageNum == null ? 1 : Math.max(1, pageNum);
        pageSize = pageSize == null ? 10 : Math.max(1, Math.min(100, pageSize));
        Page<TAiConversation> page = new Page<>(pageNum, pageSize);

        // 2. 构建查询条件
        LambdaQueryWrapper<TAiConversation> queryWrapper = new LambdaQueryWrapper<>();

        // 2.1 按用户名模糊查询
        if (StringUtils.isNotBlank(username)) {
            List<Long> userIds = adminUserService.getIdByName(username);
            if (CollectionUtils.isNotEmpty(userIds)) {
                queryWrapper.in(TAiConversation::getUserId, userIds);
            } else {
                queryWrapper.apply("1 = 3");
            }
        }

        // 2.2 按内容模糊查询
        if (StringUtils.isNotBlank(content)) {
            queryWrapper.like(TAiConversation::getTitle, content);
        }

        // 2.3 按模型匹配
        if (modelId != null) {
            List<Long> conversationIds = this.getConversationIdsByModerConfigId(modelId);
            if (CollectionUtils.isNotEmpty(conversationIds)) {
                queryWrapper.in(TAiConversation::getAiConversationId, conversationIds);
            } else {
                // 确实没有符合该模型的对话
                queryWrapper.apply("1 = 3");
            }
            // 没传模型 ID 时什么都不做，继续走其它条件
        }


        // 2.4 排序：按创建时间排序
        queryWrapper.orderByDesc(TAiConversation::getCreateTime);

        // 3. 执行分页查询
        IPage<TAiConversation> conversationPage = adminConversationMapper.selectPage(page, queryWrapper);

        // 4. 提取主表分页记录，准备构建VO
        List<TAiConversation> conversationList = conversationPage.getRecords();
        if (conversationList.isEmpty()) {
            return new Page<>(pageNum, pageSize, conversationPage.getTotal());
        }

        // 5. 批量查询关联的用户信息（避免N+1查询问题）
        List<Long> userIds = conversationList.stream()
                .map(TAiConversation::getUserId)
                .distinct()
                .collect(Collectors.toList());
        List<Map<Long, String>> userList = adminUserService.getNameByIds(userIds);
        List<Long> conversationIds = conversationList.stream().map(TAiConversation::getAiConversationId).toList();
        List<TAiMessage> summaries = adminMessageMapper.selectList(new LambdaQueryWrapper<TAiMessage>()
                .select(TAiMessage::getAiMessageId, TAiMessage::getAiConversationId, TAiMessage::getAiModelConfigId,
                        TAiMessage::getRole, TAiMessage::getUsageJson)
                .in(TAiMessage::getAiConversationId, conversationIds));
        Map<Long, String> modelNames = modelNames(summaries);
        Map<Long, List<TAiMessage>> grouped = summaries.stream().collect(Collectors.groupingBy(TAiMessage::getAiConversationId));

        // 6. 组装 VO 列表（只改用户匹配逻辑）
        List<AdminConversationVO> voList = conversationList.stream()
                .map(conversation -> {
                    AdminConversationVO vo = new AdminConversationVO();
                    // 1. 先获取当前对话的userId（关键：用这个ID去匹配用户）
                    Long currentConversationUserId = conversation.getUserId();

                    // 2. 其他基础字段赋值（不变）
                    vo.setId(conversation.getAiConversationId());
                    vo.setTitle(conversation.getTitle());
                    vo.setCreateTime(conversation.getCreateTime());
                    AdminConversationDetailVO summary = summarize(grouped.getOrDefault(conversation.getAiConversationId(), List.of()), modelNames);
                    vo.setModel(summary.getModel());
                    vo.setMeasuredTokens(summary.getTokens());
                    vo.setEstimatedCost(summary.getEstimatedCost());
                    vo.setTotalReplies(summary.getTotalReplies());
                    vo.setMeteredReplies(summary.getMeteredReplies());
                    vo.setPricedReplies(summary.getPricedReplies());

                    // 3. 修正：匹配当前对话对应的用户名（核心修复）
                    String name = userList.stream()
                            // 筛选条件：当前userMap的key（userId） == 当前对话的userId
                            .filter(userMap -> {
                                // 每个userMap只有1个键（Long类型的userId），提取这个key
                                Long mapUserId = userMap.keySet().iterator().next();
                                // 匹配：当前对话的userId == userMap的key
                                return mapUserId.equals(currentConversationUserId);
                            })
                            // 提取userMap的value（即用户名）
                            .map(userMap -> {
                                // 每个userMap只有1个值（用户名），提取这个value
                                return userMap.values().iterator().next();
                            })
                            // 没匹配到则返回"未知用户"
                            .findFirst()
                            .orElse("未知用户");

                    // 4. 赋值用户名
                    vo.setUsername(name);

                    return vo;
                })
                .collect(Collectors.toList());
        // 7. 构建分页 VO 对象
        IPage<AdminConversationVO> voPage = new Page<>();
        voPage.setRecords(voList);
        voPage.setTotal(conversationPage.getTotal());
        voPage.setCurrent(pageNum);
        voPage.setSize(pageSize);
        voPage.setPages(conversationPage.getPages());

        return voPage;
    }


    /**
     * 对话细节
     *
     * @param conversationId 对话ID
     * @return
     */
    //TODO 没有看到页面
    @Override
    // @OperationLog(operationType = "查询", description = "查询会话详情", recordParams = true)
    public AdminConversationDetailVO getConversationDetail(String conversationId) {
        if (conversationId == null || !conversationId.matches("[1-9][0-9]{0,18}")) throw new IllegalArgumentException("对话ID无效");
        long businessId;
        try { businessId = Long.parseLong(conversationId); }
        catch (NumberFormatException ex) { throw new IllegalArgumentException("对话ID无效"); }
        TAiConversation conversation = adminConversationMapper.selectOne(new LambdaQueryWrapper<TAiConversation>()
                .eq(TAiConversation::getAiConversationId, businessId));
        if (conversation == null) throw new com.hzmwmt.types.exception.AppException(
                com.hzmwmt.types.enums.ResponseCode.ILLEGAL_PARAMETER.getCode(), "会话不存在");
        List<TAiMessage> messages = adminMessageMapper.selectList(new LambdaQueryWrapper<TAiMessage>()
                .select(TAiMessage::getId, TAiMessage::getAiMessageId, TAiMessage::getAiConversationId,
                        TAiMessage::getAiModelConfigId, TAiMessage::getRole, TAiMessage::getContent,
                        TAiMessage::getCreateTime, TAiMessage::getToken, TAiMessage::getUsageJson)
                .eq(TAiMessage::getAiConversationId, businessId)
                .orderByAsc(TAiMessage::getCreateTime, TAiMessage::getId));
        AdminConversationDetailVO detail = summarize(messages, modelNames(messages));
        detail.setId(conversationId);
        detail.setConversationId(conversationId);
        detail.setUserId(String.valueOf(conversation.getUserId()));
        detail.setTitle(conversation.getTitle());
        detail.setCreateTime(conversation.getCreateTime());
        detail.setUpdateTime(conversation.getUpdateTime());
        detail.setUsername(adminUserService.getNameByIds(List.of(conversation.getUserId())).stream()
                .map(item -> item.get(conversation.getUserId())).filter(Objects::nonNull).findFirst().orElse("未知用户"));
        return detail;
    }

    private Map<Long, String> modelNames(List<TAiMessage> messages) {
        List<Long> ids = messages.stream().map(TAiMessage::getAiModelConfigId).filter(Objects::nonNull).distinct().toList();
        if (ids.isEmpty()) return Map.of();
        // Never project credential columns, and retain hidden/deleted model history.
        return adminModelConfigMapper.selectList(new LambdaQueryWrapper<TAiModelConfig>()
                .select(TAiModelConfig::getAiModelConfigId, TAiModelConfig::getName)
                .in(TAiModelConfig::getAiModelConfigId, ids)).stream()
                .collect(Collectors.toMap(TAiModelConfig::getAiModelConfigId, m -> Objects.requireNonNullElse(m.getName(), "未记录"), (a,b) -> a));
    }

    static AdminConversationDetailVO summarize(List<TAiMessage> messages, Map<Long, String> names) {
        AdminConversationDetailVO result = new AdminConversationDetailVO();
        List<AdminConversationMessageVO> rows = new ArrayList<>();
        long tokens = 0;
        BigDecimal cost = BigDecimal.ZERO;
        int total = 0, metered = 0, priced = 0;
        for (TAiMessage message : messages) {
            AdminConversationMessageVO row = new AdminConversationMessageVO();
            row.setId(String.valueOf(message.getAiMessageId()));
            row.setRole(Boolean.TRUE.equals(message.getRole()) ? "assistant" : "user");
            row.setContent(message.getContent());
            row.setCreateTime(message.getCreateTime());
            row.setModelId(String.valueOf(message.getAiModelConfigId()));
            row.setModel(names.getOrDefault(message.getAiModelConfigId(), "未记录（模型ID " + message.getAiModelConfigId() + "）"));
            row.setLegacyEstimatedTokens(message.getToken());
            if (Boolean.TRUE.equals(message.getRole())) {
                total++;
                ChatUsageRecord usage = null;
                if (message.getUsageJson() != null) {
                    try { usage = com.alibaba.fastjson.JSON.parseObject(message.getUsageJson(), ChatUsageRecord.class); }
                    catch (RuntimeException ignored) { /* legacy/malformed snapshots are unknown */ }
                }
                row.setUsage(usage);
                if (usage != null) {
                    if (usage.getModel() != null) row.setModel(usage.getModel());
                    if ("PROVIDER".equals(usage.getUsageSource()) && usage.getTotalTokens() != null && usage.getTotalTokens() >= 0) {
                        tokens += usage.getTotalTokens();
                        metered++;
                    }
                    if ("ESTIMATED".equals(usage.getCostStatus()) && "CNY".equals(usage.getCurrency()) && usage.getEstimatedCost() != null && usage.getEstimatedCost().signum() >= 0) {
                        cost = cost.add(usage.getEstimatedCost());
                        priced++;
                    }
                }
            }
            rows.add(row);
        }
        result.setMessages(rows);
        result.setModel(rows.stream().filter(r -> "assistant".equals(r.getRole())).map(AdminConversationMessageVO::getModel).distinct().collect(Collectors.joining("、")));
        result.setTokens(metered == 0 ? null : tokens);
        result.setEstimatedCost(priced == 0 ? null : cost);
        result.setTotalReplies(total);
        result.setMeteredReplies(metered);
        result.setPricedReplies(priced);
        return result;
    }

    @Override
    @OperationLog(operationType = "删除", description = "删除会话", recordParams = true)
    public Boolean deleteConversation(String conversationId) {
        if (StringUtils.isBlank(conversationId)) {
            return false; // 对话ID为空，返回失败
        }

        try {
            // 使用 MyBatis-Plus 的 deleteById 方法删除记录
            int rowsAffected = adminConversationMapper.deleteById(conversationId);
            // 返回 true 如果删除了一条记录，否则返回 false
            return rowsAffected > 0;
        } catch (Exception e) {
            // 记录异常日志（使用 @Slf4j 注解的 log）
            log.error("删除会话失败。conversationId={}, type={}",
                    conversationId, e.getClass().getSimpleName());
            return false; // 异常情况返回失败
        }
    }

    /**
     * TODO 通过模型id获取对话id 通过模型id查询
     *
     * @param moderConfigId
     * @return
     */
    @Override
    // @OperationLog(operationType = "查询", description = "根据模型ID查询会话ID列表", recordParams = true)
    public List<Long> getConversationIdsByModerConfigId(Long moderConfigId) {

        LambdaQueryWrapper<TAiMessage> wrapper = new LambdaQueryWrapper<>();
        wrapper.select(TAiMessage::getAiConversationId)
                .eq(TAiMessage::getAiModelConfigId, moderConfigId)
                .groupBy(TAiMessage::getAiConversationId);   // 去重
        return adminMessageMapper.selectList(wrapper).stream().map(TAiMessage::getAiConversationId).collect(Collectors.toList());
    }
}

