package com.hzmwmt.domain.gateway.vedio;

import com.hzmwmt.domain.common.Result;
import com.hzmwmt.domain.gateway.vedio.dto.PostVideoRequestDto;
import com.hzmwmt.domain.gateway.vedio.dto.SubmitVerificationRequestDto;
import com.hzmwmt.domain.gateway.vedio.dto.UpdateUserInfoRequestDto;
import com.hzmwmt.domain.gateway.vedio.dto.UploadCookieRequestDto;
import org.springframework.core.io.Resource;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.multipart.MultipartFile;
import reactor.core.publisher.Flux;

import java.util.List;

public interface IVideoService {
    public Result getCounts();

    Result uploadSave(MultipartFile file);

    Resource getFile(String filename);

    Result getFiles();

    Result getValidAccounts();

    Result deleteFile(Integer id);

    Result deleteAccount(Integer id);

    Flux<ServerSentEvent<String>> login(Integer type, String username);

    Result updateUserinfo(UpdateUserInfoRequestDto updateUserInfoRequestDto);

    Result postVideo(PostVideoRequestDto postVideoRequestDto);

    Result postVideoBatch(List<PostVideoRequestDto> list);

    Result uploadCookie(UploadCookieRequestDto dto);

    byte[] downloadCookie(String filePath);

    Result verifyAccount(Integer id);

    Result submitVerification(SubmitVerificationRequestDto dto);
}
