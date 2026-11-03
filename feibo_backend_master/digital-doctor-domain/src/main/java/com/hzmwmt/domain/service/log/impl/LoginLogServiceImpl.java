package com.hzmwmt.domain.service.log.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hzmwmt.domain.entity.log.TLoginLog;
import com.hzmwmt.domain.mapper.log.LoginLogMapper;
import com.hzmwmt.domain.service.log.LoginLogService;
import com.hzmwmt.domain.service.log.support.LogNicknameEnricher;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * 用户登录日志服务实现类
 */
@Service
@RequiredArgsConstructor
public class LoginLogServiceImpl extends ServiceImpl<LoginLogMapper, TLoginLog> implements LoginLogService {

    private final LogNicknameEnricher logNicknameEnricher;

    @Override
    public Page<TLoginLog> pageQuery(Integer pageNum, Integer pageSize, String loginUser,
                                     String loginIp, String loginStatus, String startTime, String endTime) {
        Page<TLoginLog> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<TLoginLog> queryWrapper = new LambdaQueryWrapper<>();

        // 按用户名模糊查询
        if (loginUser != null && !loginUser.isEmpty()) {
            queryWrapper.like(TLoginLog::getLoginUser, loginUser);
        }

        // 按IP模糊查询
        if (loginIp != null && !loginIp.isEmpty()) {
            queryWrapper.like(TLoginLog::getLoginIp, loginIp);
        }

        // 按登录状态查询
        if (loginStatus != null && !loginStatus.isEmpty()) {
            queryWrapper.eq(TLoginLog::getLoginStatus, loginStatus);
        }

        // 按时间范围查询
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        if (startTime != null && !startTime.isEmpty()) {
            try {
                Date startDate = format.parse(startTime);
                queryWrapper.ge(TLoginLog::getLoginTime, startDate);
            } catch (ParseException e) {
                // 时间格式错误，忽略该条件
            }
        }

        if (endTime != null && !endTime.isEmpty()) {
            try {
                Date endDate = format.parse(endTime);
                queryWrapper.le(TLoginLog::getLoginTime, endDate);
            } catch (ParseException e) {
                // 时间格式错误，忽略该条件
            }
        }

        // 按登录时间倒序排序
        queryWrapper.orderByDesc(TLoginLog::getLoginTime);

        Page<TLoginLog> result = baseMapper.selectPage(page, queryWrapper);
        logNicknameEnricher.enrichLoginLogs(result.getRecords());
        return result;
    }
}
