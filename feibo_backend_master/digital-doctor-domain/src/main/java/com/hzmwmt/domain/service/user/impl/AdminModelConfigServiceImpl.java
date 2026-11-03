package com.hzmwmt.domain.service.user.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hzmwmt.domain.ai.adapter.port.IAIModelRuntimeManager;
import com.hzmwmt.domain.dto.AdminAiModelCreateDTO;
import com.hzmwmt.domain.dto.AdminAiModelUpdateDTO;
import com.hzmwmt.domain.entity.user.TAiModelConfig;
import com.hzmwmt.domain.mapper.user.AdminModelConfigMapper;
import com.hzmwmt.domain.security.AIModelApiKeyCipher;
import com.hzmwmt.domain.service.user.AdminModelConfigService;
import com.hzmwmt.domain.vo.AdminAiModelVO;
import com.hzmwmt.domain.vo.AdminModelConfigVO;
import com.hzmwmt.types.enums.ResponseCode;
import com.hzmwmt.types.exception.AppException;
import jakarta.annotation.Resource;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
public class AdminModelConfigServiceImpl extends ServiceImpl<AdminModelConfigMapper, TAiModelConfig> implements AdminModelConfigService {

    @Resource
    private AdminModelConfigMapper adminModelConfigMapper;
    @Resource
    private AIModelApiKeyCipher apiKeyCipher;
    @Resource
    private IAIModelRuntimeManager runtimeManager;

    @Override
    public List<Long> getIdByName(String name) {
        // 非空判断：如果名称为空，返回空列表（避免返回null，减少调用方空指针判断）
        if (StringUtils.isBlank(name)) {
            return Collections.emptyList();
        }

        // 构建查询条件：根据name精确匹配
        LambdaQueryWrapper<TAiModelConfig> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(TAiModelConfig::getName, name)
                .eq(TAiModelConfig::getIsDeleted, false);

        // 只查询id字段（优化性能）
        queryWrapper.select(TAiModelConfig::getAiModelConfigId);

        // 执行查询，获取所有匹配的实体
        List<TAiModelConfig> modelConfigs = adminModelConfigMapper.selectList(queryWrapper);

        // 转换为ID列表（使用流处理）
        return modelConfigs.stream()
                .map(TAiModelConfig::getAiModelConfigId) // 提取每个实体的ID
                .collect(Collectors.toList()); // 收集为List<Long>
    }

    @Override
    public List<AdminModelConfigVO> getAdminModelConfigs() {
        return adminModelConfigMapper.selectList(new LambdaQueryWrapper<TAiModelConfig>()
                        .eq(TAiModelConfig::getIsDeleted, false)
                        .select(TAiModelConfig::getAiModelConfigId, TAiModelConfig::getName))
                .stream()
                .map(e -> new AdminModelConfigVO(e.getAiModelConfigId(), e.getName()))
                .toList();
    }

    @Override
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public List<AdminAiModelVO> listManagedModels() {
        return adminModelConfigMapper.selectManagedModels().stream()
                .map(this::toManagedVO)
                .toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public AdminAiModelVO createManagedModel(AdminAiModelCreateDTO request) {
        if (request == null) {
            throw illegalParameter("模型参数不能为空");
        }
        String modelCode = normalizeModelCode(request.getModelCode());
        String displayName = requireName(request.getName(), "展示名称不能为空");
        String providerModelName = requireModelName(request.getModelName());
        if (Boolean.TRUE.equals(request.getVisible())) {
            throw illegalParameter("新增模型必须先保持隐藏，请配置并核验后再启用");
        }
        if (!runtimeManager.isSupportedProvider(modelCode)) {
            throw illegalParameter("不支持的模型提供商");
        }
        int providerScopeCount = adminModelConfigMapper
                .countManagedProviderScopesForModelCode(modelCode);
        if (providerScopeCount == 0) {
            throw illegalParameter("该提供商尚未配置基础模型");
        }
        if (providerScopeCount != 1) {
            throw illegalParameter("该调用策略对应多个供应商，无法确定新增模型归属");
        }
        TAiModelConfig providerTemplate = adminModelConfigMapper.selectProviderTemplate(modelCode);
        if (providerTemplate == null || providerTemplate.getParentId() == null) {
            throw illegalParameter("该提供商尚未配置基础模型");
        }
        List<TAiModelConfig> siblings = lockOrderedSiblings(providerTemplate.getParentId());
        if (siblings.stream().anyMatch(model ->
                Objects.equals(modelCode, model.getModelCode())
                        && Objects.equals(providerModelName, model.getName()))) {
            throw new AppException(ResponseCode.INDEX_EXCEPTION);
        }
        int targetPosition = request.getOrder() == null
                ? siblings.size() + 1
                : requireTargetPosition(request.getOrder(), siblings.size() + 1, "模型版本");

        Long businessId = IdWorker.getId();
        String normalizedKey = normalizeOptionalKey(request.getApiKey());
        String encryptedKey = normalizedKey == null
                ? null
                : apiKeyCipher.encrypt(businessId, normalizedKey);

        TAiModelConfig model = new TAiModelConfig();
        model.setAiModelConfigId(businessId);
        model.setParentId(providerTemplate.getParentId());
        model.setIsLeaf(true);
        model.setDisplayName(displayName);
        model.setName(providerModelName);
        model.setDisplayDescription(normalizeDescription(request.getDescription()));
        model.setModelCode(modelCode);
        model.setType(false);
        model.setImage(providerTemplate.getImage());
        model.setProviderId(providerTemplate.getProviderId());
        model.setProviderName(providerTemplate.getProviderName());
        model.setProviderOrder(providerTemplate.getProviderOrder());
        model.setSort(targetPosition);
        model.setVisible(false);
        model.setIsDeleted(false);
        model.setApiKeyCiphertext(encryptedKey);
        if (adminModelConfigMapper.insert(model) != 1) {
            throw new AppException(ResponseCode.UPDATE_ZERO);
        }
        siblings.add(targetPosition - 1, model);
        persistSequentialModelOrder(siblings, providerTemplate.getParentId());
        invalidateAfterCommit(businessId);
        return toManagedVO(model);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public AdminAiModelVO updateManagedModel(
            Long aiModelConfigId,
            AdminAiModelUpdateDTO request) {
        if (request == null) {
            throw illegalParameter("模型参数不能为空");
        }
        TAiModelConfig existing = requireLeafModel(aiModelConfigId);
        List<TAiModelConfig> reorderedSiblings = null;
        if (request.getOrder() != null) {
            reorderedSiblings = moveModelToPosition(
                    lockOrderedSiblings(existing.getParentId()),
                    aiModelConfigId,
                    request.getOrder());
        }
        TAiModelConfig update = new TAiModelConfig();
        update.setAiModelConfigId(aiModelConfigId);
        update.setParentId(existing.getParentId());
        boolean metadataChanged = false;
        if (request.getName() != null) {
            update.setDisplayName(requireName(request.getName(), "展示名称不能为空"));
            metadataChanged = true;
        }
        if (request.getModelName() != null) {
            String providerModelName = requireModelName(request.getModelName());
            List<TAiModelConfig> siblings = reorderedSiblings == null
                    ? lockOrderedSiblings(existing.getParentId())
                    : reorderedSiblings;
            if (!Objects.equals(providerModelName, existing.getName())
                    && siblings.stream().anyMatch(model ->
                    !Objects.equals(model.getAiModelConfigId(), aiModelConfigId)
                            && Objects.equals(providerModelName, model.getName()))) {
                throw new AppException(ResponseCode.INDEX_EXCEPTION);
            }
            update.setName(providerModelName);
            metadataChanged = true;
        }
        if (request.getDescription() != null) {
            update.setDisplayDescription(normalizeDescription(request.getDescription()));
            metadataChanged = true;
        }
        if (!metadataChanged && reorderedSiblings == null) {
            return toManagedVO(existing);
        }
        if (metadataChanged
                && adminModelConfigMapper.updateManagedLeafMetadata(update) != 1) {
            throw new AppException(ResponseCode.UPDATE_ZERO);
        }
        if (reorderedSiblings != null) {
            persistSequentialModelOrder(reorderedSiblings, existing.getParentId());
        }
        invalidateAfterCommit(aiModelConfigId);
        return toManagedVO(requireLeafModel(aiModelConfigId));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public List<AdminAiModelVO> updateProviderOrder(Long providerId, Integer order) {
        if (providerId == null || providerId <= 0) {
            throw illegalParameter("供应商 ID 无效");
        }
        if (order == null) {
            throw illegalParameter("供应商目标位置不能为空");
        }
        if (adminModelConfigMapper.lockManagedGeneralRootForUpdate() == null) {
            throw illegalParameter("通用模型根节点不存在");
        }
        List<TAiModelConfig> providers = orderedCopy(
                adminModelConfigMapper.selectManagedProvidersForUpdate());
        if (adminModelConfigMapper.countManagedEmptyProviders() != 0) {
            throw illegalParameter("存在无模型版本的供应商，无法确定完整排序位置");
        }
        TAiModelConfig provider = providers.stream()
                .filter(item -> Objects.equals(item.getAiModelConfigId(), providerId))
                .findFirst()
                .orElse(null);
        if (provider == null || Boolean.TRUE.equals(provider.getIsLeaf())) {
            throw illegalParameter("供应商不存在");
        }
        int targetPosition = requireTargetPosition(order, providers.size(), "供应商");
        providers.remove(provider);
        providers.add(targetPosition - 1, provider);
        persistSequentialProviderOrder(providers);
        return adminModelConfigMapper.selectManagedModels().stream()
                .filter(model -> Objects.equals(model.getProviderId(), providerId))
                .map(model -> {
                    model.setProviderOrder(targetPosition);
                    return toManagedVO(model);
                })
                .toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public AdminAiModelVO updateVisibility(Long aiModelConfigId, Boolean visible) {
        if (visible == null) {
            throw illegalParameter("显示状态不能为空");
        }
        TAiModelConfig existing = requireLeafModel(aiModelConfigId);
        if (visible) {
            requireReadyForVisibility(existing);
        }
        if (adminModelConfigMapper.updateManagedLeafVisibility(
                aiModelConfigId, existing.getParentId(), visible) != 1) {
            throw new AppException(ResponseCode.UPDATE_ZERO);
        }
        invalidateAfterCommit(aiModelConfigId);
        existing.setVisible(visible);
        return toManagedVO(existing);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public AdminAiModelVO updateApiKey(Long aiModelConfigId, String apiKey) {
        TAiModelConfig existing = requireLeafModel(aiModelConfigId);
        String normalizedKey = normalizeOptionalKey(apiKey);
        if (normalizedKey == null) {
            return toManagedVO(existing);
        }
        if (!runtimeManager.isSupportedProvider(existing.getModelCode())) {
            throw illegalParameter("该模型不支持运行时 API Key 更新");
        }
        String encryptedKey = apiKeyCipher.encrypt(aiModelConfigId, normalizedKey);
        if (adminModelConfigMapper.updateManagedLeafApiKey(
                aiModelConfigId, existing.getParentId(), encryptedKey) != 1) {
            throw new AppException(ResponseCode.UPDATE_ZERO);
        }
        invalidateAfterCommit(aiModelConfigId);
        existing.setApiKeyCiphertext(encryptedKey);
        return toManagedVO(existing);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public void deleteManagedModel(Long aiModelConfigId) {
        TAiModelConfig existing = requireLeafModel(aiModelConfigId);
        Long parentId = existing.getParentId();
        List<TAiModelConfig> siblings = lockOrderedSiblings(parentId);
        TAiModelConfig lockedModel = siblings.stream()
                .filter(model -> Objects.equals(
                        model.getAiModelConfigId(), aiModelConfigId))
                .findFirst()
                .orElseThrow(() -> illegalParameter("模型不存在"));

        if (!Boolean.FALSE.equals(lockedModel.getVisible())) {
            throw illegalParameter("请先隐藏模型，再执行删除");
        }
        if (siblings.size() <= 1) {
            throw illegalParameter("每个供应商至少需要保留一个模型版本");
        }
        if (adminModelConfigMapper.softDeleteManagedLeafModel(
                aiModelConfigId, parentId) != 1) {
            throw new AppException(ResponseCode.UPDATE_ZERO);
        }

        siblings.remove(lockedModel);
        persistSequentialModelOrder(siblings, parentId);
        invalidateAfterCommit(aiModelConfigId);
    }

    private TAiModelConfig requireLeafModel(Long aiModelConfigId) {
        if (aiModelConfigId == null || aiModelConfigId <= 0) {
            throw illegalParameter("模型 ID 无效");
        }
        TAiModelConfig model = adminModelConfigMapper.selectManagedModelById(aiModelConfigId);
        if (model == null
                || !Boolean.TRUE.equals(model.getIsLeaf())
                || Boolean.TRUE.equals(model.getIsDeleted())) {
            throw illegalParameter("模型不存在");
        }
        return model;
    }

    private List<TAiModelConfig> lockOrderedSiblings(Long parentId) {
        if (parentId == null || parentId <= 0) {
            throw illegalParameter("模型所属供应商无效");
        }
        if (adminModelConfigMapper.lockManagedProviderScopeForUpdate(parentId) == null) {
            throw illegalParameter("模型所属供应商不存在");
        }
        return orderedCopy(adminModelConfigMapper
                .selectManagedSiblingModelsForUpdate(parentId));
    }

    private List<TAiModelConfig> moveModelToPosition(
            List<TAiModelConfig> siblings,
            Long aiModelConfigId,
            Integer requestedPosition) {
        TAiModelConfig target = siblings.stream()
                .filter(model -> Objects.equals(
                        model.getAiModelConfigId(), aiModelConfigId))
                .findFirst()
                .orElseThrow(() -> illegalParameter("模型不存在"));
        int targetPosition = requireTargetPosition(
                requestedPosition,
                siblings.size(),
                "模型版本");
        siblings.remove(target);
        siblings.add(targetPosition - 1, target);
        return siblings;
    }

    private List<TAiModelConfig> orderedCopy(List<TAiModelConfig> models) {
        List<TAiModelConfig> ordered = new ArrayList<>(
                models == null ? Collections.emptyList() : models);
        ordered.sort(Comparator
                .comparing(TAiModelConfig::getSort,
                        Comparator.nullsLast(Integer::compareTo))
                .thenComparing(TAiModelConfig::getAiModelConfigId,
                        Comparator.nullsLast(Long::compareTo)));
        return ordered;
    }

    private int requireTargetPosition(Integer position, int itemCount, String scopeName) {
        if (position == null || position < 1 || position > itemCount) {
            throw illegalParameter(scopeName + "目标位置必须在 1 到 " + itemCount + " 之间");
        }
        return position;
    }

    private void persistSequentialProviderOrder(List<TAiModelConfig> providers) {
        for (int index = 0; index < providers.size(); index++) {
            TAiModelConfig provider = providers.get(index);
            int position = index + 1;
            if (!Objects.equals(provider.getSort(), position)) {
                if (adminModelConfigMapper.updateManagedProviderOrder(
                        provider.getAiModelConfigId(), position) != 1) {
                    throw new AppException(ResponseCode.UPDATE_ZERO);
                }
                provider.setSort(position);
            }
        }
    }

    private void persistSequentialModelOrder(
            List<TAiModelConfig> siblings,
            Long parentId) {
        for (int index = 0; index < siblings.size(); index++) {
            TAiModelConfig model = siblings.get(index);
            int position = index + 1;
            if (!Objects.equals(model.getSort(), position)) {
                if (adminModelConfigMapper.updateManagedModelOrder(
                        model.getAiModelConfigId(), parentId, position) != 1) {
                    throw new AppException(ResponseCode.UPDATE_ZERO);
                }
                model.setSort(position);
            }
        }
    }

    private AdminAiModelVO toManagedVO(TAiModelConfig model) {
        boolean configured = StringUtils.isNotBlank(model.getApiKeyCiphertext())
                || runtimeManager.hasFallbackApiKey(model.getModelCode());
        return AdminAiModelVO.builder()
                .id(String.valueOf(model.getAiModelConfigId()))
                .name(StringUtils.isBlank(model.getDisplayName())
                        ? model.getName()
                        : model.getDisplayName())
                .modelCode(model.getModelCode())
                .modelName(model.getName())
                .providerId(model.getProviderId() == null
                        ? null
                        : String.valueOf(model.getProviderId()))
                .providerName(model.getProviderName())
                .providerOrder(model.getProviderOrder())
                .description(model.getDisplayDescription() == null
                        ? model.getDescription()
                        : model.getDisplayDescription())
                .visible(Boolean.TRUE.equals(model.getVisible()))
                .apiKeyConfigured(configured)
                .apiKeyMasked(configured ? "********" : null)
                .order(model.getSort())
                .build();
    }

    private String normalizeModelCode(String modelCode) {
        return requireName(modelCode, "模型提供商不能为空")
                .toLowerCase(Locale.ROOT);
    }

    private String requireModelName(String value) {
        String modelName = requireName(value, "供应商模型名称不能为空");
        if (modelName.length() > 100 || modelName.codePoints().anyMatch(codePoint ->
                Character.isISOControl(codePoint)
                        || Character.getType(codePoint) == Character.FORMAT)) {
            throw illegalParameter("供应商模型名称格式无效");
        }
        return modelName;
    }

    private void requireReadyForVisibility(TAiModelConfig model) {
        String modelCode = model.getModelCode();
        // A provider must remain attached to the managed general-model tree;
        // the runtime allowlist mirrors the registered model-strategy beans.
        if (adminModelConfigMapper.selectProviderTemplate(modelCode) == null
                || !runtimeManager.isProviderReady(modelCode)) {
            throw illegalParameter("模型提供商当前未启用或配置不完整");
        }
        if (StringUtils.isBlank(model.getApiKeyCiphertext())) {
            if (!runtimeManager.hasFallbackApiKey(modelCode)) {
                throw illegalParameter("显示模型前请先配置 API Key");
            }
            return;
        }
        // Fail closed if the stored override cannot actually be decrypted by
        // this instance. The plaintext is neither retained nor logged.
        apiKeyCipher.decrypt(model.getAiModelConfigId(), model.getApiKeyCiphertext());
    }

    private String requireName(String value, String message) {
        if (StringUtils.isBlank(value)) {
            throw illegalParameter(message);
        }
        return value.trim();
    }

    private String normalizeDescription(String value) {
        return value == null ? null : value.trim();
    }

    private String normalizeOptionalKey(String apiKey) {
        return StringUtils.isBlank(apiKey) ? null : apiKey.trim();
    }

    private void invalidateAfterCommit(Long aiModelConfigId) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            runtimeManager.invalidate(aiModelConfigId);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                runtimeManager.invalidate(aiModelConfigId);
            }
        });
    }

    private AppException illegalParameter(String message) {
        return new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), message);
    }
}
