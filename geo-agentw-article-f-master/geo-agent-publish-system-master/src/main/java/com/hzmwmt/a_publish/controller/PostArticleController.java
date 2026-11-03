package com.hzmwmt.a_publish.controller;

import com.hzmwmt.a_publish.model.dto.PostArticleDTO;
import com.hzmwmt.a_publish.service.PublishArticleClient;
import com.hzmwmt.common.response.ClientResult;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/postArticle")
public class PostArticleController {
    @Resource
    private PublishArticleClient publishArticleClient;
    @PostMapping("/postArticle")
    public ClientResult postArticle(@RequestBody PostArticleDTO postArticleDTO){
        return publishArticleClient.postArticle(postArticleDTO);
    }
}
