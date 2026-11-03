package com.hzmwmt.domain.service.user.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hzmwmt.domain.entity.user.TUser;
import com.hzmwmt.domain.mapper.user.AdminUserMapper;
import com.hzmwmt.domain.service.user.UserSignService;
import com.hzmwmt.types.enums.ResponseCode;
import com.hzmwmt.types.exception.AppException;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RBitSet;
import org.redisson.api.RScoredSortedSet;
import org.redisson.api.RedissonClient;
import org.redisson.client.protocol.ScoredEntry;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.TimeUnit;

@Service
@Slf4j
public class UserSignServiceImpl extends ServiceImpl<AdminUserMapper, TUser> implements UserSignService {

    @Resource
    private RedissonClient redissonClient;


//    redis key:
//    签到状态：sign:{userId}:yyyyMM
//    排行榜：sign:rank:month:yyyyMM

    @Override
    public Boolean checkSign(Long userId) {
        requireUserId(userId);
        LocalDateTime now = LocalDateTime.now();
        String keySuffix = now.format(DateTimeFormatter.ofPattern(":yyyyMM"));

        String signKey = "sign:" + userId + keySuffix;

        int dayOfMonth = now.getDayOfMonth();
        RBitSet bitSet = redissonClient.getBitSet(signKey);
        if (bitSet.get(dayOfMonth - 1)) {
            log.info("已签到");
            return false; // 已签到
        } else {
            log.info("未签到");
            return true; // 未签到
        }
    }

    @Override
    public void sign(Long userId) {
        requireUserId(userId);
        LocalDateTime now = LocalDateTime.now();
        String keySuffix = now.format(DateTimeFormatter.ofPattern(":yyyyMM"));

        String signKey = "sign:" + userId + keySuffix;
        String rankKey = "sign:rank:month" + keySuffix;

        int dayOfMonth = now.getDayOfMonth();

        RBitSet bitSet = redissonClient.getBitSet(signKey);

        // Redis SETBIT returns the previous value, so concurrent requests can
        // increment the ranking at most once for the same user/day.
        if (bitSet.set(dayOfMonth - 1, true)) {
            log.info("已签到");
            throw new AppException(ResponseCode.ILLEGAL_PARAMETER);
        }
        RScoredSortedSet<String> rankSet =
                redissonClient.getScoredSortedSet(rankKey);
        rankSet.addScore(String.valueOf(userId), 1);

        log.info("用户签到，{}，日期{}", userId, dayOfMonth);

        // 设置过期时间（防止 Redis 无限增长）
        bitSet.expire(35 - dayOfMonth, TimeUnit.DAYS);
        rankSet.expire(35 - dayOfMonth, TimeUnit.DAYS);
    }


    @Override
    public List<Map<String, Object>> rank() {
        LocalDateTime now = LocalDateTime.now();
        String keySuffix = now.format(DateTimeFormatter.ofPattern(":yyyyMM"));
        String rankKey = "sign:rank:month" + keySuffix;

        RScoredSortedSet<String> rankSet =
                redissonClient.getScoredSortedSet(rankKey);

        Collection<ScoredEntry<String>> entries =
                rankSet.entryRangeReversed(0, 9);

        List<Map<String, Object>> result = new ArrayList<>();

        for (ScoredEntry<String> entry : entries) {
            Map<String, Object> map = new HashMap<>();

            long userId = Long.parseLong(entry.getValue());
            QueryWrapper<TUser> queryWrapper = new QueryWrapper<>();
            queryWrapper.eq("user_id", userId);
            TUser user = baseMapper.selectOne(queryWrapper);
            map.put("nickName", user == null ? "已注销用户" : user.getNickName());

            map.put("days", entry.getScore().intValue());
            result.add(map);
        }

        return result;
    }

    private void requireUserId(Long userId) {
        if (userId == null) {
            throw new AppException(ResponseCode.E0001);
        }
    }

}
