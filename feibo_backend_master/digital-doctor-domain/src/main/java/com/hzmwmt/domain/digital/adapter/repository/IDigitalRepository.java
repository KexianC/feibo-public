package com.hzmwmt.domain.digital.adapter.repository;

import com.hzmwmt.domain.digital.model.aggregate.CreateDigitalVoiceAggregate;
import com.hzmwmt.domain.digital.model.aggregate.DigitalVoiceAggregate;
import com.hzmwmt.domain.digital.model.entity.*;
import com.hzmwmt.domain.digital.model.valobj.DigitalVoiceCategoryTypeEnumVO;
import com.hzmwmt.domain.digital.model.valobj.VoiceGenderEnumVO;
import com.hzmwmt.types.common.CursorPageResponseData;

import java.util.List;

/**
 * @author huihui
 * @version v1.0
 * @Description: TODO
 * @ClassName IDigitalRepository
 * @Date: 2025/8/18 0:16
 */
public interface IDigitalRepository {
    // --- Voice ---
    CursorPageResponseData<DigitalVoiceAggregate> queryVoiceIndex(Long lastCursorId, Integer size, Long userId);

    CursorPageResponseData<DigitalVoiceAggregate> advancedVoiceQuery(
            Long lastCursorId,
            Integer size,
            VoiceGenderEnumVO voiceGenderEnumVO,
            String name,
            Long userId,
            List<Long> digitalVoiceCategoryIdList);

    CursorPageResponseData<DigitalVoiceEntity> queryVoiceCollectedIndex(Long lastCursorId, Integer size, Long userId);

    List<DigitalVoiceCategoryEntity> queryDigitalVoiceCategory(DigitalVoiceCategoryTypeEnumVO typeEnumVO);

    void updateVoiceFavoriteStatus(Long userId, Long voiceId, boolean collected);

    void saveVoice(CreateDigitalVoiceAggregate aggregate);

    // --- Audio ---
    CursorPageResponseData<DigitalAudioEntity> queryUserMusicIndex(Long userId, Long lastCursorId, Integer size);

    CursorPageResponseData<DigitalAudioEntity> queryUserRecordingIndex(Long userId, Long lastCursorId, Integer size);

    CursorPageResponseData<DigitalAudioEntity> querySysMusicIndex(Long lastCursorId, Integer size);

    void saveDigitalAudio(List<DigitalAudioEntity> musicEntityList);

    // --- Video ---
    CursorPageResponseData<DigitalVideoEntity> queryUserVideoIndex(Long userId, Long lastCursorId, Integer size);

    CursorPageResponseData<DigitalVideoEntity> querySysVideoIndex(Long lastCursorId, Integer size);

    void saveDigitalVideo(List<DigitalVideoEntity> videoEntityList);

    // --- Image ---
    CursorPageResponseData<DigitalImageEntity> queryUserBackgroundImageIndex(Long userId, Long lastCursorId, Integer size);

    CursorPageResponseData<DigitalImageEntity> queryUserIllustrationImageIndex(Long userId, Long lastCursorId, Integer size);

    CursorPageResponseData<DigitalImageEntity> querySysBackgroundImageIndex(Long lastCursorId, Integer size);

    CursorPageResponseData<DigitalImageEntity> querySysIllustrationImageIndex(Long lastCursorId, Integer size);

    void saveDigitalImage(List<DigitalImageEntity> imageEntityList);

    // --- Model ---
    CursorPageResponseData<DigitalModelEntity> queryDigitalModelIndex(Long userId, Long lastCursorId, Integer size);

    // --- Work ---
    Long deductUserAccountQuota(VideoCompositionEntity videoCompositionEntity);

    void saveDigitalWork(DigitalWorkEntity digitalWorkEntity);
    Integer queryOwnedAudioDuration(Long userId, String url);
    DigitalWorkEntity findDigitalWorkByQuotaKey(Long userId, String quotaKey);
    void bindDigitalWorkSubmission(Long userId, String quotaKey, VideoCompositionSubmissionEntity submission);
    void markDigitalWorkSubmissionRejected(Long userId, String quotaKey);

    int deleteDigitalWork(DigitalWorkEntity digitalWorkEntity);

    int updateDigitalWorkTitle(DigitalWorkEntity digitalWorkEntity);

    CursorPageResponseData<DigitalWorkEntity> queryDigitalWorkIndex(Long userId, Long lastCursorId, Integer size);
    //template

    CursorPageResponseData<DigitalTemplateEntity> queryUserTemplateIndex(Long userId, Long lastCursorId, Integer size);

    CursorPageResponseData<DigitalTemplateEntity> querySysTemplateIndex(Long lastCursorId, Integer size);

    void createUserDigitalTemplate(DigitalTemplateEntity entity);

    int deleteDigitalTemplate(DigitalTemplateEntity entity);

    int updateDigitalTemplateTitle(DigitalTemplateEntity entity);
}
