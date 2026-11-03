package com.hzmwmt.domain.gateway.vedio;

import com.hzmwmt.domain.common.Result;
import com.hzmwmt.domain.gateway.vedio.dto.PostVideoRequestDto;
import com.hzmwmt.domain.gateway.vedio.dto.SubmitVerificationRequestDto;
import com.hzmwmt.domain.gateway.vedio.dto.UpdateUserInfoRequestDto;
import com.hzmwmt.domain.gateway.vedio.dto.UploadCookieRequestDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.util.List;

@Service
@Slf4j
public class VideoService implements IVideoService {

    @Autowired
    @Qualifier("vedioWebClient")
    private WebClient webClient;

    @Override
    public Result getCounts() {
        return webClient.get()
                .uri("/getAccounts")
                .retrieve()
                .bodyToMono(Result.class)
                .block();
    }

    @Override
    public Result uploadSave(MultipartFile file) {
        try {
            // 构建 multipart/form-data 请求
            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();

            // 将 MultipartFile 转换为 Resource
            ByteArrayResource fileResource = new ByteArrayResource(file.getBytes()) {
                @Override
                public String getFilename() {
                    return file.getOriginalFilename();
                }
            };

            body.add("file", fileResource);

            return webClient.post()
                    .uri("/uploadSave")
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(BodyInserters.fromMultipartData(body))
                    .retrieve()
                    .onStatus(status -> status.isError(), response ->
                            response.bodyToMono(String.class)
                                    .flatMap(error -> Mono.error(
                                            new RuntimeException("上传失败: " + response.statusCode() + " - " + error)
                                    ))
                    )
                    .bodyToMono(Result.class)
                    .block(); // 这里可以用 block()，因为已经是 Service 层

        } catch (IOException e) {
            throw new RuntimeException("文件读取失败", e);
        }
    }

    @Override
    public Resource getFile(String filename) {
        return webClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/getFile")
                        .queryParam("filename", filename)
                        .build())
                .retrieve()
                .bodyToMono(Resource.class)
                .doOnError(error -> log.error("获取素材文件失败"))
                .block();
    }

    @Override
    public Result getFiles() {
        return webClient.get()
                .uri("/getFiles")
                .retrieve()
                .bodyToMono(Result.class)
                .block();
    }

    @Override
    public Result getValidAccounts() {
        return webClient.get()
                .uri("/getValidAccounts")
                .retrieve()
                .bodyToMono(Result.class)
                .block();
    }

    @Override
    public Result deleteFile(Integer id) {
        return webClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/deleteFile")
                        .queryParam("id", id)
                        .build())
                .retrieve()
                .bodyToMono(Result.class)
                .block();
    }

    @Override
    public Result deleteAccount(Integer id) {
        return webClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/deleteAccount")
                        .queryParam("id", id)
                        .build())
                .retrieve()
                .bodyToMono(Result.class)
                .block();
    }

    @Override
    public Flux<ServerSentEvent<String>> login(Integer type, String username) {
        return webClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/login")
                        .queryParam("type", type)
                        .queryParam("username", username)
                        .build())
                .accept(MediaType.TEXT_EVENT_STREAM)
                .retrieve()
                .bodyToFlux(new ParameterizedTypeReference<ServerSentEvent<String>>() {
                });
    }

    @Override
    public Result updateUserinfo(UpdateUserInfoRequestDto updateUserInfoRequestDto) {
        return webClient.post()
                .uri("/updateUserinfo")
                .bodyValue(updateUserInfoRequestDto)
                .retrieve()
                .bodyToMono(Result.class)
                .block();
    }

    @Override
    public Result postVideo(PostVideoRequestDto postVideoRequestDto) {
        return webClient.post()
                .uri("/postVideo")
                .bodyValue(postVideoRequestDto)
                .retrieve()
                .bodyToMono(Result.class)
                .block();
    }

    @Override
    public Result postVideoBatch(List<PostVideoRequestDto> list) {
        return webClient.post()
                .uri("/postVideoBatch")
                .bodyValue(list)
                .retrieve()
                .bodyToMono(Result.class)
                .block();
    }

    @Override
    public Result uploadCookie(UploadCookieRequestDto dto) {
        try {
            MultipartBodyBuilder builder = new MultipartBodyBuilder();

            builder.part("file", new ByteArrayResource(dto.getFile().getBytes()) {
                @Override
                public String getFilename() {
                    return dto.getFile().getOriginalFilename();
                }
            });

            builder.part("id", dto.getId() != null ? dto.getId().toString() : "");
            builder.part("platform", dto.getPlatform() != null ? dto.getPlatform() : "");

            return webClient.post()
                    .uri("/uploadCookie")
                    .body(BodyInserters.fromMultipartData(builder.build()))
                    .retrieve()
                    .bodyToMono(Result.class)
                    .block();

        } catch (IOException e) {
            throw new RuntimeException("文件读取失败", e);
        }
    }

    @Override
    public byte[] downloadCookie(String filePath) {
        return webClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/downloadCookie")
                        .queryParam("filePath", filePath)
                        .build())
                .retrieve()
                .bodyToMono(byte[].class)
                .doOnError(error -> log.error("获取cookie文件失败"))
                .block();
    }

    @Override
    public Result verifyAccount(Integer id) {
        return webClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/verifyAccount")
                        .queryParam("id", id)
                        .build())
                .retrieve()
                .bodyToMono(Result.class)
                .block();
    }

    @Override
    public Result submitVerification(SubmitVerificationRequestDto dto) {
        return webClient.post()
                .uri("/submit_verification")
                .bodyValue(dto)
                .retrieve()
                .bodyToMono(Result.class)
                .block();
    }
}