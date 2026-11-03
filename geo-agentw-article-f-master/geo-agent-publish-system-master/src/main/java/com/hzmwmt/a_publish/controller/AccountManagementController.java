package com.hzmwmt.a_publish.controller;

import com.hzmwmt.a_publish.service.PublishArticleClient;
import com.hzmwmt.common.response.ClientResult;
import jakarta.annotation.Resource;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

@RestController
@RequestMapping("/account")
public class AccountManagementController {
    @Resource
    private PublishArticleClient publishArticleClient;

    @GetMapping("/login")
    public Flux<ServerSentEvent<String>> login(@RequestParam("type")Integer type,@RequestParam("username")String username){
        return publishArticleClient.login(type,username);
    }

    @GetMapping("/getAccounts")
    public ClientResult getAccounts(){
        return publishArticleClient.getAccounts();
    }

    @PostMapping("/verifyAccount")
    public ClientResult verifyAccount(@RequestParam("id")Integer id){
        return publishArticleClient.verifyAccount(id);
    }

    @DeleteMapping("/deleteAccount")
    public ClientResult deleteAccount(@RequestParam("id")Integer id){
        return publishArticleClient.deleteAccount(id);
    }
}
