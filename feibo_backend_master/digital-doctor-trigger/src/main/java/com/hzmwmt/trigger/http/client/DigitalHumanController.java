package com.hzmwmt.trigger.http.client;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.hzmwmt.api.IDigitalHumanApi;
import com.hzmwmt.api.dto.*;
import com.hzmwmt.api.response.ApiCursorPageData;
import com.hzmwmt.api.response.Response;
import com.hzmwmt.domain.auth.service.IAuthService;
import com.hzmwmt.domain.digital.adapter.repository.IDigitalRepository;
import com.hzmwmt.domain.digital.model.aggregate.DigitalVoiceAggregate;
import com.hzmwmt.domain.digital.model.entity.*;
import com.hzmwmt.domain.digital.model.valobj.DigitalVoiceCategoryTypeEnumVO;
import com.hzmwmt.domain.digital.model.valobj.VoiceGenderEnumVO;
import com.hzmwmt.domain.digital.service.File.factory.UploadStrategyFactory;
import com.hzmwmt.domain.digital.service.*;
import com.hzmwmt.domain.digital.service.work.DigitalVideoDuration;
import com.hzmwmt.domain.digital.service.work.DigitalWorkQuotaSettlement;
import com.hzmwmt.domain.dto.UploadDTO;
import com.hzmwmt.domain.entity.user.TDigitalWork;
import com.hzmwmt.domain.gateway.FileStorageGateway;
import com.hzmwmt.domain.mapper.user.AdminDigitalWorkMapper;
import com.hzmwmt.domain.service.function.TUserDigitalModelMapService;
import com.hzmwmt.infrastructure.gateway.netty.INettyService;
import com.hzmwmt.infrastructure.gateway.digital.DigitalHumanCallbackVerifier;
import com.hzmwmt.infrastructure.gateway.digital.SafeVideoDownloader;
import com.hzmwmt.infrastructure.properties.DigitalHumanApiProperties;
import com.hzmwmt.types.annotations.Idempotent;
import com.hzmwmt.types.annotations.OperationLog;
import com.hzmwmt.types.common.CursorPageResponseData;
import com.hzmwmt.types.enums.ResourceUserTypeEnumVO;
import com.hzmwmt.types.enums.ResponseCode;
import com.hzmwmt.types.exception.AppException;
import com.hzmwmt.types.util.SpringUtil;
import com.hzmwmt.types.util.TimeAgoUtil;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * @author huihui
 * @version v1.0
 * @Description: TODO
 * @ClassName DigitalHumanController
 * @Date: 2025/9/13 14:52
 */
@Slf4j
@Validated
@RestController
@RequestMapping("/api/v1/digital-human")
public class DigitalHumanController implements IDigitalHumanApi {
    @Resource
    private IAuthService authService;
    @Resource
    private IDigitalAudioService digitalAudioService;
    @Resource
    private IDigitalImageService digitalImageService;
    @Resource
    private IDigitalRepository digitalRepository;
    @Resource
    private IDigitalModelService digitalModelService;
    @Resource
    private IDigitalVideoService digitalVideoService;
    @Resource
    private IDigitalFileService digitalFileService;
    @Resource
    private IDigitalWorkService digitalWorkService;
    @Resource
    private IDigitalTemplateService digitalTemplateService;
    @Resource
    private IDigitalVoiceService digitalVoiceService;
    @Resource
    private AdminDigitalWorkMapper adminDigitalWorkMapper;
    @Resource(name = "hybridOSSGateway")
    private FileStorageGateway fileStorageGateway;

    @Resource
    private INettyService nettyService;
    @Resource
    private TUserDigitalModelMapService userDigitalModelMapService;
    @Resource
    private DigitalHumanCallbackVerifier callbackVerifier;
    @Resource
    private SafeVideoDownloader safeVideoDownloader;
    @Resource
    private DigitalHumanApiProperties digitalHumanApiProperties;
    @Resource
    private DigitalWorkQuotaSettlement digitalWorkQuotaSettlement;
    @Resource
    private HttpServletRequest servletRequest;


    @Override
    @RequestMapping(value = "music/user", method = RequestMethod.GET)
    // @OperationLog(operationType = "查询", description = "查询用户音乐列表", recordParams = true)
    public Response<ApiCursorPageData<DigitalAudioResponseDTO>> queryUserMusic(
            @RequestParam(required = false) Long lastCursorId,
            @RequestParam Integer size) {
        Long userId = authService.queryValidatedUser().getUserId();
        log.info("用户 {} 查询个人音乐列表, lastCursorId: {}, size: {}", userId, lastCursorId, size);
        CursorPageResponseData<DigitalAudioEntity> domainPage = digitalAudioService.queryUserMusicIndex(userId, lastCursorId, size);

        if (domainPage.getData() == null) {
            return Response.<ApiCursorPageData<DigitalAudioResponseDTO>>builder()
                    .code(ResponseCode.SUCCESS.getCode())
                    .info(ResponseCode.SUCCESS.getInfo())
                    .data(ApiCursorPageData.<DigitalAudioResponseDTO>builder()
                            .hasNext(false)
                            .data(null)
                            .build())
                    .build();
        }

        return Response.<ApiCursorPageData<DigitalAudioResponseDTO>>builder()
                .code(ResponseCode.SUCCESS.getCode())
                .info(ResponseCode.SUCCESS.getInfo())
                .data(
                        ApiCursorPageData.<DigitalAudioResponseDTO>builder()
                                .data(
                                        domainPage.getData().stream()
                                                .map(digitalAudio -> DigitalAudioResponseDTO.builder()
                                                        .name(digitalAudio.getName())
                                                        .url(digitalAudio.getUrl())
                                                        .duration(digitalAudio.getDuration())
                                                        .build()
                                                ).toList()
                                )
                                .nextCursorId(domainPage.getNextCursorId())
                                .hasNext(domainPage.getHasNext())
                                .build()
                )
                .build();
    }

    @Override
    @RequestMapping(value = "recording/user", method = RequestMethod.GET)
    // @OperationLog(operationType = "查询", description = "查询用户录音列表", recordParams = true)
    public Response<ApiCursorPageData<DigitalAudioResponseDTO>> queryUserRecordings(
            @RequestParam(required = false) Long lastCursorId,
            @RequestParam Integer size) {
        Long userId = authService.queryValidatedUser().getUserId();
        log.info("用户 {} 查询个人录音列表, lastCursorId: {}, size: {}", userId, lastCursorId, size);
        CursorPageResponseData<DigitalAudioEntity> domainPage = digitalAudioService.queryUserRecordingIndex(userId, lastCursorId, size);

        if (domainPage == null || domainPage.getData() == null) {
            return Response.<ApiCursorPageData<DigitalAudioResponseDTO>>builder()
                    .code(ResponseCode.SUCCESS.getCode())
                    .info(ResponseCode.SUCCESS.getInfo())
                    .data(ApiCursorPageData.<DigitalAudioResponseDTO>builder().hasNext(false).data(List.of()).build())
                    .build();
        }
        return Response.<ApiCursorPageData<DigitalAudioResponseDTO>>builder()
                .code(ResponseCode.SUCCESS.getCode())
                .info(ResponseCode.SUCCESS.getInfo())
                .data(
                        ApiCursorPageData.<DigitalAudioResponseDTO>builder()
                                .nextCursorId(domainPage.getNextCursorId())
                                .hasNext(domainPage.getHasNext())
                                .data(
                                        domainPage.getData().stream()
                                                .map(digitalAudio -> DigitalAudioResponseDTO.builder()
                                                        .name(digitalAudio.getName())
                                                        .url(digitalAudio.getUrl())
                                                        .duration(digitalAudio.getDuration())
                                                        .build()
                                                ).toList()
                                )
                                .build()
                )
                .build();
    }

    @Override
    @RequestMapping(value = "music/system", method = RequestMethod.GET)
    // @OperationLog(operationType = "查询", description = "查询系统音乐列表", recordParams = true)
    public Response<ApiCursorPageData<DigitalAudioResponseDTO>> querySystemMusic(
            @RequestParam(required = false) Long lastCursorId,
            @RequestParam Integer size) {
        log.info("查询系统音乐列表, lastCursorId: {}, size: {}", lastCursorId, size);
        CursorPageResponseData<DigitalAudioEntity> domainPage = digitalAudioService.querySysMusicIndex(lastCursorId, size);

        if (domainPage.getData() == null) {
            return Response.<ApiCursorPageData<DigitalAudioResponseDTO>>builder()
                    .code(ResponseCode.SUCCESS.getCode())
                    .info(ResponseCode.SUCCESS.getInfo())
                    .data(ApiCursorPageData.<DigitalAudioResponseDTO>builder().hasNext(false).data(List.of()).build())
                    .build();
        }


        return Response.<ApiCursorPageData<DigitalAudioResponseDTO>>builder()
                .code(ResponseCode.SUCCESS.getCode())
                .info(ResponseCode.SUCCESS.getInfo())
                .data(
                        ApiCursorPageData.<DigitalAudioResponseDTO>builder()
                                .nextCursorId(domainPage.getNextCursorId())
                                .hasNext(domainPage.getHasNext())
                                .data(
                                        domainPage.getData().stream()
                                                .map(digitalAudio -> DigitalAudioResponseDTO.builder()
                                                        .name(digitalAudio.getName())
                                                        .url(digitalAudio.getUrl())
                                                        .duration(digitalAudio.getDuration())
                                                        .build()
                                                ).toList()
                                )
                                .build()
                )
                .build();
    }

    @Override
    @RequestMapping(value = "background/user", method = RequestMethod.GET)
    // @OperationLog(operationType = "查询", description = "查询用户背景图片列表", recordParams = true)
    public Response<ApiCursorPageData<DigitalImageResponseDTO>> queryUserBackgroundImages(
            @RequestParam(required = false) Long lastCursorId,
            @RequestParam Integer size) {
        Long userId = authService.queryValidatedUser().getUserId();
        log.info("用户 {} 查询个人背景图片列表, lastCursorId: {}, size: {}", userId, lastCursorId, size);
        CursorPageResponseData<DigitalImageEntity> domainPage = digitalImageService.queryUserBackgroundImageIndex(userId, lastCursorId, size);

        if (domainPage.getData() == null) {
            return Response.<ApiCursorPageData<DigitalImageResponseDTO>>builder()
                    .code(ResponseCode.SUCCESS.getCode())
                    .info(ResponseCode.SUCCESS.getInfo())
                    .data(ApiCursorPageData.<DigitalImageResponseDTO>builder().hasNext(false).data(List.of()).build())
                    .build();
        }

        return Response.<ApiCursorPageData<DigitalImageResponseDTO>>builder()
                .code(ResponseCode.SUCCESS.getCode())
                .info(ResponseCode.SUCCESS.getInfo())
                .data(
                        ApiCursorPageData.<DigitalImageResponseDTO>builder()
                                .nextCursorId(domainPage.getNextCursorId())
                                .hasNext(domainPage.getHasNext())
                                .data(
                                        domainPage.getData().stream()
                                                .map(digitalImage -> DigitalImageResponseDTO.builder()
                                                        .name(digitalImage.getName())
                                                        .url(digitalImage.getUrl())
                                                        .build()
                                                ).toList()
                                )
                                .build()
                )
                .build();
    }

    @Override
    @RequestMapping(value = "illustration/user", method = RequestMethod.GET)
    // @OperationLog(operationType = "查询", description = "查询用户插图列表", recordParams = true)
    public Response<ApiCursorPageData<DigitalImageResponseDTO>> queryUserIllustrationImages(
            @RequestParam(required = false) Long lastCursorId,
            @RequestParam Integer size) {
        Long userId = authService.queryValidatedUser().getUserId();
        log.info("用户 {} 查询个人插图列表, lastCursorId: {}, size: {}", userId, lastCursorId, size);
        CursorPageResponseData<DigitalImageEntity> domainPage = digitalImageService.queryUserIllustrationImageIndex(userId, lastCursorId, size);

        if (domainPage.getData() == null) {
            return Response.<ApiCursorPageData<DigitalImageResponseDTO>>builder()
                    .code(ResponseCode.SUCCESS.getCode())
                    .info(ResponseCode.SUCCESS.getInfo())
                    .data(ApiCursorPageData.<DigitalImageResponseDTO>builder().hasNext(false).data(List.of()).build())
                    .build();
        }

        return Response.<ApiCursorPageData<DigitalImageResponseDTO>>builder()
                .code(ResponseCode.SUCCESS.getCode())
                .info(ResponseCode.SUCCESS.getInfo())
                .data(
                        ApiCursorPageData.<DigitalImageResponseDTO>builder()
                                .nextCursorId(domainPage.getNextCursorId())
                                .hasNext(domainPage.getHasNext())
                                .data(
                                        domainPage.getData().stream()
                                                .map(digitalImage -> DigitalImageResponseDTO.builder()
                                                        .name(digitalImage.getName())
                                                        .url(digitalImage.getUrl())
                                                        .build()
                                                ).toList()
                                )
                                .build()
                )
                .build();
    }

    @Override
    @RequestMapping(value = "background/system", method = RequestMethod.GET)
    // @OperationLog(operationType = "查询", description = "查询系统背景图片列表", recordParams = true)
    public Response<ApiCursorPageData<DigitalImageResponseDTO>> querySystemBackgroundImages(
            @RequestParam(required = false) Long lastCursorId,
            @RequestParam Integer size) {
        log.info("查询系统背景图片列表, lastCursorId: {}, size: {}", lastCursorId, size);
        CursorPageResponseData<DigitalImageEntity> domainPage = digitalImageService.querySysBackgroundImageIndex(lastCursorId, size);

        if (domainPage == null || domainPage.getData() == null) {
            return Response.<ApiCursorPageData<DigitalImageResponseDTO>>builder()
                    .code(ResponseCode.SUCCESS.getCode())
                    .info(ResponseCode.SUCCESS.getInfo())
                    .data(ApiCursorPageData.<DigitalImageResponseDTO>builder().hasNext(false).data(List.of()).build())
                    .build();
        }

        List<DigitalImageResponseDTO> dtoList = domainPage.getData().stream()
                .map(e -> DigitalImageResponseDTO.builder()
                        .name(e.getName())
                        .url(e.getUrl())
                        .build())
                .collect(Collectors.toList());

        ApiCursorPageData<DigitalImageResponseDTO> responsePage = ApiCursorPageData.<DigitalImageResponseDTO>builder()
                .data(dtoList)
                .nextCursorId(domainPage.getNextCursorId())
                .hasNext(domainPage.getHasNext())
                .build();

        return Response.<ApiCursorPageData<DigitalImageResponseDTO>>builder()
                .code(ResponseCode.SUCCESS.getCode())
                .info(ResponseCode.SUCCESS.getInfo())
                .data(responsePage)
                .build();
    }

    @Override
    @RequestMapping(value = "illustration/system", method = RequestMethod.GET)
    // @OperationLog(operationType = "查询", description = "查询系统插图列表", recordParams = true)
    public Response<ApiCursorPageData<DigitalImageResponseDTO>> querySystemIllustrationImages(
            @RequestParam(required = false) Long lastCursorId,
            @RequestParam Integer size) {
        log.info("查询系统插图列表, lastCursorId: {}, size: {}", lastCursorId, size);
        CursorPageResponseData<DigitalImageEntity> domainPage = digitalImageService.querySysIllustrationImageIndex(lastCursorId, size);

        if (domainPage == null || domainPage.getData() == null) {
            return Response.<ApiCursorPageData<DigitalImageResponseDTO>>builder()
                    .code(ResponseCode.SUCCESS.getCode())
                    .info(ResponseCode.SUCCESS.getInfo())
                    .data(ApiCursorPageData.<DigitalImageResponseDTO>builder().hasNext(false).data(List.of()).build())
                    .build();
        }

        List<DigitalImageResponseDTO> dtoList = domainPage.getData().stream()
                .map(e -> DigitalImageResponseDTO.builder()
                        .name(e.getName())
                        .url(e.getUrl())
                        .build())
                .collect(Collectors.toList());

        ApiCursorPageData<DigitalImageResponseDTO> responsePage = ApiCursorPageData.<DigitalImageResponseDTO>builder()
                .data(dtoList)
                .nextCursorId(domainPage.getNextCursorId())
                .hasNext(domainPage.getHasNext())
                .build();

        return Response.<ApiCursorPageData<DigitalImageResponseDTO>>builder()
                .code(ResponseCode.SUCCESS.getCode())
                .info(ResponseCode.SUCCESS.getInfo())
                .data(responsePage)
                .build();
    }

    @Override
    @RequestMapping(value = "video/user", method = RequestMethod.GET)
    // @OperationLog(operationType = "查询", description = "查询用户视频列表", recordParams = true)
    public Response<ApiCursorPageData<DigitalVideoResponseDTO>> queryUserVideos(
            @RequestParam(required = false) Long lastCursorId,
            @RequestParam Integer size) {
        Long userId = authService.queryValidatedUser().getUserId();
        log.info("用户 {} 查询个人视频列表, lastCursorId: {}, size: {}", userId, lastCursorId, size);
        CursorPageResponseData<DigitalVideoEntity> domainPage = digitalVideoService.queryUserVideoIndex(userId, lastCursorId, size);

        if (domainPage == null || domainPage.getData() == null) {
            return Response.<ApiCursorPageData<DigitalVideoResponseDTO>>builder()
                    .code(ResponseCode.SUCCESS.getCode())
                    .info(ResponseCode.SUCCESS.getInfo())
                    .data(ApiCursorPageData.<DigitalVideoResponseDTO>builder().hasNext(false).data(List.of()).build())
                    .build();
        }

        List<DigitalVideoResponseDTO> dtoList = domainPage.getData().stream()
                .map(e -> DigitalVideoResponseDTO.builder()
                        .name(e.getName())
                        .url(e.getUrl())
                        .duration(e.getDuration())
                        .build())
                .collect(Collectors.toList());

        ApiCursorPageData<DigitalVideoResponseDTO> responsePage = ApiCursorPageData.<DigitalVideoResponseDTO>builder()
                .data(dtoList)
                .nextCursorId(domainPage.getNextCursorId())
                .hasNext(domainPage.getHasNext())
                .build();

        return Response.<ApiCursorPageData<DigitalVideoResponseDTO>>builder()
                .code(ResponseCode.SUCCESS.getCode())
                .info(ResponseCode.SUCCESS.getInfo())
                .data(responsePage)
                .build();
    }

    // queryGeneratedVideos方法已移除，因为IDigitalHumanApi接口中未定义此方法

    @Override
    @RequestMapping(value = "video/system", method = RequestMethod.GET)
    // @OperationLog(operationType = "查询", description = "查询系统视频列表", recordParams = true)
    public Response<ApiCursorPageData<DigitalVideoResponseDTO>> querySystemVideos(
            @RequestParam(required = false) Long lastCursorId,
            @RequestParam Integer size) {
        log.info("查询系统视频列表, lastCursorId: {}, size: {}", lastCursorId, size);
        CursorPageResponseData<DigitalVideoEntity> domainPage = digitalVideoService.querySysVideoIndex(lastCursorId, size);

        if (domainPage == null || domainPage.getData() == null) {
            return Response.<ApiCursorPageData<DigitalVideoResponseDTO>>builder()
                    .code(ResponseCode.SUCCESS.getCode())
                    .info(ResponseCode.SUCCESS.getInfo())
                    .data(ApiCursorPageData.<DigitalVideoResponseDTO>builder().hasNext(false).data(List.of()).build())
                    .build();
        }

        List<DigitalVideoResponseDTO> dtoList = domainPage.getData().stream()
                .map(e -> DigitalVideoResponseDTO.builder()
                        .name(e.getName())
                        .url(e.getUrl())
                        .duration(e.getDuration())
                        .build())
                .collect(Collectors.toList());

        ApiCursorPageData<DigitalVideoResponseDTO> responsePage = ApiCursorPageData.<DigitalVideoResponseDTO>builder()
                .data(dtoList)
                .nextCursorId(domainPage.getNextCursorId())
                .hasNext(domainPage.getHasNext())
                .build();

        return Response.<ApiCursorPageData<DigitalVideoResponseDTO>>builder()
                .code(ResponseCode.SUCCESS.getCode())
                .info(ResponseCode.SUCCESS.getInfo())
                .data(responsePage)
                .build();
    }

    // --- 文件上传接口 ---

    @Override
    @RequestMapping(value = "file/upload-single", method = RequestMethod.POST, consumes = "multipart/form-data")
    @OperationLog(operationType = "上传", description = "上传单个文件", recordParams = true)
    public Response<String> uploadSingleFile(
            @RequestParam("userId") Long userId,
            @RequestParam("file") MultipartFile file,
            @RequestParam("uploadType") Integer uploadType) {

        log.info("用户 {} 上传单个文件，文件大小: {}，类型: {}", userId, file.getSize(), uploadType);

        SingleUploadEntity entity = SingleUploadEntity.builder()
                .userId(userId)
                .file(file)
                .uploadType(UploadStrategyFactory.UploadTypeEnumVO.getByCode(uploadType))
                .build();

        String url = digitalFileService.uploadSingleFile(entity);
        return Response.<String>builder()
                .code(ResponseCode.SUCCESS.getCode())
                .info(ResponseCode.SUCCESS.getInfo())
                .data(url)
                .build();
    }

    @Override
    @RequestMapping(value = "file/upload-multiple", method = RequestMethod.POST, consumes = "multipart/form-data")
    @OperationLog(operationType = "上传", description = "上传多个文件", recordParams = true)
    public Response<List<String>> uploadMultipleFiles(@ModelAttribute @Valid MultipleFileUploadRequestDTO requestDTO) {
        Long userId = authService.queryValidatedUser().getUserId();
        UploadStrategyFactory.UploadTypeEnumVO typeEnum = UploadStrategyFactory.UploadTypeEnumVO.getByCode(requestDTO.getUploadType());
        log.info("用户 {} 上传多个文件, 数量: {}, 类型: {}", userId, requestDTO.getFiles().size(), typeEnum.getDesc());

        MultipleUploadEntity entity = new MultipleUploadEntity();
        entity.setUserId(userId);
        entity.setFiles(requestDTO.getFiles());
        entity.setUploadType(typeEnum);

        List<String> urls = digitalFileService.uploadMultipleFiles(entity);

        return Response.<List<String>>builder()
                .code(ResponseCode.SUCCESS.getCode())
                .info(ResponseCode.SUCCESS.getInfo())
                .data(urls)
                .build();
    }

    @Override
    @RequestMapping(value = "template/user", method = RequestMethod.GET)
    // @OperationLog(operationType = "查询", description = "查询用户模板列表", recordParams = true)
    public Response<ApiCursorPageData<QueryDigitalTemplateResponseDTO>> queryUserTemplates(
            @RequestParam(required = false) Long lastCursorId,
            @RequestParam() Integer size) {
        Long userId = authService.queryValidatedUser().getUserId();
        log.info("用户 {} 查询个人模板列表, lastCursorId: {}, size: {}", userId, lastCursorId, size);
        CursorPageResponseData<DigitalTemplateEntity> domainPage = digitalTemplateService.queryUserTemplateIndex(userId, lastCursorId, size);

        if (domainPage.getData() == null) {
            return Response.<ApiCursorPageData<QueryDigitalTemplateResponseDTO>>builder()
                    .code(ResponseCode.SUCCESS.getCode())
                    .info(ResponseCode.SUCCESS.getInfo())
                    .data(ApiCursorPageData.<QueryDigitalTemplateResponseDTO>builder().hasNext(false).data(List.of()).build())
                    .build();
        }

        return Response.<ApiCursorPageData<QueryDigitalTemplateResponseDTO>>builder()
                .code(ResponseCode.SUCCESS.getCode())
                .info(ResponseCode.SUCCESS.getInfo())
                .data(
                        ApiCursorPageData.<QueryDigitalTemplateResponseDTO>builder()
                                .hasNext(domainPage.getHasNext())
                                .nextCursorId(domainPage.getNextCursorId())
                                .data(
                                        domainPage.getData().stream().map(e ->
                                                QueryDigitalTemplateResponseDTO.builder()
                                                        .digitalTemplateId(e.getDigitalTemplateId())
                                                        .title(e.getTitle())
                                                        .body(e.getBody())
                                                        .createTime(TimeAgoUtil.format(e.getCreateTime()))
                                                        .build()
                                        ).toList()
                                ).build()
                ).build();
    }

    @Override
    @RequestMapping(value = "template/system", method = RequestMethod.GET)
    // @OperationLog(operationType = "查询", description = "查询系统模板列表", recordParams = true)
    public Response<ApiCursorPageData<QueryDigitalTemplateResponseDTO>> querySystemTemplates(
            @RequestParam(required = false) Long lastCursorId,
            @RequestParam() Integer size) {
        log.info("查询系统模板列表, lastCursorId: {}, size: {}", lastCursorId, size);
        CursorPageResponseData<DigitalTemplateEntity> domainPage = digitalTemplateService.querySysTemplateIndex(lastCursorId, size);

        if (domainPage == null || domainPage.getData() == null) {
            return Response.<ApiCursorPageData<QueryDigitalTemplateResponseDTO>>builder()
                    .code(ResponseCode.SUCCESS.getCode())
                    .info(ResponseCode.SUCCESS.getInfo())
                    .data(ApiCursorPageData.<QueryDigitalTemplateResponseDTO>builder().hasNext(false).data(List.of()).build())
                    .build();
        }

        return Response.<ApiCursorPageData<QueryDigitalTemplateResponseDTO>>builder()
                .code(ResponseCode.SUCCESS.getCode())
                .info(ResponseCode.SUCCESS.getInfo())
                .data(
                        ApiCursorPageData.<QueryDigitalTemplateResponseDTO>builder()
                                .hasNext(domainPage.getHasNext())
                                .nextCursorId(domainPage.getNextCursorId())
                                .data(
                                        domainPage.getData().stream().map(e ->
                                                QueryDigitalTemplateResponseDTO.builder()
                                                        .digitalTemplateId(e.getDigitalTemplateId())
                                                        .title(e.getTitle())
                                                        .body(e.getBody())
                                                        .createTime(TimeAgoUtil.format(e.getCreateTime()))
                                                        .build()
                                        ).toList()
                                ).build()
                ).build();
    }

    @Override
    @Idempotent
    @RequestMapping(value = "template/create", method = RequestMethod.POST)
    @OperationLog(operationType = "创建", description = "创建用户模板", recordParams = true)
    public Response<Void> createUserTemplate(@RequestBody @Valid CreateDigitalTemplateRequestDTO requestDTO) {
        Long userId = authService.queryValidatedUser().getUserId();
        int titleLength = requestDTO.getTitle() == null ? 0 : requestDTO.getTitle().length();
        log.info("用户 {} 创建新模板，标题长度: {}", userId, titleLength);

        DigitalTemplateEntity entity = DigitalTemplateEntity.builder()
                .userId(userId)
                .title(requestDTO.getTitle())
                .body(requestDTO.getBody())
                .build();

        digitalTemplateService.createUserDigitalTemplate(entity);

        return Response.<Void>builder()
                .code(ResponseCode.SUCCESS.getCode())
                .info(ResponseCode.SUCCESS.getInfo())
                .build();
    }

    @Override
    @Idempotent
    @RequestMapping(value = "template/{templateId}/delete", method = RequestMethod.POST)
    @OperationLog(operationType = "删除", description = "删除模板", recordParams = true)
    public Response<Void> deleteTemplate(@PathVariable("templateId") Long digitalTemplateId) {
        Long userId = authService.queryValidatedUser().getUserId();
        log.info("用户 {} 删除模板, ID: {}", userId, digitalTemplateId);

        DigitalTemplateEntity entity = DigitalTemplateEntity.builder()
                .userId(userId)
                .digitalTemplateId(digitalTemplateId)
                .build();

        digitalTemplateService.deleteUserDigitalTemplate(entity);

        return Response.<Void>builder()
                .code(ResponseCode.SUCCESS.getCode())
                .info(ResponseCode.SUCCESS.getInfo())
                .build();
    }

    @Override
    @Idempotent
    @RequestMapping(value = "template/update-title", method = RequestMethod.PUT)
    @OperationLog(operationType = "更新", description = "更新模板标题", recordParams = true)
    public Response<Void> updateTemplateTitle(@RequestBody @Valid UpdateDigitalTemplateTitleRequestDTO requestDTO) {
        Long userId = authService.queryValidatedUser().getUserId();
        int titleLength = requestDTO.getTitle() == null ? 0 : requestDTO.getTitle().length();
        log.info("用户 {} 更新模板标题，ID: {}，标题长度: {}", userId,
                requestDTO.getDigitalTemplateId(),
                titleLength);

        DigitalTemplateEntity entity = DigitalTemplateEntity.builder()
                .userId(userId)
                .digitalTemplateId(requestDTO.getDigitalTemplateId())
                .title(requestDTO.getTitle())
                .build();

        digitalTemplateService.updateUserDigitalTemplateTitle(entity);

        return Response.<Void>builder()
                .code(ResponseCode.SUCCESS.getCode())
                .info(ResponseCode.SUCCESS.getInfo())
                .build();
    }


    @Override
    @Idempotent
    @RequestMapping(value = "work/composition", method = RequestMethod.POST)
    @OperationLog(operationType = "合成", description = "视频合成", recordParams = true)
    public Response<String> videoComposition(@RequestBody @Valid DigitalVideoCompositionRequestDTO requestDTO) {
        Boolean isAssistant = authService.isDoctorAssistant();
        String videoCompositionId = digitalWorkService.createVideoComposition(
                VideoCompositionEntity.builder()
                        .body(requestDTO.getBody())
                        .requestId(servletRequest == null ? null : servletRequest.getHeader("Idempotency-Token"))
                        .userId(authService.queryValidatedUser().getUserId())
                        .resourceUserTypeEnumVO(ResourceUserTypeEnumVO.getByCode(isAssistant))
                        .videoTime(requestDTO.getVideoTime())
                        .build()
        );

        return Response.<String>builder()
                .info(ResponseCode.SUCCESS.getInfo())
                .code(ResponseCode.SUCCESS.getCode())
                .data(videoCompositionId)
                .build();
    }

    /**
     * 接收外部系统视频生成回调通知
     * 完全按照文档要求实现，接收格式与视频合成结果查询一致的数据结构
     *
     * @param obj 回调数据对象
     * @return 响应结果
     */
    @RequestMapping(value = "work/video-callback", method = RequestMethod.POST)
    public ResponseEntity<Response<Void>> receiveVideoCallback(
            @RequestParam(value = "callbackId", required = false) String callbackId,
            @RequestParam(value = "callbackToken", required = false) String callbackToken,
            @RequestBody DigitalHumanVideoCallbackRequestDTO callback) {
        try {
            String taskId = callback == null ? null : callback.getTaskId();
            DigitalHumanCallbackVerifier.ClaimResult claim =
                    callbackVerifier.claim(callbackId, callbackToken, taskId);
            if (claim == DigitalHumanCallbackVerifier.ClaimResult.TERMINAL) {
                return ResponseEntity.ok(Response.<Void>builder()
                        .code(ResponseCode.SUCCESS.getCode())
                        .info(ResponseCode.SUCCESS.getInfo())
                        .build());
            }
            if (claim == DigitalHumanCallbackVerifier.ClaimResult.PROCESSING) {
                return ResponseEntity.status(HttpStatus.ACCEPTED).body(Response.<Void>builder()
                        .code(ResponseCode.SUCCESS.getCode())
                        .info("Digital-human callback is already processing")
                        .build());
            }

            boolean claimHeld = true;
            try {
                if (processVideoCallback(callback)) {
                    callbackVerifier.markTerminal(callbackId, callbackToken, taskId);
                } else {
                    callbackVerifier.release(callbackId, callbackToken, taskId);
                }
                claimHeld = false;
            } catch (Exception processingError) {
                if (claimHeld) {
                    try {
                        callbackVerifier.release(callbackId, callbackToken, taskId);
                    } catch (Exception releaseError) {
                        processingError.addSuppressed(releaseError);
                        log.error("Could not release digital-human callback claim; taskId={}, exceptionType={}",
                                taskId, releaseError.getClass().getSimpleName());
                    }
                }
                throw processingError;
            }
            return ResponseEntity.ok(Response.<Void>builder()
                    .code(ResponseCode.SUCCESS.getCode())
                    .info(ResponseCode.SUCCESS.getInfo())
                    .build());
        } catch (com.hzmwmt.types.exception.AppException e) {
            HttpStatus status = ResponseCode.UNAUTHORIZED.getCode().equals(e.getCode())
                    ? HttpStatus.UNAUTHORIZED
                    : HttpStatus.BAD_REQUEST;
            if (ResponseCode.SERVICE_ERROR.getCode().equals(e.getCode())) {
                status = HttpStatus.SERVICE_UNAVAILABLE;
            }
            log.warn("Rejected digital-human callback; taskId={}, code={}",
                    callback == null ? null : callback.getTaskId(), e.getCode());
            return ResponseEntity.status(status).body(Response.<Void>builder()
                    .code(e.getCode())
                    .info(e.getInfo())
                    .build());
        } catch (Exception e) {
            log.error("Digital-human callback processing failed; taskId={}, exceptionType={}",
                    callback == null ? null : callback.getTaskId(), e.getClass().getSimpleName());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Response.<Void>builder()
                    .code(ResponseCode.SYSTEM_ERROR.getCode())
                    .info(ResponseCode.SYSTEM_ERROR.getInfo())
                    .build());
        }
    }

    /**
     * 处理视频生成回调
     * 根据文档要求，处理完整的视频生成结果数据
     *
     * @param callback provider callback payload
     */
    private boolean processVideoCallback(DigitalHumanVideoCallbackRequestDTO callback) {
        if (callback == null
                || callback.getTaskId() == null
                || !callback.getTaskId().matches("[A-Za-z0-9_-]{1,128}")
                || callback.getStatus() == null) {
            throw new AppException(ResponseCode.ILLEGAL_PARAMETER);
        }
        String taskId = callback.getTaskId().trim();
        String status = callback.getStatus().trim().toLowerCase(Locale.ROOT);
        String statusDesc = callback.getErrorMessage() != null
                ? callback.getErrorMessage().trim()
                : callback.getStatusDesc() == null ? "" : callback.getStatusDesc().trim();
        if (statusDesc.length() > 1000) {
            statusDesc = statusDesc.substring(0, 1000);
        }
        String resultUrl = callback.getResultUrl() == null ? null : callback.getResultUrl().trim();
        String name = callback.getName() == null ? "video_" + taskId : callback.getName().trim();
        Long duration = callback.getDuration();

        TDigitalWork existingWork = adminDigitalWorkMapper.selectOne(
                new LambdaQueryWrapper<TDigitalWork>().eq(TDigitalWork::getExternalId, taskId));
        if (existingWork == null || existingWork.getUserId() == null) {
            throw new AppException(
                    ResponseCode.SERVICE_ERROR.getCode(),
                    "Digital-human task is not ready for callbacks");
        }

        boolean inProgress = "processing".equals(status) || "pending".equals(status) || "ing".equals(status);
        if (inProgress) {
            return false;
        }
        boolean success = "suc".equals(status) || "success".equals(status);
        boolean failure = "fail".equals(status) || "failed".equals(status) || "error".equals(status);
        if (!success && !failure) {
            throw new AppException(
                    ResponseCode.ILLEGAL_PARAMETER.getCode(),
                    "Unsupported digital-human callback status");
        }
        int videoStatus = success ? 1 : 0;
        if (success && (resultUrl == null || resultUrl.isBlank())) {
            throw new AppException(
                    ResponseCode.ILLEGAL_PARAMETER.getCode(),
                    "Successful digital-human callback requires resultUrl");
        }
        if (existingWork.getStatus() != null) {
            if (existingWork.getStatus() == videoStatus) {
                return true;
            }
            throw new AppException(
                    ResponseCode.REPEATED_OPERATION_EXCEPTION.getCode(),
                    "Digital-human task is already terminal");
        }

        log.info("Processing authenticated digital-human callback; taskId={}, status={}", taskId, status);

        try {
            boolean isPreview = Boolean.TRUE.equals(existingWork.getIsPreview());

            // 根据状态进行不同的业务处理
            if (success) {
                log.info("视频生成成功，开始处理成功逻辑，taskId: {}，是否预览: {}，status: {}",
                        taskId, isPreview, videoStatus);

                // 将视频URL保存到数据库
                try {
                    if (taskId != null && !taskId.isEmpty() && resultUrl != null && !resultUrl.isEmpty()) {
                        // 计算视频时长（秒）- 将毫秒转换为秒
                        int durationSeconds = normalizeCallbackDuration(duration);
                        if (existingWork.getQuotaBizKey() != null)
                            digitalWorkQuotaSettlement.settle(existingWork, true, durationSeconds);

                        String videoUrl = downloadAndUploadToOSS(resultUrl, taskId);
                        Long actualUserId = existingWork.getUserId();

                        // 创建并保存DigitalWorkEntity
                        DigitalWorkEntity digitalWorkEntity = DigitalWorkEntity.builder()
                                .externalId(taskId) // 使用taskId作为externalId
                                .url(videoUrl)   // 设置视频URL
                                .duration(durationSeconds) // 设置视频时长（秒）
                                // 注释掉name字段，因为数据库中不存在该列
                                // .name(name) // 设置视频名称
                                .isPreview(isPreview) // 设置是否为预览视频
                                .status(videoStatus) // 设置视频状态为成功
                                .userId(actualUserId) // 使用从数据库查询到的实际用户ID
                                .build();

                        // 更新DigitalWorkEntity
                        updateDigitalWorkByExternalId(digitalWorkEntity);

                        log.info("视频结果保存到数据库成功，taskId: {}，duration: {}秒，是否预览: {}，status: {}",
                                taskId, durationSeconds, isPreview, videoStatus);

                        // 通过WebSocket向前端推送视频合成成功通知
                        try {
                            log.info("尝试获取NettyService并发送WebSocket消息给用户ID: {}, 任务ID: {}",
                                    digitalWorkEntity.getUserId(), taskId);

                            // 先检查Spring容器中是否存在NettyService Bean
                            boolean nettyServiceExists = SpringUtil.getApplicationContext().containsBean("nettyService");
                            log.info("Spring容器中是否存在nettyService Bean: {}", nettyServiceExists);

                            if (nettyService != null && digitalWorkEntity.getUserId() != null) {
                                String title = "视频合成成功";
                                String content = "您的视频已成功合成，点击查看详情";
                                String createTime = TimeAgoUtil.format(new java.util.Date());

                                log.info("准备发送WebSocket消息: userId={}, taskId={}",
                                        digitalWorkEntity.getUserId(), taskId);

                                boolean sent = nettyService.sendMessageToUser(
                                        digitalWorkEntity.getUserId(),
                                        title,
                                        content,
                                        createTime
                                );

                                if (sent) {
                                    log.info("WebSocket视频合成成功通知发送成功：用户ID={}, 任务ID={}",
                                            digitalWorkEntity.getUserId(), taskId);
                                } else {
                                    log.warn("WebSocket消息发送失败，可能用户不在线或连接已断开：用户ID={}, 任务ID={}",
                                            digitalWorkEntity.getUserId(), taskId);
                                }
                            } else if (digitalWorkEntity.getUserId() == null) {
                                log.warn("无法通过WebSocket推送通知，用户ID为空，任务ID={}", taskId);
                            } else {
                                log.warn("WebSocket服务未就绪，跳过视频合成通知推送，任务ID={}", taskId);
                            }
                        } catch (Exception e) {
                            // WebSocket通知失败不影响主要业务流程，仅记录日志
                            log.error("通过WebSocket推送视频合成成功通知失败，任务ID={}，异常类型={}",
                                    taskId, e.getClass().getSimpleName());
                        }
                    }
                } catch (Exception e) {
                    log.error("保存视频结果到数据库失败，taskId: {}，异常类型: {}",
                            taskId, e.getClass().getSimpleName());
                    throw new IllegalStateException("Unable to persist provider video result", e);
                }
            } else {
                log.info("视频生成失败，开始处理失败逻辑，taskId: {}，status: {}", taskId, videoStatus);

                // 处理失败逻辑，保存失败状态到数据库
                try {
                    if (taskId != null && !taskId.isEmpty()) {
                        if (existingWork.getQuotaBizKey() != null)
                            digitalWorkQuotaSettlement.settle(existingWork, false, 0);
                        Long actualUserId = existingWork.getUserId();

                        // 创建并保存DigitalWorkEntity（仅包含必要信息和失败状态）
                        DigitalWorkEntity digitalWorkEntity = DigitalWorkEntity.builder()
                                .externalId(taskId) // 使用taskId作为externalId
                                // 注释掉name字段，因为数据库中不存在该列
                                // .name(name) // 设置视频名称
                                .isPreview(isPreview) // 设置是否为预览视频
                                .status(videoStatus) // 设置视频状态为失败
                                .failureReason(statusDesc)
                                .userId(actualUserId) // 使用从数据库查询到的实际用户ID
                                .build();

                        // 更新DigitalWorkEntity
                        updateDigitalWorkByExternalId(digitalWorkEntity);

                        log.info("视频失败状态保存到数据库成功，taskId: {}，status: {}",
                                taskId, videoStatus);
                    }
                } catch (Exception e) {
                    log.error("保存视频失败状态到数据库失败，taskId: {}，异常类型: {}",
                            taskId, e.getClass().getSimpleName());
                    throw new IllegalStateException("Unable to persist provider failure result", e);
                }
            }
        } catch (Exception e) {
            log.error("处理视频回调异常，taskId: {}，status: {}，异常类型: {}",
                    taskId, status, e.getClass().getSimpleName());
            if (e instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw new IllegalStateException("Unable to process provider callback", e);
        }
        return true;
    }

    /**
     * 从URL下载视频并上传到OSS
     *
     * @param videoUrl 视频URL
     * @param taskId   任务ID
     * @return OSS上的文件URL
     */
    private String downloadAndUploadToOSS(String videoUrl, String taskId) throws IOException {
        log.info("开始下载视频并上传到OSS, taskId: {}", taskId);

        File tempFile = null;
        SafeVideoDownloader.DownloadedVideo downloadedVideo = null;

        try {
            downloadedVideo = safeVideoDownloader.download(videoUrl);
            tempFile = downloadedVideo.path().toFile();
            String extension = downloadedVideo.extension();

            log.info("视频下载完成, taskId: {}, 文件大小: {} bytes", taskId, tempFile.length());

            String contentType = downloadedVideo.contentType();

            log.info("视频MIME类型检测完成, taskId: {}, contentType: {}", taskId, contentType);

            // 确保final变量用于lambda表达式
            final File finalTempFile = tempFile;
            final String finalContentType = contentType;
            final String fileName = "video_" + taskId + extension;

            // 创建MultipartFile接口的实现
            MultipartFile multipartFile = new MultipartFile() {
                @Override
                public String getName() {
                    return "file";
                }

                @Override
                public String getOriginalFilename() {
                    return fileName;
                }

                @Override
                public String getContentType() {
                    return finalContentType;
                }

                @Override
                public boolean isEmpty() {
                    return finalTempFile.length() == 0;
                }

                @Override
                public long getSize() {
                    return finalTempFile.length();
                }

                @Override
                public byte[] getBytes() throws IOException {
                    return Files.readAllBytes(finalTempFile.toPath());
                }

                @Override
                public InputStream getInputStream() throws IOException {
                    return new FileInputStream(finalTempFile);
                }

                @Override
                public void transferTo(File dest) throws IOException, IllegalStateException {
                    Files.copy(finalTempFile.toPath(), dest.toPath());
                }
            };

            // 使用OSSAdminGateway上传文件
            // 创建上传路径：work/176231/1985894587183763456.mp4
            String objectName = String.format("work/176231/%s%s",
                    taskId, extension);

            UploadDTO uploadDTO = new UploadDTO();
            uploadDTO.setFile(multipartFile);
            uploadDTO.setPath(objectName);

            // 上传到OSS并返回URL
            String ossUrl = fileStorageGateway.upload(uploadDTO);
            log.info("视频上传到OSS成功，taskId: {}", taskId);

            return ossUrl;

        } catch (Exception e) {
            log.error("视频下载或上传到OSS失败，taskId: {}，异常类型: {}",
                    taskId, e.getClass().getSimpleName());
            throw e;
        } finally {
            if (downloadedVideo != null) {
                downloadedVideo.close();
            } else if (tempFile != null && tempFile.exists() && !tempFile.delete()) {
                log.warn("无法删除数字人回调临时文件，taskId: {}", taskId);
            }
        }
    }

    private int normalizeCallbackDuration(Long rawDuration) {
        String unit = digitalHumanApiProperties == null
                ? null
                : digitalHumanApiProperties.getCallbackDurationUnit();
        return DigitalVideoDuration.normalize(rawDuration, unit);
    }

    /**
     * 根据externalId更新DigitalWorkEntity
     *
     * @param entity 包含更新信息的DigitalWorkEntity
     */
    private void updateDigitalWorkByExternalId(DigitalWorkEntity entity) {
        try {
            log.info("开始更新DigitalWork，externalId: {}，duration: {}，status: {}",
                    entity.getExternalId(), entity.getDuration(), entity.getStatus());

            // 根据时长计算bill（1分钟以下按1分钟计算，1分钟以上按实际时间计算）
            Double bill = 0.0;

            // 检查是否是预览视频
            boolean isPreviewVideo = entity.getIsPreview() != null && entity.getIsPreview();

            if (isPreviewVideo) {
                log.info("预览视频免费, externalId: {}, isPreview: {}", entity.getExternalId(), isPreviewVideo);
                bill = 0.0; // 预览视频免费
            } else if (entity.getDuration() != null && entity.getDuration() > 0) {
                double minutes;
                if (entity.getDuration() <= 60) {
                    // 1分钟以下按1分钟计算
                    minutes = 1.0;
                } else {
                    // 1分钟以上按实际时间计算（保留小数）
                    minutes = entity.getDuration() / 60.0;
                }
                bill = minutes * 50.0;
                log.info("计算视频费用: 时长{}秒, 折合{}分钟, 费用{}元", entity.getDuration(), minutes, bill);
            }

            // 首先尝试更新现有记录
            TDigitalWork updateWork = new TDigitalWork();
            updateWork.setUrl(entity.getUrl());
            updateWork.setDuration(entity.getDuration());
            updateWork.setBill(bill);
            // 注释掉name字段，因为数据库中不存在该列
            // updateWork.setName(entity.getName());
            updateWork.setIsPreview(entity.getIsPreview());
            updateWork.setStatus(entity.getStatus()); // 设置视频状态
            updateWork.setFailureReason(entity.getFailureReason());
            updateWork.setUpdateTime(LocalDateTime.now()); // 更新时间

            // 执行更新
            int updateCount = adminDigitalWorkMapper.update(updateWork,
                    new LambdaUpdateWrapper<TDigitalWork>()
                            .eq(TDigitalWork::getExternalId, entity.getExternalId())
                            .isNull(TDigitalWork::getStatus)
                            .set(TDigitalWork::getFailureReason, entity.getFailureReason()));

            if (updateCount > 0) {
                log.info("成功更新DigitalWork，externalId: {}，bill: {}，status: {}",
                        entity.getExternalId(), bill, entity.getStatus());
            } else {
                TDigitalWork current = adminDigitalWorkMapper.selectOne(
                        new LambdaQueryWrapper<TDigitalWork>()
                                .eq(TDigitalWork::getExternalId, entity.getExternalId()));
                if (current != null && Objects.equals(current.getStatus(), entity.getStatus())) {
                    log.info("Digital-human callback was already applied, externalId={}", entity.getExternalId());
                    return;
                }
                throw new AppException(
                        ResponseCode.REPEATED_OPERATION_EXCEPTION.getCode(),
                        "Digital-human task changed concurrently");
            }
        } catch (Exception e) {
            log.error("更新/创建DigitalWork失败，externalId: {}，异常类型: {}",
                    entity.getExternalId(), e.getClass().getSimpleName());
            throw e;
        }
    }

    @Override
    @RequestMapping(value = "work/index", method = RequestMethod.GET)
    // @OperationLog(operationType = "查询", description = "查询数字人作品列表", recordParams = true)
    public Response<ApiCursorPageData<QueryDigitalWorksResponseDTO>> queryDigitalWorks(
            @RequestParam(required = false) Long lastCursorId,
            @RequestParam Integer size) {
        Long userId = authService.queryValidatedUser().getUserId();
        log.info("用户 {} 查询数字人作品列表, lastCursorId: {}, size: {}", userId, lastCursorId, size);
        CursorPageResponseData<DigitalWorkEntity> domainPage = digitalWorkService.queryDigitalWorkIndex(userId, lastCursorId, size);

        if (domainPage == null || domainPage.getData() == null) {
            return Response.<ApiCursorPageData<QueryDigitalWorksResponseDTO>>builder()
                    .code(ResponseCode.SUCCESS.getCode())
                    .info(ResponseCode.SUCCESS.getInfo())
                    .data(ApiCursorPageData.<QueryDigitalWorksResponseDTO>builder().hasNext(false).data(List.of()).build())
                    .build();
        }

        return Response.<ApiCursorPageData<QueryDigitalWorksResponseDTO>>builder()
                .code(ResponseCode.SUCCESS.getCode())
                .info(ResponseCode.SUCCESS.getInfo())
                .data(
                        ApiCursorPageData.<QueryDigitalWorksResponseDTO>builder()
                                .hasNext(domainPage.getHasNext())
                                .nextCursorId(domainPage.getNextCursorId())
                                .data(
                                        domainPage.getData().stream().map(work ->
                                                QueryDigitalWorksResponseDTO.builder()
                                                        .digitalWorkId(work.getDigitalWorkId())
                                                        .title(work.getTitle())
                                                        .body(work.getBody())
                                                        .externalId(work.getExternalId())
                                                        .status(work.getStatus())
                                                        .failureReason(work.getFailureReason())
                                                        .createTime(TimeAgoUtil.format(work.getCreateTime()))
                                                        .build()
                                        ).toList()
                                ).build()
                ).build();
    }

    @Override
    @Idempotent
    @RequestMapping(value = "work/{workId}/delete", method = RequestMethod.POST)
    @OperationLog(operationType = "删除", description = "删除数字人作品", recordParams = true)
    public Response<Void> deleteDigitalWork(@PathVariable("workId") Long digitalWorkId) {
        Long userId = authService.queryValidatedUser().getUserId();
        log.info("用户 {} 删除作品, ID: {}", userId, digitalWorkId);

        DigitalWorkEntity entity = DigitalWorkEntity.builder()
                .userId(userId)
                .digitalWorkId(digitalWorkId)
                .build();

        digitalWorkService.deleteDigitalWork(entity);

        return Response.<Void>builder()
                .code(ResponseCode.SUCCESS.getCode())
                .info(ResponseCode.SUCCESS.getInfo())
                .build();
    }

    @Override
    @Idempotent
    @RequestMapping(value = "work/update-title", method = RequestMethod.PUT)
    @OperationLog(operationType = "更新", description = "更新数字人作品标题", recordParams = true)
    public Response<Void> updateDigitalWorkTitle(@RequestBody UpdateDigitalWorkTitleRequestDTO requestDTO) {
        Long userId = authService.queryValidatedUser().getUserId();
        int titleLength = requestDTO.getTitle() == null ? 0 : requestDTO.getTitle().length();
        log.info("用户 {} 更新作品标题，ID: {}，标题长度: {}", userId,
                requestDTO.getDigitalWorkId(),
                titleLength);

        DigitalWorkEntity entity = DigitalWorkEntity.builder()
                .userId(userId)
                .digitalWorkId(requestDTO.getDigitalWorkId())
                .title(requestDTO.getTitle())
                .build();

        digitalWorkService.updateDigitalWorkTitle(entity);

        return Response.<Void>builder()
                .code(ResponseCode.SUCCESS.getCode())
                .info(ResponseCode.SUCCESS.getInfo())
                .build();
    }

    // --- 数字人声音组件接口 ---
    @Override
    @RequestMapping(value = "voice/index", method = RequestMethod.GET)
    // @OperationLog(operationType = "查询", description = "查询声音列表", recordParams = true)
    public Response<ApiCursorPageData<QueryDigitalVoicesResponseDTO>> queryVoices(
            @RequestParam(required = false) Long lastCursorId,
            @RequestParam Integer size) {
        Long userId = authService.queryValidatedUser().getUserId();
        log.info("用户 {} 查询声音首页列表, lastCursorId: {}, size: {}", userId, lastCursorId, size);
        CursorPageResponseData<DigitalVoiceAggregate> domainPage = digitalVoiceService.queryVoiceIndex(lastCursorId, size, userId);

        if (domainPage == null || domainPage.getData() == null) {
            return Response.<ApiCursorPageData<QueryDigitalVoicesResponseDTO>>builder()
                    .code(ResponseCode.SUCCESS.getCode())
                    .info(ResponseCode.SUCCESS.getInfo())
                    .data(ApiCursorPageData.<QueryDigitalVoicesResponseDTO>builder().hasNext(false).data(List.of()).build())
                    .build();
        }

        return Response.<ApiCursorPageData<QueryDigitalVoicesResponseDTO>>builder()
                .code(ResponseCode.SUCCESS.getCode())
                .info(ResponseCode.SUCCESS.getInfo())
                .data(
                        ApiCursorPageData.<QueryDigitalVoicesResponseDTO>builder()
                                .hasNext(domainPage.getHasNext())
                                .nextCursorId(domainPage.getNextCursorId())
                                .data(
                                        domainPage.getData().stream().map(agg -> {
                                            DigitalVoiceEntity entity = agg.getVoiceEntity();
                                            return QueryDigitalVoicesResponseDTO.builder()
                                                    .voiceId(entity.getVoiceId())
                                                    .name(entity.getFilteredName())
                                                    .styleName(entity.getStyleName())
                                                    .gender(entity.getVoiceGenderEnumVO().getDesc())
                                                    .isCollected(agg.getIsCollected())
                                                    .build();
                                        }).toList()
                                ).build()
                ).build();
    }

    @Override
    @RequestMapping(value = "voice/search", method = RequestMethod.GET)
    // @OperationLog(operationType = "查询", description = "高级查询声音", recordParams = true)
    public Response<ApiCursorPageData<QueryDigitalVoicesResponseDTO>> advancedQueryVoices(@RequestBody AdvancedDigitalVoiceQueryRequestDTO requestDTO) {
        Long userId = authService.queryValidatedUser().getUserId();
        log.info("用户 {} 高级筛选查询声音列表", userId);

        VoiceGenderEnumVO genderEnumVO = null;
        if (requestDTO.getVoiceGender() != null) {
            genderEnumVO = VoiceGenderEnumVO.getByCode(requestDTO.getVoiceGender());
        }

        CursorPageResponseData<DigitalVoiceAggregate> domainPage = digitalVoiceService.advancedVoiceQuery(
                requestDTO.getLastCursorId(),
                requestDTO.getSize(),
                genderEnumVO,
                requestDTO.getName(),
                userId,
                requestDTO.getDigitalVoiceCategoryIdList()
        );

        if (domainPage == null || domainPage.getData() == null) {
            return Response.<ApiCursorPageData<QueryDigitalVoicesResponseDTO>>builder()
                    .code(ResponseCode.SUCCESS.getCode())
                    .info(ResponseCode.SUCCESS.getInfo())
                    .data(ApiCursorPageData.<QueryDigitalVoicesResponseDTO>builder().hasNext(false).data(List.of()).build())
                    .build();
        }

        return Response.<ApiCursorPageData<QueryDigitalVoicesResponseDTO>>builder()
                .code(ResponseCode.SUCCESS.getCode())
                .info(ResponseCode.SUCCESS.getInfo())
                .data(
                        ApiCursorPageData.<QueryDigitalVoicesResponseDTO>builder()
                                .hasNext(domainPage.getHasNext())
                                .nextCursorId(domainPage.getNextCursorId())
                                .data(
                                        domainPage.getData().stream().map(agg -> {
                                            DigitalVoiceEntity entity = agg.getVoiceEntity();
                                            return QueryDigitalVoicesResponseDTO.builder()
                                                    .voiceId(entity.getVoiceId())
                                                    .name(entity.getFilteredName())
                                                    .styleName(entity.getStyleName())
                                                    .gender(entity.getVoiceGenderEnumVO().getDesc())
                                                    .isCollected(agg.getIsCollected())
                                                    .build();
                                        }).toList()
                                ).build()
                ).build();
    }


    @Override
    @RequestMapping(value = "voice/collections", method = RequestMethod.GET)
    // @OperationLog(operationType = "查询", description = "查询收藏的声音列表", recordParams = true)
    public Response<ApiCursorPageData<QueryCollectedDigitalVoicesResponseDTO>> queryCollectedVoices(
            @RequestParam(required = false) Long lastCursorId,
            @RequestParam Integer size) {
        Long userId = authService.queryValidatedUser().getUserId();
        log.info("用户 {} 查询收藏的声音列表, lastCursorId: {}, size: {}", userId, lastCursorId, size);
        CursorPageResponseData<DigitalVoiceEntity> domainPage = digitalVoiceService.queryVoiceCollectedIndex(lastCursorId, size, userId);

        if (domainPage == null || domainPage.getData() == null) {
            return Response.<ApiCursorPageData<QueryCollectedDigitalVoicesResponseDTO>>builder()
                    .code(ResponseCode.SUCCESS.getCode())
                    .info(ResponseCode.SUCCESS.getInfo())
                    .data(ApiCursorPageData.<QueryCollectedDigitalVoicesResponseDTO>builder().hasNext(false).data(List.of()).build())
                    .build();
        }

        return Response.<ApiCursorPageData<QueryCollectedDigitalVoicesResponseDTO>>builder()
                .code(ResponseCode.SUCCESS.getCode())
                .info(ResponseCode.SUCCESS.getInfo())
                .data(
                        ApiCursorPageData.<QueryCollectedDigitalVoicesResponseDTO>builder()
                                .hasNext(domainPage.getHasNext())
                                .nextCursorId(domainPage.getNextCursorId())
                                .data(
                                        domainPage.getData().stream().map(entity ->
                                                QueryCollectedDigitalVoicesResponseDTO.builder()
                                                        .voiceId(entity.getVoiceId())
                                                        .name(entity.getFilteredName())
                                                        .styleName(entity.getStyleName())
                                                        .gender(entity.getVoiceGenderEnumVO().getDesc())
                                                        .build()
                                        ).toList()
                                ).build()
                ).build();
    }

    @Override
    @RequestMapping(value = "voice/update-favorite-status", method = RequestMethod.POST)
    @OperationLog(operationType = "更新", description = "更新声音收藏状态", recordParams = true)
    public Response<Void> updateVoiceFavoriteStatus(@RequestBody UpdateDigitalVoiceFavoriteStatusRequestDTO requestDTO) {
        Long userId = authService.queryValidatedUser().getUserId();
        log.info("用户 {} 更新声音 {} 的收藏状态为: {}", userId, requestDTO.getVoiceId(), requestDTO.getCollected());
        digitalVoiceService.updateVoiceFavoriteStatus(userId, requestDTO.getVoiceId(), requestDTO.getCollected());
        return Response.<Void>builder()
                .code(ResponseCode.SUCCESS.getCode())
                .info(ResponseCode.SUCCESS.getInfo())
                .build();
    }

    @Override
    public Response<List<DigitalVoiceCategoryResponseDTO>> queryVoiceCategories(Integer categoryType) {
        return null;
    }

    @RequestMapping(value = "voice/{voiceId}/style-categories", method = RequestMethod.GET)
    // @OperationLog(operationType = "查询", description = "查询声音风格分类", recordParams = true)
    public Response<List<DigitalVoiceCategoryResponseDTO>> queryVoiceCategories(
            @RequestParam("type") String categoryType) {

        DigitalVoiceCategoryTypeEnumVO typeEnum = DigitalVoiceCategoryTypeEnumVO.getByCode(Integer.parseInt(categoryType));
        log.info("查询声音分类列表, 类型: {}", typeEnum.getDesc());

        List<DigitalVoiceCategoryEntity> categories = digitalVoiceService.queryDigitalVoiceCategory(typeEnum);

        return Response.<List<DigitalVoiceCategoryResponseDTO>>builder()
                .code(ResponseCode.SUCCESS.getCode())
                .info(ResponseCode.SUCCESS.getInfo())
                .data(categories.stream().map(e -> DigitalVoiceCategoryResponseDTO.builder()
                        .categoryId(e.getDigitalVoiceCategoryId())
                        .name(e.getName())
                        .categoryType(e.getDigitalVoiceCategoryTypeEnumVO().getDesc())
                        .build()).toList())
                .build();
    }


}
