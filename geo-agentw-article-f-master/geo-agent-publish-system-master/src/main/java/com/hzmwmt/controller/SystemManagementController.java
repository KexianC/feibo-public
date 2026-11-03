package com.hzmwmt.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hzmwmt.common.context.LoginContextHolder;
import com.hzmwmt.common.exception.AppException;
import com.hzmwmt.common.response.PageResult;
import com.hzmwmt.common.response.Result;
import com.hzmwmt.config.AppSecurityProperties;
import com.hzmwmt.constant.LoginConstants;
import com.hzmwmt.geo.config.GeoProperties;
import com.hzmwmt.mapper.SystemOperationLogMapper;
import com.hzmwmt.mapper.SystemSettingMapper;
import com.hzmwmt.mapper.SystemUserMapper;
import com.hzmwmt.model.dto.RuntimeSettingResponse;
import com.hzmwmt.model.dto.SystemUserPasswordRequest;
import com.hzmwmt.model.dto.SystemUserRequest;
import com.hzmwmt.model.entity.SystemOperationLog;
import com.hzmwmt.model.entity.SystemSetting;
import com.hzmwmt.model.entity.SystemUser;
import com.hzmwmt.util.SnowflakeIdGenerator;
import jakarta.annotation.Resource;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/system")
@ConditionalOnProperty(prefix = "app.geo-integration", name = "enabled", havingValue = "false")
public class SystemManagementController {

    @Resource
    private SystemUserMapper systemUserMapper;

    @Resource
    private SystemSettingMapper systemSettingMapper;

    @Resource
    private SystemOperationLogMapper operationLogMapper;

    @Resource
    private AppSecurityProperties securityProperties;

    @Resource
    private GeoProperties geoProperties;

    @Resource
    private SnowflakeIdGenerator snowflakeIdGenerator;

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    @GetMapping("/users/page")
    public PageResult<SystemUser> pageUsers(
            @RequestParam(value = "pageNum", defaultValue = "1") Long pageNum,
            @RequestParam(value = "pageSize", defaultValue = "20") Long pageSize,
            @RequestParam(value = "username", required = false) String username,
            @RequestParam(value = "role", required = false) String role,
            @RequestParam(value = "enableStatus", required = false) Integer enableStatus) {
        assertAdmin();
        assertPage(pageNum, pageSize);
        LambdaQueryWrapper<SystemUser> wrapper = new LambdaQueryWrapper<>();
        wrapper.like(StringUtils.hasText(username), SystemUser::getUsername, username);
        wrapper.eq(StringUtils.hasText(role), SystemUser::getRole, role);
        wrapper.eq(enableStatus != null, SystemUser::getEnableStatus, enableStatus);
        wrapper.orderByDesc(SystemUser::getCreatedTime);
        return PageResult.of(systemUserMapper.selectPage(new Page<>(pageNum, pageSize), wrapper));
    }

    @PostMapping("/users")
    public Result<SystemUser> addUser(@RequestBody SystemUserRequest request) {
        assertAdmin();
        if (request == null || !StringUtils.hasText(request.getUsername()) || !StringUtils.hasText(request.getPassword())) {
            throw new AppException("PARAM_INVALID", "用户名和密码不能为空");
        }
        Long exists = systemUserMapper.selectCount(new LambdaQueryWrapper<SystemUser>()
                .eq(SystemUser::getUsername, request.getUsername().trim()));
        if (exists != null && exists > 0) {
            throw new AppException("USER_EXISTS", "用户名已存在");
        }
        SystemUser user = SystemUser.builder()
                .id(snowflakeIdGenerator.nextId())
                .username(request.getUsername().trim())
                .displayName(StringUtils.hasText(request.getDisplayName()) ? request.getDisplayName().trim() : request.getUsername().trim())
                .role(normalizeRole(request.getRole()))
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .enableStatus(request.getEnableStatus() == null ? 1 : request.getEnableStatus())
                .build();
        systemUserMapper.insert(user);
        return Result.success(user);
    }

    @PostMapping("/users/update")
    public Result<Void> updateUser(@RequestBody SystemUserRequest request) {
        assertAdmin();
        if (request == null || request.getId() == null) {
            throw new AppException("PARAM_INVALID", "用户ID不能为空");
        }
        SystemUser update = new SystemUser();
        update.setId(request.getId());
        update.setDisplayName(request.getDisplayName());
        update.setRole(normalizeRole(request.getRole()));
        update.setEnableStatus(request.getEnableStatus());
        systemUserMapper.updateById(update);
        return Result.success();
    }

    @PostMapping("/users/resetPassword")
    public Result<Void> resetPassword(@RequestBody SystemUserPasswordRequest request) {
        assertAdmin();
        if (request == null || request.getId() == null || !StringUtils.hasText(request.getPassword())) {
            throw new AppException("PARAM_INVALID", "用户ID和新密码不能为空");
        }
        SystemUser update = new SystemUser();
        update.setId(request.getId());
        update.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        systemUserMapper.updateById(update);
        return Result.success();
    }

    @PostMapping("/users/toggle")
    public Result<Void> toggleUser(@RequestParam("id") Long id, @RequestParam("enableStatus") Integer enableStatus) {
        assertAdmin();
        SystemUser update = new SystemUser();
        update.setId(id);
        update.setEnableStatus(enableStatus == null || enableStatus != 0 ? 1 : 0);
        systemUserMapper.updateById(update);
        return Result.success();
    }

    @GetMapping("/settings/page")
    public PageResult<SystemSetting> pageSettings(
            @RequestParam(value = "pageNum", defaultValue = "1") Long pageNum,
            @RequestParam(value = "pageSize", defaultValue = "20") Long pageSize,
            @RequestParam(value = "settingKey", required = false) String settingKey,
            @RequestParam(value = "settingGroup", required = false) String settingGroup) {
        assertAdmin();
        assertPage(pageNum, pageSize);
        LambdaQueryWrapper<SystemSetting> wrapper = new LambdaQueryWrapper<>();
        wrapper.like(StringUtils.hasText(settingKey), SystemSetting::getSettingKey, settingKey);
        wrapper.eq(StringUtils.hasText(settingGroup), SystemSetting::getSettingGroup, settingGroup);
        wrapper.orderByAsc(SystemSetting::getSettingGroup).orderByAsc(SystemSetting::getSettingKey);
        return PageResult.of(systemSettingMapper.selectPage(new Page<>(pageNum, pageSize), wrapper));
    }

    @PostMapping("/settings")
    public Result<SystemSetting> saveSetting(@RequestBody SystemSetting setting) {
        assertAdmin();
        if (setting == null || !StringUtils.hasText(setting.getSettingKey())) {
            throw new AppException("PARAM_INVALID", "设置键不能为空");
        }
        SystemSetting existing = systemSettingMapper.selectOne(new LambdaQueryWrapper<SystemSetting>()
                .eq(SystemSetting::getSettingKey, setting.getSettingKey().trim())
                .last("LIMIT 1"));
        if (existing == null) {
            setting.setId(snowflakeIdGenerator.nextId());
            setting.setSettingKey(setting.getSettingKey().trim());
            setting.setSensitive(setting.getSensitive() == null ? 0 : setting.getSensitive());
            systemSettingMapper.insert(setting);
            return Result.success(setting);
        }
        setting.setId(existing.getId());
        systemSettingMapper.updateById(setting);
        return Result.success(setting);
    }

    @GetMapping("/settings/runtime")
    public Result<List<RuntimeSettingResponse>> runtimeSettings() {
        assertAdmin();
        List<RuntimeSettingResponse> settings = new ArrayList<>();
        settings.add(new RuntimeSettingResponse("会话超时", securityProperties.getSessionTimeoutMinutes() + " 分钟", "安全", false));
        settings.add(new RuntimeSettingResponse("记住我时长", securityProperties.getRememberMeDays() + " 天", "安全", false));
        settings.add(new RuntimeSettingResponse("登录失败锁定阈值", String.valueOf(securityProperties.getLoginMaxAttempts()), "安全", false));
        settings.add(new RuntimeSettingResponse("最大上传大小", securityProperties.getMaxUploadBytes() + " bytes", "文件", false));
        settings.add(new RuntimeSettingResponse("GEO定时监测", geoProperties.isMonitorEnabled() ? "启用" : "停用", "GEO", false));
        settings.add(new RuntimeSettingResponse("GEO批量大小", String.valueOf(geoProperties.getMonitorBatchSize()), "GEO", false));
        settings.add(new RuntimeSettingResponse("GEO引擎数量", String.valueOf(geoProperties.getEngines().size()), "GEO", false));
        geoProperties.getEngines().forEach(engine ->
                settings.add(new RuntimeSettingResponse("GEO引擎:" + engine.getName(), engine.getModel(), "GEO", false)));
        return Result.success(settings);
    }

    @GetMapping("/operationLogs/page")
    public PageResult<SystemOperationLog> pageOperationLogs(
            @RequestParam(value = "pageNum", defaultValue = "1") Long pageNum,
            @RequestParam(value = "pageSize", defaultValue = "20") Long pageSize,
            @RequestParam(value = "operatorName", required = false) String operatorName,
            @RequestParam(value = "moduleName", required = false) String moduleName,
            @RequestParam(value = "actionName", required = false) String actionName,
            @RequestParam(value = "status", required = false) Integer status) {
        assertAdmin();
        assertPage(pageNum, pageSize);
        LambdaQueryWrapper<SystemOperationLog> wrapper = new LambdaQueryWrapper<>();
        wrapper.like(StringUtils.hasText(operatorName), SystemOperationLog::getOperatorName, operatorName);
        wrapper.eq(StringUtils.hasText(moduleName), SystemOperationLog::getModuleName, moduleName);
        wrapper.eq(StringUtils.hasText(actionName), SystemOperationLog::getActionName, actionName);
        wrapper.eq(status != null, SystemOperationLog::getStatus, status);
        wrapper.orderByDesc(SystemOperationLog::getCreatedTime);
        IPage<SystemOperationLog> result = operationLogMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
        return PageResult.of(result);
    }

    private void assertAdmin() {
        if (!LoginContextHolder.isAdmin()) {
            throw new AppException("FORBIDDEN", "Admin permission required.", HttpStatus.FORBIDDEN);
        }
    }

    private void assertPage(Long pageNum, Long pageSize) {
        if (pageNum == null || pageNum <= 0 || pageSize == null || pageSize <= 0 || pageSize > 100) {
            throw new AppException("PARAM_INVALID", "分页参数不合法");
        }
    }

    private String normalizeRole(String role) {
        if (LoginConstants.ROLE_ADMIN.equalsIgnoreCase(role)) {
            return LoginConstants.ROLE_ADMIN;
        }
        return LoginConstants.ROLE_OPERATOR;
    }
}
