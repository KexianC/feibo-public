package com.hzmwmt.domain.service.example;

import com.hzmwmt.types.annotations.LoginLog;
import org.springframework.stereotype.Service;

/**
 * 示例服务类
 * 用于展示OperationLog和LoginLog注解的使用方法
 *
 * @author AI Assistant
 * @version v1.0
 * @Date: 2025-11-05
 */
@Service
public class ExampleService {

    /**
     * 用户登录方法示例
     * 使用@LoginLog注解标记，会自动记录登录日志到登录日志表
     *
     * @param username 用户名
     * @param password 密码
     * @return 登录结果
     */
    @LoginLog(loginType = "密码登录", description = "用户通过密码方式登录系统")
    public boolean login(String username, String password) {
        // 模拟登录逻辑
        // 实际项目中这里会进行密码校验等操作
        if (username != null && !username.isEmpty() && password != null && !password.isEmpty()) {
            // 登录成功
            return true;
        }
        // 登录失败
        return false;
    }

    /**
     * 验证码登录示例
     */
    @LoginLog(loginType = "验证码登录", description = "用户通过验证码方式登录系统")
    public boolean loginWithCode(String phone, String code) {
        // 模拟验证码登录逻辑
        return phone != null && code != null && code.equals("123456");
    }

    /**
     * 新增数据示例
     */
    public boolean addData(Object data) {
        // 模拟新增操作
        return true;
    }

    /**
     * 更新数据示例
     */
    public boolean updateData(Long id, Object data) {
        // 模拟更新操作
        return true;
    }

    /**
     * 删除数据示例
     */
    public boolean deleteData(Long id) {
        // 模拟删除操作
        return true;
    }

    /**
     * 查询数据示例
     */
    public Object queryData(Long id) {
        // 模拟查询操作
        return new Object();
    }
}