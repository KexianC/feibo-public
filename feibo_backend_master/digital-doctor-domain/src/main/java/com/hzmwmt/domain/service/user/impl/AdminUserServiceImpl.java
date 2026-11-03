package com.hzmwmt.domain.service.user.impl;

import com.alibaba.excel.EasyExcel;
import com.alibaba.excel.context.AnalysisContext;
import com.alibaba.excel.read.listener.ReadListener;
import com.alibaba.excel.util.ListUtils;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hzmwmt.domain.auth.model.aggregate.AuthUserAggregate;
import com.hzmwmt.domain.auth.model.entity.AuthUserEntity;
import com.hzmwmt.domain.auth.service.AuthService;
import com.hzmwmt.domain.copywriting.model.valobj.ApprovalStatusEnumVO;
import com.hzmwmt.domain.dto.AdminUserUpdateDTO;
import com.hzmwmt.domain.dto.ChangePasswordByOldRequestDTO;
import com.hzmwmt.domain.entity.function.TCopywritingApprovalFlow;
import com.hzmwmt.domain.entity.function.TAdminDigitalModel;
import com.hzmwmt.domain.entity.role.TUserRoleMap;
import com.hzmwmt.domain.entity.user.TUser;
import com.hzmwmt.domain.mapper.function.AdminCopywritingApprovalFlowMapper;
import com.hzmwmt.domain.mapper.user.AdminUserMapper;
import com.hzmwmt.domain.service.function.AdminDigitalModelService;
import com.hzmwmt.domain.service.function.DigitalModelService;
import com.hzmwmt.domain.service.order.AdminUserRoleMapService;
import com.hzmwmt.domain.service.user.AdminUserService;
import com.hzmwmt.domain.user.model.entity.UpdatePasswordCommandEntity;
import com.hzmwmt.domain.user.model.entity.UserExcelModel;
import com.hzmwmt.types.enums.ResponseCode;
import com.hzmwmt.types.annotations.OperationLog;
import com.hzmwmt.types.exception.AppException;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

@Service
@Slf4j
public class AdminUserServiceImpl extends ServiceImpl<AdminUserMapper, TUser> implements AdminUserService {

    private final AdminUserMapper adminUserMapper;
    // 注入密码加密器（BCrypt 加密，安全且不可逆）
    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    @Resource
    private AuthService authService;
    @Resource
    private AdminUserRoleMapService adminUserRoleMapService;
    @Resource
    private AdminCopywritingApprovalFlowMapper adminCopywritingApprovalFlowMapper;
    @Resource
    private AdminDigitalModelService adminDigitalModelService;
    @Autowired
    private DigitalModelService digitalModelService;

    public AdminUserServiceImpl(AdminUserMapper adminUserMapper) {
        this.adminUserMapper = adminUserMapper;
    }

    @Override
    public List<Long> getIdByName(String name) {
        // 非空判断：如果名称为空，返回空列表（避免返回null，减少调用方空指针判断）
        if (!StringUtils.hasText(name)) {
            return Collections.emptyList();
        }

        // 构建查询条件：根据name精确匹配
        LambdaQueryWrapper<TUser> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(TUser::getNickName, name);

        // 只查询id字段（优化性能）
        queryWrapper.select(TUser::getUserId);

        // 执行查询，获取所有匹配的实体
        List<TUser> users = adminUserMapper.selectList(queryWrapper);

        // 转换为ID列表（使用流处理）
        return users.stream()
                .map(TUser::getUserId) // 提取每个实体的ID
                .collect(Collectors.toList()); // 收集为List<Long>
    }

    @Override
    public List<Map<Long, String>> getNameByIds(List<Long> userIds) {
        // 1. 处理空参数：若用户ID列表为空，直接返回空列表
        if (userIds == null || userIds.isEmpty()) {
            return new ArrayList<>();
        }

        // 2. 去重ID列表（避免重复查询，提高效率）
        List<Long> distinctUserIds = userIds.stream().distinct().collect(Collectors.toList());


        // 3. 批量查询用户信息（只查id和username，减少数据传输）
        List<TUser> userList = adminUserMapper.selectList(
                new LambdaQueryWrapper<TUser>()
                        .select(TUser::getUserId, TUser::getNickName) // 只查询需要的字段
                        .in(TUser::getUserId, distinctUserIds) // 根据ID列表查询
        );

        // 4. 转换为Map<用户ID, 用户名>（便于快速匹配）
        Map<Long, String> userIdToNameMap = userList.stream()
                .collect(Collectors.toMap(
                        TUser::getUserId,    // key：用户ID
                        TUser::getNickName,  // value：用户名
                        (existing, replacement) -> existing  // 若有重复ID（理论上不会，因为ID是主键），取第一个
                ));

        // 5. 构建返回结果：为原始userIds列表中的每个ID创建一个Map
        List<Map<Long, String>> result = new ArrayList<>();
        for (Long userId : userIds) {
            Map<Long, String> idNameMap = new HashMap<>(1); // 初始容量1，节省内存
            // 若用户存在，取用户名；不存在则设为"未知用户"（可根据业务调整）
            String username = userIdToNameMap.getOrDefault(userId, "未知用户");
            idNameMap.put(userId, username);
            result.add(idNameMap);
        }

        return result;
    }

    /**
     * 根据ID查询用户
     */
    @Override
    public Optional<TUser> getUserById(Long id) {
        if (id == null) {
            return Optional.empty();
        }
        // 调用MP的getById查询
        TUser user = this.getById(id);
        return Optional.ofNullable(user);
    }

    // 新增：根据业务userId查询用户（核心修复）
    @Override
    public Optional<TUser> getUserByBusinessUserId(Long businessUserId) {
        if (businessUserId == null) return Optional.empty();
        // 用业务字段 userId 匹配，而非自增主键 id
        LambdaQueryWrapper<TUser> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(TUser::getUserId, businessUserId); // 匹配 t_user 表的 user_id 字段
        TUser user = this.getOne(wrapper); // 查单个用户（假设 businessUserId 唯一）
        return Optional.ofNullable(user);
    }

    @Override
    public List<Long> getUserIdsByUsernameLike(String username) {
        // 非空判断：如果名称为空，返回空列表
        if (!StringUtils.hasText(username)) {
            return Collections.emptyList();
        }

        // 构建查询条件：根据nick_name模糊匹配
        QueryWrapper<TUser> wrapper = new QueryWrapper<>();
        wrapper.like("nick_name", username);

        // 只查询business_user_id字段
        wrapper.select("business_user_id");

        // 执行查询，获取所有匹配的实体
        List<TUser> users = adminUserMapper.selectList(wrapper);

        // 转换为ID列表
        return users.stream()
                .map(TUser::getUserId)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    // AdminUserServiceImpl 中修正后的方法
    @Override
    public boolean updateByUserId(Long businessUserId, TUser user) {
        // 双重校验：避免空参数
        if (businessUserId == null || user == null) {
            log.error("更新失败：businessUserId或用户实体为空");
            return false;
        }

        // 1. 构建更新条件：WHERE user_id = businessUserId
        LambdaUpdateWrapper<TUser> updateWrapper = new LambdaUpdateWrapper<>();
        updateWrapper.eq(TUser::getUserId, businessUserId); // 条件：业务userId匹配

        // 2. 执行更新：第一个参数=要更新的字段（user中的avatar、updateTime），第二个参数=条件
        boolean success = this.update(user, updateWrapper);
        log.info("数据库更新执行：businessUserId={}, 成功={}", businessUserId, success);
        return success;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @OperationLog(operationType = "新增", description = "批量创建后台用户", recordResult = false, critical = true)
    public List<String> createUserByExcel(MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new AppException(ResponseCode.UPLOAD_EXCEPTION.getCode(), "文件不能为空");
        }
        if (file.getSize() > 10L * 1024 * 1024) {
            throw new AppException(ResponseCode.UPLOAD_EXCEPTION.getCode(), "文件大小不能超过10MB");
        }
        // 获取后缀
        String originalFilename = file.getOriginalFilename();
        if (!StringUtils.hasText(originalFilename) || !originalFilename.contains(".")) {
            throw new AppException(ResponseCode.UPLOAD_EXCEPTION.getCode(), "文件格式错误");
        }
        String suffix = originalFilename.substring(originalFilename.lastIndexOf(".") + 1);
        if (!suffix.equalsIgnoreCase("xlsx") && !suffix.equalsIgnoreCase("xls")) {
            throw new AppException(ResponseCode.UPLOAD_EXCEPTION.getCode(), "文件格式错误");
        }

        // 插入失败错误日志，返回给前端
        List<String> errorLogs = new ArrayList<>();
        AtomicInteger importRow = new AtomicInteger(1);

        // 从文件中读取数据
        EasyExcel.read(file.getInputStream(), UserExcelModel.class, new ReadListener<UserExcelModel>() {

            // 单词缓存数量
            public static final int BATCH_COUNT = 100;
            // 临时存储
            private List<UserExcelModel> cachedDataList = ListUtils.newArrayListWithExpectedSize(BATCH_COUNT);

            @Override
            public void invoke(UserExcelModel data, AnalysisContext context) {
                cachedDataList.add(data);
                if (cachedDataList.size() >= BATCH_COUNT) {
                    saveData();
                    // 存储完成清理 list
                    cachedDataList = ListUtils.newArrayListWithExpectedSize(BATCH_COUNT);
                }
            }

            @Override
            public void doAfterAllAnalysed(AnalysisContext context) {
                saveData();
            }

            // 保存到数据库
            private void saveData() {
                // 先查出所有用户的手机号，用于重复检测
                QueryWrapper<TUser> queryWrapper = new QueryWrapper<>();
                queryWrapper.select("phone_number").isNotNull("phone_number");
                Set<String> phoneNumberSet = list(queryWrapper).stream().map(TUser::getPhoneNumber).collect(Collectors.toSet());
                // 用户id和模型id对应关系
                Map<Long, List<Long>> map = new HashMap<>();
                Collection<TUser> userList = new ArrayList<>();
                for (UserExcelModel data : cachedDataList) {
                    int row = importRow.incrementAndGet();
                    if (!StringUtils.hasText(data.getPhoneNumber()) || !StringUtils.hasText(data.getNickName())) {
                        errorLogs.add(row + "行用户名或手机号为空，已跳过");
                        continue;
                    }
                    String phoneNumber = data.getPhoneNumber().trim();
                    if (phoneNumberSet.contains(phoneNumber)) {
                        errorLogs.add(row + "行手机号已存在，已跳过");
                        continue;
                    }
                    List<Long> modelIds;
                    try {
                        modelIds = parseModelIds(data.getModels());
                    } catch (ImportModelValidationException invalid) {
                        errorLogs.add(row + "行" + invalid.getMessage() + "，已跳过");
                        continue;
                    }
                    TUser user = new TUser();
                    user.setNickName(data.getNickName().trim());
                    user.setPhoneNumber(phoneNumber);
                    Long userId = IdWorker.getId();
                    user.setUserId(userId);
                    user.setAvatar("https://digital-doctor-oss.oss-cn-hangzhou.aliyuncs.com/avatar/default.jpg");
                    user.setStatus(false);
                    user.setPassword(passwordEncoder.encode(UUID.randomUUID().toString()));
                    userList.add(user);
                    // Only valid rows reserve a phone number or create model bindings.
                    phoneNumberSet.add(phoneNumber);
                    map.put(userId, modelIds);
                }
                // 批量插入用户
                if (!userList.isEmpty()) {
                    log.info("批量插入用户开始，数量：{}", userList.size());
                    if (!saveBatch(userList)) throw new IllegalStateException("批量导入未完成，请核对数据后重试");
                }
                // 将映射关系保存到数据库
                map.forEach((userId, modelIds) -> {
                    digitalModelService.updateUserModelBindings(userId, modelIds);
                });
            }
        }).sheet().doRead();
        if (importRow.get() == 1) errorLogs.add("文件中没有可导入的数据行");
        return errorLogs;
    }

    @Override
    public String getSpecialtyByUserId(Long userId) {
        if (userId == null) {
            return null;
        }
        QueryWrapper<TUser> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("user_id", userId).select("specialty");
        return getOne(queryWrapper).getSpecialty();
    }

    /**
     * 从模型名称解析模型id
     *
     * @param modelNames 模型名字，中英文逗号分隔；留空表示不绑定
     * @return 模型id列表
     */
    private List<Long> parseModelIds(String modelNames) {
        if (!StringUtils.hasText(modelNames)) return Collections.emptyList();
        List<String> names = Arrays.stream(modelNames.split("[,，]"))
                .map(String::trim).filter(StringUtils::hasText).distinct().toList();
        if (names.isEmpty()) throw new ImportModelValidationException("未填写有效模型名称");
        List<TAdminDigitalModel> models = adminDigitalModelService.list(new LambdaQueryWrapper<TAdminDigitalModel>()
                .select(TAdminDigitalModel::getId, TAdminDigitalModel::getModelName)
                .in(TAdminDigitalModel::getModelName, names));
        Map<String, List<TAdminDigitalModel>> byName = models.stream().collect(Collectors.groupingBy(TAdminDigitalModel::getModelName));
        List<Long> ids = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        for (String name : names) {
            List<TAdminDigitalModel> matches = byName.getOrDefault(name, Collections.emptyList());
            if (matches.isEmpty()) {
                errors.add("模型不存在：" + name);
            } else if (matches.size() != 1) {
                errors.add("模型名称重名，无法确定绑定：" + name);
            } else {
                try {
                    long id = Long.parseLong(matches.get(0).getId());
                    if (id <= 0) throw new NumberFormatException();
                    ids.add(id);
                } catch (NumberFormatException invalid) {
                    errors.add("模型ID无效：" + name);
                }
            }
        }
        if (!errors.isEmpty()) throw new ImportModelValidationException(String.join("；", errors));
        return ids;
    }

    private static final class ImportModelValidationException extends RuntimeException {
        private ImportModelValidationException(String message) { super(message); }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @OperationLog(operationType = "新增", description = "创建后台用户", recordResult = false, critical = true)
    public TUser createUser(TUser user, Long roleId) {
        try {
            Long userId = IdWorker.getId();
            user.setUserId(userId);

            // 设置默认头像
            user.setAvatar("https://digital-doctor-oss.oss-cn-hangzhou.aliyuncs.com/avatar/default.jpg");

            // 设置默认状态为正常（Boolean类型，true表示禁用，false表示正常）
            user.setStatus(false);

            // 后台建号不再使用可预测的“手机号后六位”。用户可通过短信登录后设置密码。
            if (!StringUtils.hasText(user.getPassword())) {
                user.setPassword(passwordEncoder.encode(UUID.randomUUID().toString()));
            }

            // 设置创建时间和更新时间
            LocalDateTime now = LocalDateTime.now();
            user.setCreateTime(now);
            user.setUpdateTime(now);

            // 保存用户信息
            if (!this.save(user)) {
                throw new AppException(ResponseCode.SERVICE_ERROR);
            }

            // 分配角色（使用默认角色ID 1964218007207526401 或传入的roleId）
            Long finalRoleId = roleId != null ? roleId : 1964218007207526401L;

            // 创建用户角色映射关系
            TUserRoleMap userRoleMap = new TUserRoleMap();
            userRoleMap.setUserId(userId);
            userRoleMap.setRoleId(finalRoleId);
            userRoleMap.setCreateTime(now);

            // 保存用户角色映射
            if (!adminUserRoleMapService.save(userRoleMap)) {
                throw new AppException(ResponseCode.SERVICE_ERROR);
            }

            return user;
        } catch (Exception e) {
            log.error("创建用户失败: code={}, type={}",
                    ResponseCode.SERVICE_ERROR.getCode(), exceptionType(e));
            throw new RuntimeException("创建用户失败", e);
        }
    }

//    @Override
//    public boolean updateByPhoneNumber(String PhoneNumber, TUser user) {
//        // 双重校验：避免空参数
//        if (PhoneNumber == null || user == null) {
//            return false;
//        }
//        // 1. 构建更新条件：WHERE user_id = businessUserId
//        LambdaUpdateWrapper<TUser> updateWrapper = new LambdaUpdateWrapper<>();
//        updateWrapper.eq(TUser::getPhoneNumber, PhoneNumber); // 条件：业务userId匹配
//
//        // 2. 执行更新：第一个参数=要更新的字段（user中的avatar、updateTime），第二个参数=条件
//        boolean success = this.update(user, updateWrapper);
//        return success;
//    }

    /**
     * 更新用户信息
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @OperationLog(operationType = "修改", description = "更新后台用户资料", recordResult = false, critical = true)
    public boolean updateUser(Long id, AdminUserUpdateDTO updateDTO) {
        // 1. 校验ID和参数
        if (id == null || updateDTO == null) {
            return false;
        }

        // 2. 查询用户是否存在
        TUser user = this.getById(id);
        if (user == null) {
            return false; // 用户不存在，更新失败
        }

        // 3. 复制DTO的非空字段到实体（仅更新传递的字段）
        // 昵称
        if (StringUtils.hasText(updateDTO.getNickName())) {
            user.setNickName(updateDTO.getNickName());
        }
        // 头像
        if (StringUtils.hasText(updateDTO.getAvatar())) {
            user.setAvatar(updateDTO.getAvatar());
        }
        // 手机号码
        if (StringUtils.hasText(updateDTO.getPhoneNumber())) {
            user.setPhoneNumber(updateDTO.getPhoneNumber());
        }
        // 性别（允许为0，需判断是否不为null）
        if (updateDTO.getGender() != null) {
            user.setGender(updateDTO.getGender());
        }
        // TODO 密码加密
//        // 密码（若传递则加密后更新）
//        if (StringUtils.hasText(updateDTO.getPassword())) {
//            // 密码加密（若无加密需求，可直接赋值：user.setPassword(updateDTO.getPassword())）
//            user.setPassword(passwordEncoder.encode(updateDTO.getPassword()));
//        }

        // 4. 调用MP的updateById更新（自动更新updateTime，依赖@TableField(fill = INSERT_UPDATE)）
        return this.updateById(user);
    }

    /**
     * 启用或禁用用户
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @OperationLog(operationType = "修改", description = "设置后台用户启禁状态", recordResult = false, critical = true)
    public boolean setUserDisabled(Long id, boolean disabled) {
        // 1. 参数校验：ID不能为空
        if (id == null) {
            return false;
        }

        // 2. 查询用户是否存在
        TUser user = this.getById(id);
        if (user == null) {
            return false; // 用户不存在，操作失败
        }

        // 3. 显式设置状态；同值请求幂等成功，绝不反向切换。
        boolean wasDisabled = Boolean.TRUE.equals(user.getStatus());
        if (wasDisabled == disabled) {
            return true;
        }
        if (disabled) {
            validateAccountDisable(user, true);
        }
        user.setStatus(disabled);

        // 4. 调用MP的updateById更新（自动维护updateTime）
        boolean updated = this.updateById(user);
        if (updated && !wasDisabled && Boolean.TRUE.equals(user.getStatus())) {
            releaseClaimedExpertTasks(user.getUserId());
        }
        return updated;
    }

    private void validateAccountDisable(TUser user, boolean targetDisabled) {
        if (!targetDisabled || user == null || Boolean.TRUE.equals(user.getStatus())) {
            return;
        }
        AuthUserEntity actor = authService.queryValidatedUser();
        if (actor != null && Objects.equals(actor.getUserId(), user.getUserId())) {
            throw new AppException(
                    ResponseCode.FORBIDDEN.getCode(),
                    "禁止禁用当前登录账号");
        }
        adminUserRoleMapService.assertCanDisableUser(user.getUserId());
    }

    private void releaseClaimedExpertTasks(Long expertUserId) {
        if (expertUserId == null) {
            return;
        }
        adminCopywritingApprovalFlowMapper.update(null, new LambdaUpdateWrapper<TCopywritingApprovalFlow>()
                .set(TCopywritingApprovalFlow::getCurrentProcessorId, null)
                .set(TCopywritingApprovalFlow::getUpdateTime, LocalDateTime.now())
                .eq(TCopywritingApprovalFlow::getCurrentStatus, ApprovalStatusEnumVO.EXPERT_PENDING.getCode())
                .eq(TCopywritingApprovalFlow::getCurrentProcessorId, expertUserId));
        adminCopywritingApprovalFlowMapper.update(null, new LambdaUpdateWrapper<TCopywritingApprovalFlow>()
                .set(TCopywritingApprovalFlow::getCurrentProcessorId, null)
                .set(TCopywritingApprovalFlow::getUpdateTime, LocalDateTime.now())
                .eq(TCopywritingApprovalFlow::getCurrentStatus, ApprovalStatusEnumVO.PLATFORM_PENDING.getCode())
                .eq(TCopywritingApprovalFlow::getCurrentProcessorId, expertUserId));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updatePassword(UpdatePasswordCommandEntity command) {
        // 1. 提取参数并校验（非空、格式）
        String phoneNumber = command.getPhoneNumber();
        String newPassword = command.getPassword();
        String verificationCode = command.getCode();

        // 1.1 校验手机号
        if (!StringUtils.hasText(phoneNumber)) {
            throw new AppException("PHONE_NULL", "手机号码不能为空");
        }
        if (!phoneNumber.matches("^1[3-9]\\d{9}$")) { // 匹配11位手机号格式
            throw new AppException("PHONE_FORMAT_ERROR", "手机号码格式不正确");
        }

        // 1.2 校验验证码
        if (!StringUtils.hasText(verificationCode)) {
            throw new AppException("CODE_NULL", "验证码不能为空");
        }

        // 1.3 校验新密码（长度6-20位，可根据业务调整）
        if (!StringUtils.hasText(newPassword)) {
            throw new AppException("PASSWORD_NULL", "新密码不能为空");
        }
        if (newPassword.length() < 6 || newPassword.length() > 20) {
            throw new AppException("PASSWORD_LENGTH_ERROR", "密码长度必须在6-20位之间");
        }

        try {
            // 2. 复用登录校验逻辑：验证手机号与验证码是否匹配（核心复用）
            // 注：authService.authUserByVerificationCode 已实现「验证码有效性校验」（过期、错误会抛异常）
            AuthUserAggregate authUserAggregate = authService.authUserByVerificationCode(phoneNumber, verificationCode);
            AuthUserEntity authUser = authUserAggregate.getAuthUserEntity();
            log.info("用户[业务ID: {}]验证码校验通过，开始修改密码", authUser.getUserId());

            // 3. 根据手机号查询 TUser 实体（确保用户存在且账号正常）
            LambdaQueryWrapper<TUser> userWrapper = new LambdaQueryWrapper<>();
            userWrapper.eq(TUser::getPhoneNumber, phoneNumber) // 手机号精准匹配
                    .eq(TUser::getStatus, 0); // 仅允许正常状态（status=0）的用户修改
            TUser user = this.getOne(userWrapper);
            if (user == null) {
                throw new AppException("USER_NOT_FOUND", "用户不存在或账号已禁用，无法修改密码");
            }

            // 4. 密码加密（避免明文存储，与登录密码加密逻辑一致）
            String encryptedPassword = passwordEncoder.encode(newPassword);

            // 5. 更新用户密码和修改时间
            user.setPassword(encryptedPassword);
            user.setUpdateTime(LocalDateTime.now()); // 手动更新时间（无自动填充）

            // 6. 数据库更新（根据业务userId作为条件，复用之前的可靠逻辑）
            boolean updateSuccess = this.updateByUserId(authUser.getUserId(), user);
            if (!updateSuccess) {
                throw new AppException("DB_UPDATE_FAILED", "密码更新失败，请重试");
            }

            log.info("用户[业务ID: {}]密码修改成功", authUser.getUserId());

        } catch (AppException e) {
            // 捕获业务异常（如验证码错误、用户不存在等，直接抛出）
            log.error("密码修改业务异常: code={}, type={}",
                    controlledExceptionCode(e), exceptionType(e));
            throw e;
        } catch (Exception e) {
            // 捕获系统异常（如数据库报错、加密异常等）
            log.error("密码修改系统异常: code={}, type={}",
                    ResponseCode.SYSTEM_ERROR.getCode(), exceptionType(e));
            throw new AppException("SYSTEM_ERROR", "系统异常，密码修改失败");
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void changePasswordByOld(Long userId, ChangePasswordByOldRequestDTO dto) {
        // 1. 提取参数并校验
        String currentPassword = dto.getCurrentPassword();
        String newPassword = dto.getNewPassword();

        // 2. 查询用户
        Optional<TUser> userOptional = getUserByBusinessUserId(userId);
        if (userOptional.isEmpty()) {
            throw new AppException("USER_NOT_FOUND", "用户不存在，无法修改密码");
        }
        TUser user = userOptional.get();

        // 3. 验证旧密码是否正确
        if (!passwordEncoder.matches(currentPassword, user.getPassword())) {
            throw new AppException("CURRENT_PASSWORD_ERROR", "当前密码错误");
        }

        // 4. 校验新密码复杂度（长度、字符组合、与旧密码不同）
        if (!isValidNewPassword(newPassword, user.getPassword())) {
            throw new AppException("NEW_PASSWORD_INVALID", "新密码需满足：至少8个字符，包含大小写字母、数字和特殊字符，且不能与旧密码相同");
        }

        // 5. 加密新密码并更新用户
        String encryptedPassword = passwordEncoder.encode(newPassword);
        user.setPassword(encryptedPassword);
        user.setUpdateTime(LocalDateTime.now()); // 手动更新修改时间

        // 6. 数据库更新（复用已有updateByUserId方法）
        boolean updateSuccess = updateByUserId(userId, user);
        if (!updateSuccess) {
            throw new AppException("DB_UPDATE_FAILED", "密码更新失败，请重试");
        }

        log.info("用户[业务ID: {}]密码修改成功", userId);
    }

    /**
     * 校验新密码复杂度
     * - 长度≥8
     * - 包含大写字母、小写字母、数字、特殊字符（@$!%*?&）
     * - 不能与旧密码（加密后）相同
     */
    private boolean isValidNewPassword(String newPassword, String oldEncryptedPassword) {
        // 长度校验
        if (newPassword.length() < 8) {
            return false;
        }
        // 字符组合校验（正则匹配大小写字母、数字、特殊字符）
        if (!newPassword.matches("^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[@$!%*?&])[A-Za-z\\d@$!%*?&]{8,}$")) {
            return false;
        }
        // 与旧密码（加密后）比对
        return !passwordEncoder.matches(newPassword, oldEncryptedPassword);
    }

    private static String controlledExceptionCode(AppException exception) {
        if (exception == null || exception.getCode() == null) {
            return "UNMAPPED";
        }
        return Arrays.stream(ResponseCode.values())
                .map(ResponseCode::getCode)
                .filter(exception.getCode()::equals)
                .findFirst()
                .orElse("UNMAPPED");
    }

    private static String exceptionType(Throwable exception) {
        if (exception == null) {
            return "Unknown";
        }
        String type = exception.getClass().getSimpleName();
        return type == null || type.isBlank() ? "Unknown" : type;
    }

}

