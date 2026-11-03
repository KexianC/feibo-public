package com.hzmwmt.a_publish.service;

import com.hzmwmt.a_publish.model.dto.PostArticleDTO;
import com.hzmwmt.common.response.ClientResult;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.multipart.MultipartFile;
import reactor.core.publisher.Flux;

public interface PublishArticleClient {
    Flux<ServerSentEvent<String>> login(Integer type, String username);

    ClientResult getAccounts();

    ClientResult verifyAccount(Integer id);

    ClientResult deleteAccount(Integer id);

    ClientResult uploadSave(MultipartFile file);

    ClientResult getFiles();

    ClientResult deleteFile(Integer id);

    ClientResult postArticle(PostArticleDTO postArticleDTO);
}
