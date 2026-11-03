package com.hzmwmt.domain.service.function.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hzmwmt.domain.entity.function.TAdminDigitalModel;
import com.hzmwmt.domain.entity.function.TUserDigitalModelMap;
import com.hzmwmt.domain.entity.user.TUser;
import com.hzmwmt.domain.mapper.function.AdminDigitalModelMapper;
import com.hzmwmt.domain.mapper.function.TUserDigitalModelMapMapper;
import com.hzmwmt.domain.mapper.user.AdminUserMapper;
import com.hzmwmt.domain.service.function.DigitalModelService;
import com.hzmwmt.domain.vo.DigitalModelVO;
import com.hzmwmt.types.enums.ResponseCode;
import com.hzmwmt.types.exception.AppException;
import com.hzmwmt.types.annotations.OperationLog;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 数字模型服务实现类
 *
 * @author AI Assistant
 */
@Service
public class DigitalModelServiceImpl extends ServiceImpl<AdminDigitalModelMapper, TAdminDigitalModel> implements DigitalModelService {

    private static final Logger log = LoggerFactory.getLogger(DigitalModelServiceImpl.class);

    @Autowired
    private TUserDigitalModelMapMapper userDigitalModelMapMapper;
    @Autowired
    private AdminUserMapper adminUserMapper;

    /**
     * 查询所有数字模型信息
     *
     * @return 数字模型VO列表
     */
    @Override
    public List<DigitalModelVO> queryAllDigitalModels() {
        // 使用MyBatis-Plus的方法查询所有未删除的数字模型
        List<TAdminDigitalModel> digitalModels = this.list();

        // 转换为VO对象列表
        return digitalModels.stream()
                .map(this::convertToVO)
                .collect(Collectors.toList());
    }

    /**
     * 将实体对象转换为VO对象
     *
     * @param digitalModel 数字模型实体
     * @return 数字模型VO
     */
    private DigitalModelVO convertToVO(TAdminDigitalModel digitalModel) {
        DigitalModelVO vo = new DigitalModelVO();
        // 复制需要的字段
        vo.setId(digitalModel.getId().toString());
        vo.setModelName(digitalModel.getModelName());
        vo.setModelId(digitalModel.getModelId());
        vo.setModelUrl(digitalModel.getModelUrl());
        vo.setHospital(digitalModel.getHospital());
        return vo;
    }

    /**
     * 根据用户ID查询绑定的数字模型列表
     *
     * @param userId 用户ID
     * @return 用户绑定的数字模型VO列表
     */
    @Override
    public List<DigitalModelVO> queryUserBoundModels(Long userId) {
        // 参数校验
        if (userId == null) {
            return Collections.emptyList();
        }

        // 根据用户ID查询绑定关系
        LambdaQueryWrapper<TUserDigitalModelMap> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(TUserDigitalModelMap::getUserId, userId);  // 使用Lambda表达式确保类型安全
        List<TUserDigitalModelMap> userModelMaps = userDigitalModelMapMapper.selectList(wrapper);

        if (userModelMaps == null || userModelMaps.isEmpty()) {
            return Collections.emptyList();
        }

        // 提取绑定的模型ID列表
        List<String> modelIds = userModelMaps.stream()
                .filter(map -> map.getDigitalModelId() != null)  // 过滤空值
                .map(TUserDigitalModelMap::getDigitalModelId)
                .collect(Collectors.toList());

        if (modelIds.isEmpty()) {
            return Collections.emptyList();
        }

        // 根据模型ID列表查询模型信息 - 使用id字段匹配（主键）
        LambdaQueryWrapper<TAdminDigitalModel> modelWrapper = new LambdaQueryWrapper<>();
        modelWrapper.in(TAdminDigitalModel::getId, modelIds);  // 匹配主键id而不是modelId
        List<TAdminDigitalModel> digitalModels = this.baseMapper.selectList(modelWrapper);

        // 转换为VO对象列表
        return digitalModels.stream()
                .map(this::convertToVO)
                .collect(Collectors.toList());
    }

    /**
     * 修改用户和数字模型的绑定关系
     * 逻辑：先删除用户的全部绑定关系，然后添加新的绑定关系
     *
     * @param userId   用户ID
     * @param modelIds 要绑定的数字模型ID列表
     * @return 是否修改成功
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @OperationLog(operationType = "修改", description = "更新用户数字模型绑定", recordResult = false, critical = true)
    public boolean updateUserModelBindings(Long userId, List<Long> modelIds) {
        if (userId == null) {
            throw new AppException(ResponseCode.ILLEGAL_PARAMETER);
        }

        Long userCount = adminUserMapper.selectCount(new LambdaQueryWrapper<TUser>()
                .eq(TUser::getUserId, userId));
        if (userCount == null || userCount != 1L) {
            throw new AppException(ResponseCode.E0005);
        }

        Set<String> requestedModelIds = new LinkedHashSet<>();
        if (modelIds != null) {
            for (Long modelId : modelIds) {
                if (modelId == null || modelId <= 0) {
                    throw new AppException(ResponseCode.ILLEGAL_PARAMETER);
                }
                requestedModelIds.add(modelId.toString());
            }
        }

        if (!requestedModelIds.isEmpty()) {
            Long existingModels = this.baseMapper.selectCount(
                    new LambdaQueryWrapper<TAdminDigitalModel>()
                            .in(TAdminDigitalModel::getId, requestedModelIds));
            if (existingModels == null || existingModels != requestedModelIds.size()) {
                throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), "存在无效的数字模型ID");
            }
        }

        LambdaQueryWrapper<TUserDigitalModelMap> deleteWrapper = new LambdaQueryWrapper<>();
        deleteWrapper.eq(TUserDigitalModelMap::getUserId, userId);
        userDigitalModelMapMapper.delete(deleteWrapper);

        for (String modelId : requestedModelIds) {
            TUserDigitalModelMap binding = new TUserDigitalModelMap();
            binding.setUserId(userId);
            binding.setDigitalModelId(modelId);
            if (userDigitalModelMapMapper.insert(binding) != 1) {
                throw new AppException(ResponseCode.SERVICE_ERROR);
            }
        }

        return true;
    }
}
