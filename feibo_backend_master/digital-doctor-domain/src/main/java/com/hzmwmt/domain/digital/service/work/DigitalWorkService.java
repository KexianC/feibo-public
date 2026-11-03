package com.hzmwmt.domain.digital.service.work;

import com.hzmwmt.domain.digital.adapter.port.IDigitalPort;
import com.hzmwmt.domain.digital.adapter.repository.IDigitalRepository;
import com.hzmwmt.domain.digital.model.entity.DigitalWorkEntity;
import com.hzmwmt.domain.digital.model.entity.VideoCompositionEntity;
import com.hzmwmt.domain.digital.model.entity.VideoCompositionSubmissionEntity;
import com.hzmwmt.domain.digital.service.IDigitalWorkService;
import com.hzmwmt.domain.entitlement.IEntitlementService;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.hzmwmt.types.common.CursorPageResponseData;
import com.hzmwmt.types.enums.ResponseCode;
import com.hzmwmt.types.exception.AppException;
import jakarta.annotation.Resource;
import org.apache.commons.codec.digest.DigestUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.UUID;

/**
 * @author huihui
 * @version v1.0
 * @Description: TODO
 * @ClassName DigitalWorkService
 * @Date: 2025/9/24 17:22
 */
@Service
public class DigitalWorkService implements IDigitalWorkService {
    @Resource
    private IDigitalRepository repository;
    @Resource
    private IDigitalPort port;
    @Resource
    private IEntitlementService entitlementService;
    @Resource
    private PlatformTransactionManager transactionManager;

    @Override
    public String createVideoComposition(VideoCompositionEntity videoCompositionEntity) {
        Integer videoTime = videoCompositionEntity == null ? null : videoCompositionEntity.getVideoTime();
        if (videoTime == null || videoTime <= 0 || videoTime > 1440) {
            throw new AppException(
                    ResponseCode.ILLEGAL_PARAMETER.getCode(),
                    "videoTime must be between 1 and 1440"
            );
        }
        Long userId = videoCompositionEntity.getUserId();
        long seconds = DigitalVideoDuration.estimateSeconds(videoCompositionEntity.getBody(),
                url -> repository.queryOwnedAudioDuration(userId, url));
        JSONObject safePayload = JSON.parseObject(videoCompositionEntity.getBody());
        safePayload.remove("callbackUrl");
        String body = safePayload.toJSONString();
        String requestId = videoCompositionEntity.getRequestId();
        String quotaKey = "digital:" + DigestUtils.sha256Hex(requestId == null ? UUID.randomUUID().toString() : requestId);
        DigitalWorkEntity prior = repository.findDigitalWorkByQuotaKey(userId, quotaKey);
        if (prior != null) {
            if (prior.getExternalId() != null && !prior.getExternalId().startsWith("pending_")) return prior.getExternalId();
            throw new AppException(ResponseCode.REPEATED_OPERATION_EXCEPTION.getCode(),
                    "该合成请求已受理或结果待核查，请勿重复提交，联系管理员处理");
        }
        // The hold and durable placeholder are atomic, before any external side effect.
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            entitlementService.reserve(userId, "DIGITAL_HUMAN_SECONDS", seconds, quotaKey);
            repository.saveDigitalWork(DigitalWorkEntity.builder().userId(userId).body(body).quotaBizKey(quotaKey)
                    .externalId("pending_" + UUID.randomUUID().toString().replace("-", ""))
                    .captureSource("quota_reservation").build());
        });
        try {
            VideoCompositionSubmissionEntity submission = port.createVideoComposition(body);
            if (submission.getProviderRequestSha256() == null)
                submission.setProviderRequestSha256(DigestUtils.sha256Hex(submission.getProviderRequestBody()));
            repository.bindDigitalWorkSubmission(userId, quotaKey, submission);
            return submission.getExternalId();
        } catch (VideoCompositionRejectedException exception) {
            refundRejected(userId, quotaKey);
            throw exception;
        } catch (AppException exception) {
            if (ResponseCode.ILLEGAL_PARAMETER.getCode().equals(exception.getCode())) {
                refundRejected(userId, quotaKey);
            }
            throw exception;
        }
    }

    private void refundRejected(Long userId, String quotaKey) {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            repository.markDigitalWorkSubmissionRejected(userId, quotaKey);
            entitlementService.release(userId, quotaKey);
        });
    }

    @Override
    public void deleteDigitalWork(DigitalWorkEntity digitalWorkEntity) {
        int deleteCount = repository.deleteDigitalWork(digitalWorkEntity);
        if (deleteCount != 1) {
            throw new AppException(ResponseCode.AUTHORIZATION_EXCEPTION);
        }
    }

    @Override
    public void updateDigitalWorkTitle(DigitalWorkEntity entity) {
        int updateCount = repository.updateDigitalWorkTitle(entity);
        if (updateCount != 1) {
            throw new AppException(ResponseCode.AUTHORIZATION_EXCEPTION);
        }
    }

    @Override
    public CursorPageResponseData<DigitalWorkEntity> queryDigitalWorkIndex(Long userId, Long lastCursorId, Integer size) {
        return repository.queryDigitalWorkIndex(userId, lastCursorId, size);
    }
}
