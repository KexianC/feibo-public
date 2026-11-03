package com.hzmwmt.infrastructure.adapter.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.hzmwmt.domain.digital.adapter.repository.IDigitalRepository;
import com.hzmwmt.domain.digital.model.aggregate.CreateDigitalVoiceAggregate;
import com.hzmwmt.domain.digital.model.aggregate.DigitalVoiceAggregate;
import com.hzmwmt.domain.digital.model.entity.*;
import com.hzmwmt.domain.digital.model.valobj.DigitalModelGenderEnumVO;
import com.hzmwmt.domain.digital.model.valobj.DigitalVoiceCategoryTypeEnumVO;
import com.hzmwmt.domain.digital.model.valobj.VoiceGenderEnumVO;
import com.hzmwmt.domain.entity.user.TDigitalWorkComposeAudit;
import com.hzmwmt.domain.mapper.user.DigitalWorkComposeAuditMapper;
import com.hzmwmt.infrastructure.dao.*;
import com.hzmwmt.infrastructure.dao.po.*;
import com.hzmwmt.infrastructure.redis.IRedisService;
import com.hzmwmt.types.common.Constants;
import com.hzmwmt.types.common.CursorPageResponseData;
import com.hzmwmt.types.enums.ResourceUserTypeEnumVO;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import static com.hzmwmt.types.common.Constants.RedisConstants.KEY_VOICE_COLLECT_PREFIX;

/**
 * @author huihui
 * @version v1.0
 * @Description: TODO
 * @ClassName DigitalRepository
 * @Date: 2025/8/18 0:10
 */
@Repository
public class DigitalRepository extends AbstractRepository implements IDigitalRepository {
    @Resource
    private DigitalVoiceMapper digitalVoiceMapper;
    @Resource
    private IRedisService redissonService;
    @Resource
    private DigitalImageMapper imageMapper;
    @Resource
    private DigitalVideoMapper videoMapper;
    @Resource
    private DigitalAudioMapper audioMapper;
    @Resource
    private DigitalModelMapper digitalModelMapper;
    @Resource
    private UserAIResourceMapper userAiResourceMapper;
    @Resource
    private UserAIResourceDetailMapper userAiResourceDetailMapper;
    @Resource
    private DigitalWorkMapper digitalWorkMapper;
    @Resource
    private DigitalWorkComposeAuditMapper digitalWorkComposeAuditMapper;
    @Resource
    private ExpertAssistantMapMapper expertAssistantMapMapper;
    @Resource
    private DigitalTemplateMapper digitalTemplateMapper;
    @Resource
    private DigitalVoiceCategoryMapper digitalVoiceCategoryMapper;
    @Resource
    private DigitalVoiceCategoryMapMapper digitalVoiceCategoryMapMapper;

    @Override
    public CursorPageResponseData<DigitalVoiceAggregate> queryVoiceIndex(Long lastCursorId, Integer size, Long userId) {
        CursorPageResponseData<DigitalVoiceEntity> fromDbCursorPage = getFromDbCursorPage(() ->
                        digitalVoiceMapper.selectVoiceListCursorPage(lastCursorId, size + 1),
                DigitalVoice::getDigitalVoiceId,
                digitalVoice -> DigitalVoiceEntity.builder()
                        .voiceId(digitalVoice.getDigitalVoiceId())
                        .name(digitalVoice.getName())
                        .styleName(digitalVoice.getStyleName())
                        .voiceGenderEnumVO(VoiceGenderEnumVO.getByCode(digitalVoice.getGender()))
                        .build(),
                size);
        List<DigitalVoiceEntity> voiceList = fromDbCursorPage.getData();
        if (voiceList == null) {
            return CursorPageResponseData.<DigitalVoiceAggregate>builder()
                    .data(null)
                    .hasNext(false)
                    .nextCursorId(null)
                    .build();
        }

        List<Long> voiceIds = voiceList.stream().map(DigitalVoiceEntity::getVoiceId).toList();
        //收藏映射获取 不需要缓存重建 收藏要是最新的 缓存done了就done了
        Map<Long, Boolean> memberListInScoredSet = redissonService.isMemberListInScoredSet(KEY_VOICE_COLLECT_PREFIX + userId, voiceIds);
        //数据装填
        List<DigitalVoiceAggregate> aggregates = voiceList.stream().map(voiceEntity -> {
            DigitalVoiceAggregate build = DigitalVoiceAggregate.builder()
                    .voiceEntity(voiceEntity)
                    .build();
            if (memberListInScoredSet != null && !memberListInScoredSet.isEmpty()) {
                build.setIsCollected(memberListInScoredSet.getOrDefault(voiceEntity.getVoiceId(), false));
                ;
            }
            return build;
        }).toList();
        return CursorPageResponseData.<DigitalVoiceAggregate>builder()
                .nextCursorId(fromDbCursorPage.getNextCursorId())
                .hasNext(fromDbCursorPage.getHasNext())
                .data(aggregates)
                .build();
    }


    @Override
    public CursorPageResponseData<DigitalVoiceAggregate> advancedVoiceQuery(
            Long lastCursorId,
            Integer size,
            VoiceGenderEnumVO voiceGenderEnumVO,
            String name,
            Long userId,
            List<Long> digitalVoiceCategoryIdList) {

        List<Long> finalIds = executeDynamicFiltering(
                digitalVoiceCategoryIdList,
                (categoryId, currentIds) -> digitalVoiceCategoryMapMapper.selectDigitalVoiceIdsByDigitalVoiceCategoryIdInDigitalVoiceIds(categoryId, currentIds)
        );

        if (finalIds == null || finalIds.isEmpty()) {
            return CursorPageResponseData.<DigitalVoiceAggregate>builder()
                    .data(null)
                    .hasNext(false)
                    .nextCursorId(null)
                    .build();
        }

        // 根据最终ids，然后最终过滤+分页
        CursorPageResponseData<DigitalVoiceEntity> fromDbCursorPage =
                this.<DigitalVoice, DigitalVoiceEntity>getFromDbCursorPage(
                        () -> digitalVoiceMapper.selectVoiceListByCompoundQuery(
                                finalIds.stream().toList(),
                                lastCursorId,
                                voiceGenderEnumVO.getCode() != null ? (voiceGenderEnumVO.getCode() ? 1 : 0) : null,
                                name,
                                size + 1
                        ),
                        DigitalVoice::getDigitalVoiceId,
                        digitalVoice -> {
                            return DigitalVoiceEntity.builder()
                                    .voiceId(digitalVoice.getDigitalVoiceId())
                                    .name(digitalVoice.getName())
                                    .styleName(digitalVoice.getStyleName())
                                    .voiceGenderEnumVO(VoiceGenderEnumVO.getByCode(digitalVoice.getGender()))
                                    .build();
                        },
                        size
                );
        List<DigitalVoiceEntity> voiceList = fromDbCursorPage.getData();
        if (voiceList == null) {
            return CursorPageResponseData.<DigitalVoiceAggregate>builder()
                    .data(null)
                    .hasNext(false)
                    .nextCursorId(null)
                    .build();
        }
        List<Long> voiceIds = voiceList.stream().map(DigitalVoiceEntity::getVoiceId).toList();
        //收藏映射获取 不需要缓存重建 收藏要是最新的 缓存done了就done了
        Map<Long, Boolean> memberListInScoredSet = redissonService.isMemberListInScoredSet(KEY_VOICE_COLLECT_PREFIX + userId, voiceIds);
        //数据装填
        List<DigitalVoiceAggregate> aggregates = voiceList.stream().map(voiceEntity -> {
            DigitalVoiceAggregate build = DigitalVoiceAggregate.builder()
                    .voiceEntity(voiceEntity)
                    .build();
            if (memberListInScoredSet != null && !memberListInScoredSet.isEmpty()) {
                build.setIsCollected(memberListInScoredSet.getOrDefault(voiceEntity.getVoiceId(), false));
                ;
            }
            return build;
        }).toList();
        return CursorPageResponseData.<DigitalVoiceAggregate>builder()
                .nextCursorId(fromDbCursorPage.getNextCursorId())
                .hasNext(fromDbCursorPage.getHasNext())
                .data(aggregates)
                .build();
    }


    @Override
    public CursorPageResponseData<DigitalVoiceEntity> queryVoiceCollectedIndex(Long lastCursorId, Integer size, Long userId) {
        List<Long> voiceCollectedIds;
        CursorPageResponseData<DigitalVoiceEntity> response;
        //获取收藏表
        Map<Long, Double> collectedMap = redissonService.getMapFromScoredSet(KEY_VOICE_COLLECT_PREFIX + userId, (double) removeMachineBitsIntSnowflakeId(lastCursorId), size + 1);
        voiceCollectedIds = new ArrayList<>(collectedMap.keySet());

        //redis缓存填充数据
        boolean hasNext = voiceCollectedIds.size() > size;
        if (hasNext) {
            response = CursorPageResponseData.<DigitalVoiceEntity>builder()
                    .nextCursorId(collectedMap.get(voiceCollectedIds.get(size - 1)).longValue())
                    .build();
            voiceCollectedIds = voiceCollectedIds.subList(0, size);
        } else {
            response = CursorPageResponseData.<DigitalVoiceEntity>builder()
                    .nextCursorId(null)
                    .build();
        }
        response.setHasNext(hasNext);


        List<DigitalVoice> digitalVoiceList = digitalVoiceMapper.selectVoiceListByVoiceIds(voiceCollectedIds);
        //重新排序
        Map<Long, DigitalVoice> voiceMap = digitalVoiceList.stream().collect(Collectors.toMap(DigitalVoice::getDigitalVoiceId, digitalVoice -> digitalVoice));
        digitalVoiceList = voiceCollectedIds.stream().map(voiceMap::get).toList();
        List<DigitalVoiceEntity> aggregates = digitalVoiceList.stream().map(digitalVoice -> {
            Long voiceId = digitalVoice.getDigitalVoiceId();
            return DigitalVoiceEntity.builder()
                    .voiceId(voiceId)
                    .styleName(digitalVoice.getStyleName())
                    .name(digitalVoice.getName())
                    .build();
        }).toList();
        response.setData(aggregates);
        return response;
    }

    @Override
    public List<DigitalVoiceCategoryEntity> queryDigitalVoiceCategory(DigitalVoiceCategoryTypeEnumVO typeEnumVO) {
        List<DigitalVoiceCategory> digitalVoiceCategoryList = digitalVoiceCategoryMapper.selectDigitalVoiceCategoryByType(typeEnumVO.getCode());
        if (digitalVoiceCategoryList == null || digitalVoiceCategoryList.isEmpty()) {
            return null;
        }
        return digitalVoiceCategoryList.stream().map(
                digitalVoiceCategory -> {
                    return DigitalVoiceCategoryEntity.builder()
                            .digitalVoiceCategoryId(digitalVoiceCategory.getDigitalVoiceCategoryId())
                            .name(digitalVoiceCategory.getName())
                            .build();
                }).toList();
    }


    @Override
    public void updateVoiceFavoriteStatus(Long userId, Long voiceId, boolean collected) {
        String redisKey = Constants.RedisConstants.KEY_VOICE_COLLECT_PREFIX + userId;

//        if (collected) {
//            if (redissonService.isMemberInScoredSet(redisKey, voiceId)) {
//                return;
//            }
//            DigitalVoiceCollected digitalVoiceCollected = new DigitalVoiceCollected();
//            digitalVoiceCollected.setUserId(userId);
//            digitalVoiceCollected.setVoiceId(voiceId);
//            digitalVoiceCollectedMapper.insert(digitalVoiceCollected);
//            //保证精度
//            double score = removeMachineBitsIntSnowflakeId(digitalVoiceCollected.getVoiceCollectedId());
//            redissonService.addToScoredSet(redisKey, voiceId, score);
//
//        } else {
//            LambdaQueryWrapper<DigitalVoiceCollected> wrapper = new LambdaQueryWrapper<>();
//            wrapper.eq(DigitalVoiceCollected::getUserId, userId)
//                    .eq(DigitalVoiceCollected::getVoiceId, voiceId);
//            digitalVoiceCollectedMapper.delete(wrapper);
//            redissonService.deleteToScoredSet(redisKey, voiceId);
//        }
    }

    @Transactional(timeout = 500)
    @Override
    public void saveVoice(CreateDigitalVoiceAggregate aggregate) {
//        DigitalVoiceEntity voiceEntity = aggregate.getVideoEntity();
//        DigitalVoice build = DigitalVoice.builder()
//                .name(voiceEntity.getName())
//                .styleName(voiceEntity.getStyleName())
//                .image(voiceEntity.getImage())
//                .gender(voiceEntity.getVoiceGenderEnumVO().getCode())
//                .build();
//        digitalVoiceMapper.insert(build);
//        digitalVoiceDomainMapMapper.insert(
//                DigitalVoiceDomainMap.builder()
//                        .voiceId(build.getVoiceId())
//                        .voiceDomainCategoryId(aggregate.getDigitalVoiceDomainCategoryId())
//                        .build()
//        );
//        digitalVoiceAgeMapMapper.insert(
//                DigitalVoiceAgeMap.builder()
//                        .voiceId(build.getVoiceId())
//                        .voiceAgeCategoryId(aggregate.getDigitalVoiceAgeCategoryId())
//                        .build()
//        );
//        digitalVoiceLanguageMapMapper.insert(
//                DigitalVoiceLanguageMap.builder()
//                        .voiceId(build.getVoiceId())
//                        .voiceLanguageCategoryId(aggregate.getDigitalVoiceLanguageCategoryId())
//                        .build()
//        );
//        digitalVoiceStyleMapMapper.insert(
//                DigitalVoiceStyleMap.builder()
//                        .voiceId(build.getVoiceId())
//                        .voiceStyleCategoryId(aggregate.getDigitalVoiceStyleCategoryId())
//                        .build()
//        );
    }

    @Override
    public CursorPageResponseData<DigitalAudioEntity> queryUserMusicIndex(Long userId, Long lastCursorId, Integer size) {
        return getFromDbCursorPage(
                () -> {
                    // 使用MP条件构造器
                    QueryWrapper<DigitalAudio> queryWrapper = new QueryWrapper<>();
                    queryWrapper.eq("user_id", userId)
                            .eq("type", 2) // 更新为2，与数据库保持一致
                            .orderByDesc("digital_audio_id");

                    if (lastCursorId != null) {
                        queryWrapper.lt("digital_audio_id", lastCursorId);
                    }

                    // 设置查询大小
                    queryWrapper.last("LIMIT " + (size + 1));
                    return audioMapper.selectList(queryWrapper);
                },
                DigitalAudio::getDigitalAudioId,
                po -> DigitalAudioEntity.builder()
                        .digitalAudioId(po.getDigitalAudioId())
                        .userId(po.getUserId())
                        .duration(po.getDuration())
                        .name(po.getName())
                        .url(po.getUrl())
                        .type(po.getType())
                        .createTime(po.getCreateTime())
                        .build(),
                size
        );
    }

    @Override
    public CursorPageResponseData<DigitalAudioEntity> queryUserRecordingIndex(Long userId, Long lastCursorId, Integer size) {
        return getFromDbCursorPage(
                () -> {
                    // 使用MP条件构造器
                    QueryWrapper<DigitalAudio> queryWrapper = new QueryWrapper<>();
                    queryWrapper.eq("user_id", userId)
                            .eq("type", 1)
                            .orderByDesc("digital_audio_id");

                    if (lastCursorId != null) {
                        queryWrapper.lt("digital_audio_id", lastCursorId);
                    }

                    // 设置查询大小
                    queryWrapper.last("LIMIT " + (size + 1));
                    return audioMapper.selectList(queryWrapper);
                },
                DigitalAudio::getDigitalAudioId,
                po -> DigitalAudioEntity.builder()
                        .digitalAudioId(po.getDigitalAudioId())
                        .duration(po.getDuration())
                        .name(po.getName())
                        .url(po.getUrl())
                        .build(),
                size
        );
    }

    @Override
    public CursorPageResponseData<DigitalAudioEntity> querySysMusicIndex(Long lastCursorId, Integer size) {
        return getFromDbCursorPage(
                () -> {
                    // 使用MP条件构造器
                    QueryWrapper<DigitalAudio> queryWrapper = new QueryWrapper<>();
                    queryWrapper.isNull("user_id")
                            .eq("type", 0)
                            .orderByDesc("digital_audio_id");

                    if (lastCursorId != null) {
                        queryWrapper.lt("digital_audio_id", lastCursorId);
                    }

                    // 设置查询大小
                    queryWrapper.last("LIMIT " + (size + 1));
                    return audioMapper.selectList(queryWrapper);
                },
                DigitalAudio::getDigitalAudioId,
                po -> DigitalAudioEntity.builder()
                        .digitalAudioId(po.getDigitalAudioId())
                        .name(po.getName())
                        .url(po.getUrl())
                        .duration(po.getDuration())
                        .build(),
                size
        );
    }

    @Override
    public void saveDigitalAudio(List<DigitalAudioEntity> musicEntityList) {
        // 确保不传递createTime和updateTime，让MyBatis Plus的自动填充功能处理
        List<DigitalAudio> digitalAudioList = musicEntityList.stream()
                .map(musicEntity -> DigitalAudio.builder()
                        .name(musicEntity.getName())
                        .duration(musicEntity.getDuration())
                        .userId(musicEntity.getUserId())
                        .type(musicEntity.getType())
                        .url(musicEntity.getUrl())
                        // 显式不设置createTime和updateTime，完全依赖MyBatis Plus的自动填充
                        .build())
                .toList();

        // 使用MyBatis Plus的批量插入方法（BaseMapper扩展方法）
        // 注意：如果当前版本的MyBatis Plus不支持insertBatchSomeColumn，可以使用以下方式
        // 方式1：如果支持批量插入
        com.baomidou.mybatisplus.extension.toolkit.Db.saveBatch(digitalAudioList);

        // 方式2：如果方式1不行，保留逐个插入的回退方案
        // for (DigitalAudio digitalAudio : digitalAudioList) {
        //     audioMapper.insert(digitalAudio);
        // }
    }


    @Override
    public CursorPageResponseData<DigitalVideoEntity> queryUserVideoIndex(Long userId, Long lastCursorId, Integer size) {
        return getFromDbCursorPage(
                () -> videoMapper.selectUserVideoListCursorPage(userId, lastCursorId, size + 1),
                DigitalVideo::getDigitalVideoId,
                po -> DigitalVideoEntity.builder()
                        .digitalVideoId(po.getDigitalVideoId())
                        .url(po.getUrl())
                        .name(po.getName())
                        .duration(po.getDuration())
                        .build(),
                size
        );
    }

    @Override
    public CursorPageResponseData<DigitalVideoEntity> querySysVideoIndex(Long lastCursorId, Integer size) {
        return getFromDbCursorPage(
                () -> videoMapper.selectSysVideoListCursorPage(lastCursorId, size + 1),
                DigitalVideo::getDigitalVideoId,
                po -> DigitalVideoEntity.builder()
                        .digitalVideoId(po.getDigitalVideoId())
                        .name(po.getName())
                        .duration(po.getDuration())
                        .url(po.getUrl())
                        .build(),
                size
        );
    }

    @Override
    public void saveDigitalVideo(List<DigitalVideoEntity> videoEntityList) {
        videoMapper.insert(videoEntityList
                .stream()
                .map(entity -> {
                    return DigitalVideo.builder()
                            .url(entity.getUrl())
                            .userId(entity.getUserId())
                            .duration(entity.getDuration())
                            .name(entity.getName())
                            .build();
                })
                .toList());

    }


    @Override
    public CursorPageResponseData<DigitalImageEntity> queryUserBackgroundImageIndex(Long userId, Long lastCursorId, Integer size) {
        return getFromDbCursorPage(
                () -> imageMapper.selectUserBackgroundImageListCursorPage(userId, lastCursorId, size + 1),
                DigitalImage::getDigitalImageId,
                po -> DigitalImageEntity.builder()
                        .digitalImageId(po.getDigitalImageId())
                        .url(po.getUrl())
                        .name(po.getName())
                        .build(),
                size
        );
    }

    @Override
    public CursorPageResponseData<DigitalImageEntity> queryUserIllustrationImageIndex(Long userId, Long lastCursorId, Integer size) {
        return getFromDbCursorPage(
                () -> imageMapper.selectUserIllustrationImageListCursorPage(userId, lastCursorId, size + 1),
                DigitalImage::getDigitalImageId,
                po -> DigitalImageEntity.builder()
                        .digitalImageId(po.getDigitalImageId())
                        .url(po.getUrl())
                        .name(po.getName())
                        .build(),
                size
        );
    }

    @Override
    public CursorPageResponseData<DigitalImageEntity> querySysBackgroundImageIndex(Long lastCursorId, Integer size) {
        return getFromDbCursorPage(
                () -> imageMapper.selectSysBackgroundImageListCursorPage(lastCursorId, size + 1),
                DigitalImage::getDigitalImageId,
                po -> DigitalImageEntity.builder()
                        .digitalImageId(po.getDigitalImageId())
                        .url(po.getUrl())
                        .name(po.getName())
                        .build(),
                size
        );
    }

    @Override
    public CursorPageResponseData<DigitalImageEntity> querySysIllustrationImageIndex(Long lastCursorId, Integer size) {
        return getFromDbCursorPage(
                () -> imageMapper.selectSysIllustrationImageListCursorPage(lastCursorId, size + 1),
                DigitalImage::getDigitalImageId,
                po -> DigitalImageEntity.builder()
                        .digitalImageId(po.getDigitalImageId())
                        .url(po.getUrl())
                        .name(po.getName())
                        .build(),
                size
        );
    }

    @Override
    public void saveDigitalImage(List<DigitalImageEntity> imageEntityList) {
        imageMapper.insert(imageEntityList
                .stream()
                .map(entity -> {
                    return DigitalImage.builder()
                            .url(entity.getUrl())
                            .type(entity.getType() ? 1 : 0) // Boolean转Integer: true->1, false->0
                            .userId(entity.getUserId())
                            .name(entity.getName())
                            .build();
                })
                .toList());
    }

    @Override
    public CursorPageResponseData<DigitalModelEntity> queryDigitalModelIndex(Long userId, Long lastCursorId, Integer size) {
        return getFromDbCursorPage(
                () -> digitalModelMapper.selectDigitalModelCursorPageByUserId(userId, lastCursorId, size + 1),
                DigitalModel::getDigitalModelId,
                po -> DigitalModelEntity.builder()
                        .digitalModelId(po.getDigitalModelId())
                        .userId(po.getUserId())
                        .voiceId(po.getVoiceId())
                        .imageId(po.getImageId())
                        .name(po.getName())
                        .avatar(po.getAvatar())
                        .digitalModelGenderEnumVO(DigitalModelGenderEnumVO.getByCode(po.getGender()))
                        .build(),
                size
        );
    }

    @Override
    public Long deductUserAccountQuota(VideoCompositionEntity videoCompositionEntity) {
        Integer time = videoCompositionEntity.getVideoTime();
        //助理先扣医生
        if (videoCompositionEntity.getResourceUserTypeEnumVO() == ResourceUserTypeEnumVO.DOCTOR_ASSISTANT) {
            //共享医生资源
            List<Long> expertIds = expertAssistantMapMapper.selectAssistantIdsByExpertId(videoCompositionEntity.getUserId());
            for (Long expertId : expertIds) {
                Long resourceIdByExpertId = userAiResourceMapper.selectDigitalResourceIdByUserId(expertId);
                if (resourceIdByExpertId == null) {
                    //当前医生没有购买过
                    continue;
                }
                //先扣除订阅资源
                Long doctorSubscriptionDetailId = userAiResourceDetailMapper.selectAndLockSubscriptionDetailIdByUserAiResourceIdAndValue(resourceIdByExpertId, time);
                if (doctorSubscriptionDetailId != null) {
                    int doctorSubscriptionDeductCount = userAiResourceDetailMapper.deductBalanceByUserAiResourceDetailId(doctorSubscriptionDetailId, time);
                    if (doctorSubscriptionDeductCount == 1) {
                        return doctorSubscriptionDetailId;
                    }
                }
                //扣除直购资源
                Long doctorPurchasedDetailId = userAiResourceDetailMapper.selectAndLockPurchasedDetailIdByUserAiResourceIdAndValue(resourceIdByExpertId, time);
                if (doctorPurchasedDetailId != null) {
                    int doctorPurchasedDeductCount = userAiResourceDetailMapper.deductBalanceByUserAiResourceDetailId(doctorPurchasedDetailId, time);
                    if (doctorPurchasedDeductCount == 1) {
                        return doctorPurchasedDetailId;
                    }
                }
            }
        }
        //没有才扣自己的自己的资源
        Long resourceIdByAssistantId = userAiResourceMapper.selectDigitalResourceIdByUserId(videoCompositionEntity.getUserId());
        if (resourceIdByAssistantId == null) {
            //没有购买过
            return null;
        }
        //先扣除订阅资源
        Long assistantSubscriptionDetailId = userAiResourceDetailMapper.selectAndLockSubscriptionDetailIdByUserAiResourceIdAndValue(resourceIdByAssistantId, time);
        if (assistantSubscriptionDetailId != null) {
            int assistantSubscriptionDeductCount = userAiResourceDetailMapper.deductBalanceByUserAiResourceDetailId(assistantSubscriptionDetailId, time);
            if (assistantSubscriptionDeductCount == 1) {
                return assistantSubscriptionDetailId;
            }
        }
        //扣除直购资源
        Long assistantPurchasedDetailId = userAiResourceDetailMapper.selectAndLockPurchasedDetailIdByUserAiResourceIdAndValue(resourceIdByAssistantId, time);
        if (assistantPurchasedDetailId != null) {
            int assistantPurchasedDeductCount = userAiResourceDetailMapper.deductBalanceByUserAiResourceDetailId(assistantPurchasedDetailId, time);
            if (assistantPurchasedDeductCount == 1) {
                return assistantPurchasedDetailId;
            }
        }

        return null;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void saveDigitalWork(DigitalWorkEntity digitalWorkEntity) {
        // 根据时长计算bill（1分钟以下按1分钟计算，1分钟以上按实际时间计算）
        Double bill = 0.0;
        if (digitalWorkEntity.getDuration() != null && digitalWorkEntity.getDuration() > 0) {
            double minutes;
            if (digitalWorkEntity.getDuration() <= 60) {
                // 1分钟以下按1分钟计算
                minutes = 1.0;
            } else {
                // 1分钟以上按实际时间计算（保留小数）
                minutes = digitalWorkEntity.getDuration() / 60.0;
            }
            bill = minutes * 50.0;
        }

        DigitalWork digitalWork = DigitalWork.builder()
                .userId(digitalWorkEntity.getUserId())
                .body(digitalWorkEntity.getBody())
                .title(UUID.randomUUID().toString())
                .externalId(digitalWorkEntity.getExternalId())
                .quotaBizKey(digitalWorkEntity.getQuotaBizKey())
                .url(digitalWorkEntity.getUrl())
                .duration(digitalWorkEntity.getDuration())
                .bill(bill)
                .build();
        int workInsertCount = digitalWorkMapper.insert(digitalWork);
        if (workInsertCount != 1 || digitalWork.getDigitalWorkId() == null) {
            throw new IllegalStateException("Failed to persist digital work before audit");
        }
        // A durable placeholder precedes the external side effect; audit only actual provider submissions.
        if ("quota_reservation".equals(digitalWorkEntity.getCaptureSource())) return;

        TDigitalWorkComposeAudit audit = TDigitalWorkComposeAudit.builder()
                .workRecordId(digitalWork.getDigitalWorkId())
                .externalId(digitalWorkEntity.getExternalId())
                .userId(digitalWorkEntity.getUserId())
                .composeParams(digitalWorkEntity.getComposeParams())
                .videoTime(digitalWorkEntity.getVideoTime())
                .requestSha256(digitalWorkEntity.getRequestSha256())
                .captureSource(digitalWorkEntity.getCaptureSource())
                .build();
        int auditInsertCount = digitalWorkComposeAuditMapper.insert(audit);
        if (auditInsertCount != 1) {
            throw new IllegalStateException("Failed to persist digital work composition audit");
        }
    }

    @Override
    public Integer queryOwnedAudioDuration(Long userId, String url) {
        if (url == null || userId == null) return null;
        DigitalAudio audio = audioMapper.selectOne(new LambdaQueryWrapper<DigitalAudio>()
                .eq(DigitalAudio::getUserId, userId).eq(DigitalAudio::getUrl, url).last("LIMIT 1"));
        return audio == null ? null : audio.getDuration();
    }

    @Override
    public DigitalWorkEntity findDigitalWorkByQuotaKey(Long userId, String quotaKey) {
        DigitalWork work = digitalWorkMapper.selectOne(new LambdaQueryWrapper<DigitalWork>()
                .eq(DigitalWork::getUserId, userId).eq(DigitalWork::getQuotaBizKey, quotaKey));
        return work == null ? null : DigitalWorkEntity.builder().digitalWorkId(work.getDigitalWorkId())
                .userId(userId).quotaBizKey(quotaKey).externalId(work.getExternalId())
                .status(work.getStatus()).body(work.getBody()).build();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void bindDigitalWorkSubmission(Long userId, String quotaKey, VideoCompositionSubmissionEntity submission) {
        DigitalWorkEntity work = findDigitalWorkByQuotaKey(userId, quotaKey);
        if (work == null) throw new IllegalStateException("Missing digital work reservation");
        int count = digitalWorkMapper.update(new LambdaUpdateWrapper<DigitalWork>()
                .eq(DigitalWork::getUserId, userId).eq(DigitalWork::getQuotaBizKey, quotaKey)
                .eq(DigitalWork::getExternalId, work.getExternalId()).isNull(DigitalWork::getStatus)
                .set(DigitalWork::getExternalId, submission.getExternalId())
                .set(DigitalWork::getBody, submission.getProviderRequestBody()));
        if (count != 1) throw new IllegalStateException("Digital work reservation changed");
        int inserted = digitalWorkComposeAuditMapper.insert(TDigitalWorkComposeAudit.builder()
                .workRecordId(work.getDigitalWorkId()).externalId(submission.getExternalId()).userId(userId)
                .composeParams(submission.getProviderRequestBody()).captureSource("provider_request")
                .requestSha256(submission.getProviderRequestSha256()).build());
        if (inserted != 1) throw new IllegalStateException("Failed to audit provider submission");
    }

    @Override
    public void markDigitalWorkSubmissionRejected(Long userId, String quotaKey) {
        digitalWorkMapper.update(new LambdaUpdateWrapper<DigitalWork>()
                .eq(DigitalWork::getUserId, userId).eq(DigitalWork::getQuotaBizKey, quotaKey)
                .isNull(DigitalWork::getStatus).set(DigitalWork::getStatus, 0)
                .set(DigitalWork::getFailureReason, "供应商拒绝合成，额度已退回"));
    }

    @Override
    public int deleteDigitalWork(DigitalWorkEntity digitalWorkEntity) {
        DigitalWork owned = digitalWorkMapper.selectOne(new LambdaQueryWrapper<DigitalWork>()
                .eq(DigitalWork::getDigitalWorkId, digitalWorkEntity.getDigitalWorkId())
                .eq(DigitalWork::getUserId, digitalWorkEntity.getUserId()));
        if (owned == null || owned.getStatus() == null) return 0;
        if (owned.getQuotaBizKey() != null) {
            return digitalWorkMapper.update(new LambdaUpdateWrapper<DigitalWork>()
                    .eq(DigitalWork::getDigitalWorkId, owned.getDigitalWorkId())
                    .eq(DigitalWork::getUserId, digitalWorkEntity.getUserId())
                    .set(DigitalWork::getQuotaHidden, true));
        }
        return digitalWorkMapper.delete(
                new LambdaUpdateWrapper<DigitalWork>()
                        .eq(DigitalWork::getDigitalWorkId, digitalWorkEntity.getDigitalWorkId())
                        .eq(DigitalWork::getUserId, digitalWorkEntity.getUserId())
                        .isNotNull(DigitalWork::getStatus)
        );
    }

    @Override
    public int updateDigitalWorkTitle(DigitalWorkEntity entity) {
        return digitalWorkMapper.update(
                new LambdaUpdateWrapper<DigitalWork>()
                        .eq(DigitalWork::getDigitalWorkId, entity.getDigitalWorkId())
                        .eq(DigitalWork::getUserId, entity.getUserId())
                        .set(DigitalWork::getTitle, entity.getTitle())
        );
    }

    @Override
    public CursorPageResponseData<DigitalWorkEntity> queryDigitalWorkIndex(Long userId, Long lastCursorId, Integer size) {
        return getFromDbCursorPage(
                () -> digitalWorkMapper.selectUserWorkListCursorPageByUserId(userId, lastCursorId, size + 1),
                DigitalWork::getDigitalWorkId,
                po -> DigitalWorkEntity.builder()
                        .digitalWorkId(po.getDigitalWorkId())
                        .body(po.getBody())
                        .title(po.getTitle())
                        .externalId(po.getExternalId())
                        .status(po.getStatus())
                        .failureReason(po.getFailureReason())
                        .createTime(po.getCreateTime())
                        .build(),
                size
        );
    }

    @Override
    public CursorPageResponseData<DigitalTemplateEntity> queryUserTemplateIndex(Long userId, Long lastCursorId, Integer size) {
        return getFromDbCursorPage(
                () -> digitalTemplateMapper.selectUserTemplateListCursorPage(userId, lastCursorId, size + 1),
                DigitalTemplate::getDigitalTemplateId,
                po -> DigitalTemplateEntity.builder()
                        .digitalTemplateId(po.getDigitalTemplateId())
                        .userId(po.getUserId())
                        .title(po.getTitle())
                        .body(po.getBody())
                        .createTime(po.getCreateTime())
                        .build(),
                size
        );
    }

    @Override
    public CursorPageResponseData<DigitalTemplateEntity> querySysTemplateIndex(Long lastCursorId, Integer size) {
        return getFromDbCursorPage(
                () -> digitalTemplateMapper.selectSysTemplateListCursorPage(lastCursorId, size + 1),
                DigitalTemplate::getDigitalTemplateId,
                po -> DigitalTemplateEntity.builder()
                        .digitalTemplateId(po.getDigitalTemplateId())
                        .userId(po.getUserId())
                        .title(po.getTitle())
                        .body(po.getBody())
                        .createTime(po.getCreateTime())
                        .build(),
                size
        );
    }

    @Override
    public void createUserDigitalTemplate(DigitalTemplateEntity entity) {
        digitalTemplateMapper.insert(
                DigitalTemplate.builder()
                        .userId(entity.getUserId())
                        .title(entity.getTitle())
                        .body(entity.getBody())
                        .build()
        );
    }

    @Override
    public int deleteDigitalTemplate(DigitalTemplateEntity entity) {
        return digitalTemplateMapper.delete(
                new LambdaQueryWrapper<DigitalTemplate>()
                        .eq(DigitalTemplate::getDigitalTemplateId, entity.getDigitalTemplateId())
                        .eq(DigitalTemplate::getUserId, entity.getUserId())
        );
    }

    @Override
    public int updateDigitalTemplateTitle(DigitalTemplateEntity entity) {
        return digitalTemplateMapper.update(
                new LambdaUpdateWrapper<DigitalTemplate>()
                        .eq(DigitalTemplate::getDigitalTemplateId, entity.getDigitalTemplateId())
                        .eq(DigitalTemplate::getUserId, entity.getUserId())
                        .set(DigitalTemplate::getTitle, entity.getTitle())
        );

    }


}
