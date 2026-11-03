package com.hzmwmt.a_publish.service.impl;

import com.hzmwmt.a_publish.model.dto.PostArticleDTO;
import com.hzmwmt.a_publish.service.PublishArticleClient;
import com.hzmwmt.common.context.LoginContextHolder;
import com.hzmwmt.common.exception.AppException;
import com.hzmwmt.common.response.ClientResult;
import jakarta.annotation.Resource;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;

import java.util.function.Consumer;

@Service
public class PublishArticleClientImpl implements PublishArticleClient {


    @Resource
    private WebClient publishClient;


    @Override
    public Flux<ServerSentEvent<String>> login(Integer type, String username) {
        return publishClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/login")
                        .queryParam("type",type)
                        .queryParam("username",username)
                        .build())
                .accept(MediaType.TEXT_EVENT_STREAM)
                .headers(capturedUserHeaders())
                .retrieve()
                .bodyToFlux(new ParameterizedTypeReference<ServerSentEvent<String>>() {});
    }

    @Override
    public ClientResult getAccounts() {
        return publishClient.get()
                .uri("/getAccounts")
                .headers(capturedUserHeaders())
                .retrieve()
                .bodyToMono(ClientResult.class)
                .block();
    }

    @Override
    public ClientResult verifyAccount(Integer id) {
        return publishClient.post()
                .uri(uriBuilder -> uriBuilder
                        .path("/verifyAccount")
                        .queryParam("id",id)
                        .build())
                .headers(capturedUserHeaders())
                .retrieve()
                .bodyToMono(ClientResult.class)
                .block();
    }

    @Override
    public ClientResult deleteAccount(Integer id) {
        return publishClient.delete()
                .uri(uriBuilder -> uriBuilder
                        .path("/deleteAccount")
                        .queryParam("id",id)
                        .build())
                .headers(capturedUserHeaders())
                .retrieve()
                .bodyToMono(ClientResult.class)
                .block();
    }

    @Override
    public ClientResult uploadSave(MultipartFile file) {
        // 先创建 Multipart 请求体
        MultipartBodyBuilder body = new MultipartBodyBuilder();
        // 将 MultipartFile 转换为 Resource 并添加到表单字段 "file"
        body.part("file", file.getResource());

        return publishClient.post()
                .uri("/uploadSave")
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .headers(capturedUserHeaders())
                .body(BodyInserters.fromMultipartData(body.build()))
                .retrieve()
                .bodyToMono(ClientResult.class)
                .block();
    }

    @Override
    public ClientResult getFiles() {
        return publishClient.get()
                .uri("/getFiles")
                .headers(capturedUserHeaders())
                .retrieve()
                .bodyToMono(ClientResult.class)
                .block();
    }

    @Override
    public ClientResult deleteFile(Integer id) {
        return publishClient.delete()
                .uri(uriBuilder -> uriBuilder
                        .path("/deleteFile")
                        .queryParam("id", id)
                        .build())
                .headers(capturedUserHeaders())
                .retrieve()
                .bodyToMono(ClientResult.class)
                .block();
    }

    @Override
    public ClientResult postArticle(PostArticleDTO postArticleDTO) {
        return publishClient.post()
                .uri("/postArticle")
                .headers(capturedUserHeaders())
                .bodyValue(postArticleDTO)
                .retrieve()
                .bodyToMono(ClientResult.class)
                .block();
    }

    Consumer<HttpHeaders> capturedUserHeaders() {
        LoginContextHolder.LoginUser user = LoginContextHolder.currentUser();
        if (user == null || !user.loggedIn() || user.userId() == null) {
            throw new AppException(
                    "UNAUTHORIZED",
                    "Authenticated user identity is required for publishing.",
                    HttpStatus.UNAUTHORIZED
            );
        }
        String userId = user.userId().toString();
        return headers -> headers.set("userid", userId);
    }
}
