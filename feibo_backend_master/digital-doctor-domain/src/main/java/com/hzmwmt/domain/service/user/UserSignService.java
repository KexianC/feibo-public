package com.hzmwmt.domain.service.user;

import com.baomidou.mybatisplus.extension.service.IService;
import com.hzmwmt.domain.entity.user.TUser;

import java.util.List;
import java.util.Map;

public interface UserSignService extends IService<TUser> {

    /**
     * 检查今日是否需要签到
     *
     * @return true=需要签到，false=不需要签到
     */
    Boolean checkSign(Long userId);

    /**
     * 签到
     */
    void sign(Long userId);

    /****
     * 签到排名
     * @return 排名结果
     */
    List<Map<String, Object>> rank();
}

