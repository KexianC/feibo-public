package com.hzmwmt.infrastructure.adapter.port;

import com.hzmwmt.domain.dto.UploadDTO;
import com.hzmwmt.domain.gateway.FileStorageGateway;
import com.hzmwmt.domain.user.adapter.port.IUserPort;
import com.hzmwmt.domain.user.model.entity.AIQuotaEntity;
import com.hzmwmt.domain.user.model.entity.UpdateAvatarCommandEntity;
import com.hzmwmt.infrastructure.gateway.sms.ISMSGateway;
import com.hzmwmt.infrastructure.redis.IRedisService;
import com.hzmwmt.types.enums.ResponseCode;
import com.hzmwmt.types.exception.AppException;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.RandomStringUtils;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.concurrent.TimeUnit;

import static com.hzmwmt.types.common.Constants.RedisConstants.KEY_AI_WRITE_LIST_PREFIX;
import static com.hzmwmt.types.common.Constants.RedisConstants.KEY_UPDATE_CODE_PREFIX;

/**
 * @author huihui
 * @version v1.0
 * @Description: TODO
 * @ClassName UserPort
 * @Date: 2025/9/17 12:21
 */
@Slf4j
@Service
public class UserPort implements IUserPort {
    @Resource
    private IRedisService redissonService;
    @Resource
    private ISMSGateway smsGateway;
    @Resource(name = "hybridOSSGateway")
    private FileStorageGateway ossGateWay;


    @Override
    public void sendUpdateShortMessage(String phoneNumber) {
        String code = RandomStringUtils.randomNumeric(5);
        String content = String.format("{\"code\":\"%s\"}", code);
        smsGateway.sendShortMessage(phoneNumber, content);
        redissonService.setValue(KEY_UPDATE_CODE_PREFIX + phoneNumber, code, 5);
    }

    @Override
    public void verifyUpdateVerificationCode(String phoneNumber, String code) {
        String key = KEY_UPDATE_CODE_PREFIX + phoneNumber;
        String redisCode = redissonService.getValue(key);
        if (redisCode == null) {
            log.warn("验证码已过期");
            throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), "验证码已过期请重新发送");
        }
        if (!code.equals(redisCode)) {
            log.warn("验证码错误");
            throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), "验证码错误");
        }
        redissonService.remove(key);
    }

    @Override
    public void uploadAvatar(UpdateAvatarCommandEntity updateAvatarCommandEntity) {
        MultipartFile avatar = updateAvatarCommandEntity.getAvatar();
        Long userId = updateAvatarCommandEntity.getUserId();
        String originalFilename = avatar.getOriginalFilename();
        String fileExtension = originalFilename.substring(originalFilename.lastIndexOf("."));

        String path = "avatar/" + userId + fileExtension;
        String url = ossGateWay.upload(
                UploadDTO.builder()
                        .file(avatar)
                        .path(path)
                        .build()
        );

        updateAvatarCommandEntity.setUrl(url);
    }

    @Override
    public void receiveAiQuota(String userId) {
        long durationMillis = TimeUnit.DAYS.toMillis(7);
        double expireTimestamp = System.currentTimeMillis() + durationMillis;
        if (!redissonService.tryAddToScoredSet(KEY_AI_WRITE_LIST_PREFIX, userId, expireTimestamp)) {
            log.info("用户{}已领取过配额", userId);
            throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), "请勿重新领取内测名额");
        }
    }

    @Override
    public AIQuotaEntity queryAiQuota(String userId) {

        Double expireTimestamp = redissonService.getScoreFromScoredSet(KEY_AI_WRITE_LIST_PREFIX, userId);

        // 如果时间戳为 null，说明从未领取过
        if (expireTimestamp == null) {
            return AIQuotaEntity.builder()
                    .isReceived(false)
                    .expireTime(null)
                    .build();
        }

        // 如果领取过，就在业务层计算剩余时间
        long remainingMillis = expireTimestamp.longValue() - System.currentTimeMillis();
        String expireTime;
        if (remainingMillis <= 0) {
            expireTime = "已过期";
        } else {
            long remainingDays = remainingMillis / (24 * 60 * 60 * 1000);
            if (remainingDays == 0) {
                expireTime = "不足1天";
            } else {
                expireTime = "还剩 " + remainingDays + " 天";
            }
        }

        return AIQuotaEntity.builder()
                .isReceived(true)
                .expireTime(expireTime)
                .build();
    }


}
