package com.hzmwmt.domain.controller.function;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hzmwmt.domain.auth.service.IAuthService;
import com.hzmwmt.domain.common.Result;
import com.hzmwmt.domain.gateway.vedio.IVideoService;
import com.hzmwmt.domain.gateway.vedio.dto.PostVideoRequestDto;
import com.hzmwmt.domain.gateway.vedio.dto.SubmitVerificationRequestDto;
import com.hzmwmt.domain.gateway.vedio.dto.UpdateUserInfoRequestDto;
import com.hzmwmt.domain.gateway.vedio.dto.UploadCookieRequestDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import reactor.core.publisher.Flux;

import java.util.List;

@Tag(name = "多平台视频发布接口", description = "提供接口调用python后台服务实现多平台视频发布功能")
@RestController
@RequestMapping("/api/v1/video-publish")
public class VideoController {
    @Autowired
    private IVideoService vedioService;

    @Autowired
    private IAuthService authService;

    @Operation(summary = "获取所有账号信息", description = "获取所有已登录的第三方媒体平台的账号信息，将返回包含账号信息的数组")
    @GetMapping("/getAccounts")
    public Result getCounts() {
        return vedioService.getCounts();
    }

    @Operation(summary = "上传文件并保存", description = "上传文件视频到服务器，返回文件名称和文件地址")
    @PostMapping("/uploadSave")
    public Result uploadSave(@RequestPart("file") MultipartFile file) {
        return vedioService.uploadSave(file);
    }

    @Operation(summary = "获取单个文件素材", description = "请求文件名称，返回整个文件")
    @GetMapping("/getFile")
    public ResponseEntity<Resource> getFile(@RequestParam("filename") String filename) {
        try {
            Resource resource = vedioService.getFile(filename);

            if (resource == null || !resource.exists()) {
                return ResponseEntity.notFound().build();
            }

            // 设置响应头
            HttpHeaders headers = new HttpHeaders();

            // 设置Content-Type为二进制流
            headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);

            // 设置Content-Disposition，强制浏览器下载
            headers.setContentDisposition(
                    ContentDisposition.attachment()
                            .filename(filename)
                            .build()
            );

            // 尝试设置Content-Length
            long contentLength = resource.contentLength();
            headers.setContentLength(contentLength);

            return ResponseEntity.ok()
                    .headers(headers)
                    .body(resource);

        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    @Operation(summary = "获取所有素材文件", description = "")
    @GetMapping("/getFiles")
    public Result getFiles(){
        return vedioService.getFiles();
    }

    @Operation(summary = "先校验后获取所有账号", description = "先校验一遍所有账号的可用性，再获取所有第三方媒体平台的账号")
    @GetMapping("/getValidAccounts")
    public Result getValidAccounts(){
        return vedioService.getValidAccounts();
    }

    @Operation(summary = "根据素材文件id删除素材文件", description = "根据素材文件id删除素材文件")
    @GetMapping("/deleteFile")
    public Result deleteFile(@RequestParam("id") Integer id){
        return vedioService.deleteFile(id);
    }

    @Operation(summary = "删除已登录的第三方媒体平台账号",description = "删除已登录的第三方媒体平台账号")
    @GetMapping("/deleteAccount")
    public Result deleteAccount(@RequestParam("id") Integer id){
        return vedioService.deleteAccount(id);
    }

    @Operation(summary = "登录第三方视频账号", description = "登录第三方视频账号，使用sse流式传输，第一步会返回一张二维码图片用于登录，第二步会返回200的状态码，当前端收到状态码就应该断开sse连接")
    @GetMapping("/login")
    public Flux<ServerSentEvent<String>> login(@RequestParam("type") Integer type, @RequestParam("username") String userName){
        return vedioService.login(type,userName);
    }

    @PostMapping("/updateUserinfo")
    public Result updateUserinfo(@RequestBody UpdateUserInfoRequestDto updateUserInfoRequestDto){
        return vedioService.updateUserinfo(updateUserInfoRequestDto);
    }

    @PostMapping("/postVideo")
    public Result postVideo(@RequestBody PostVideoRequestDto postVideoRequestDto){
        return vedioService.postVideo(postVideoRequestDto);
    }

    @PostMapping("/postVideoBatch")
    public Result postVideoBatch(@RequestBody List<PostVideoRequestDto> list){
        return vedioService.postVideoBatch(list);
    }

    @PostMapping(value = "/uploadCookie", consumes = {MediaType.MULTIPART_FORM_DATA_VALUE, "application/octet-stream"})
    public Result uploadCookie( @RequestPart("file") MultipartFile file,
                                @RequestPart("dto") String dtoJson){
        ObjectMapper objectMapper = new ObjectMapper();
        try {
            UploadCookieRequestDto dto = objectMapper.readValue(dtoJson, UploadCookieRequestDto.class);
            dto.setFile(file);
            return vedioService.uploadCookie(dto);
        } catch (JsonProcessingException e) {
            return Result.error("JSON格式错误，请检查请求参数");
        }
    }

    @GetMapping("/downloadCookie")
    public ResponseEntity<Resource> downloadCookie(@RequestParam("filePath") String filePath){
        try {
            // 1. 调用Service获取文件内容
            byte[] fileContent = vedioService.downloadCookie(filePath);

            if (fileContent == null || fileContent.length == 0) {
                return ResponseEntity.notFound().build();
            }

            // 2. 将字节数组包装为Resource
            ByteArrayResource resource = new ByteArrayResource(fileContent);

            // 3. 设置响应头
            HttpHeaders headers = new HttpHeaders();

            // 设置Content-Type为二进制流
            headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);

            // 设置Content-Disposition，告诉浏览器这是附件，需要下载
            // attachment 表示附件，filename指定下载时的文件名
            headers.setContentDisposition(
                    ContentDisposition.attachment()
                            .filename(filePath)  // 可以根据实际文件类型调整
                            .build()
            );

            // 设置文件大小
            headers.setContentLength(fileContent.length);

            // 4. 返回响应
            return ResponseEntity.ok()
                    .headers(headers)
                    .body(resource);

        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    @GetMapping("/verifyAccount")
    public Result verifyAccount(@RequestParam("id")Integer id){
        return vedioService.verifyAccount(id);
    }

    @PostMapping("/submit_verification")
    public Result submitVerification(@RequestBody@Valid SubmitVerificationRequestDto dto){return vedioService.submitVerification(dto);}
}
