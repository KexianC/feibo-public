package com.hzmwmt.controller;

import com.hzmwmt.common.exception.AppException;
import com.hzmwmt.common.response.Result;
import com.hzmwmt.model.dto.LoginRequest;
import com.hzmwmt.model.dto.LoginResponse;
import com.hzmwmt.service.LoginService;
import jakarta.annotation.Resource;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import jakarta.servlet.http.HttpSession;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.util.StringUtils;

@RestController
@RequestMapping("/login")
@ConditionalOnProperty(prefix = "app.geo-integration", name = "enabled", havingValue = "false")
public class LoginController {

    @Resource
    private LoginService loginService;

    @PostMapping
    public Result<LoginResponse> login(@RequestBody LoginRequest loginRequest, HttpServletRequest request, HttpServletResponse response) {
        if (loginRequest == null || !StringUtils.hasText(loginRequest.getPassword())) {
            throw new AppException("PARAM_INVALID", "Password is required.");
        }
        HttpSession session = request.getSession(true);
        LoginResponse loginResponse = loginService.login(loginRequest, request, session);
        applySessionCookie(request, response, session, Boolean.TRUE.equals(loginRequest.getRememberMe()));
        return Result.success(loginResponse);
    }

    @GetMapping("/status")
    public Result<LoginResponse> loginStatus(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        return Result.success(loginService.status(session));
    }

    @PostMapping("/logout")
    public Result<Void> logout(HttpServletRequest request, HttpServletResponse response) {
        loginService.logout(request.getSession(false));
        clearSessionCookie(request, response);
        return Result.success();
    }

    private void applySessionCookie(HttpServletRequest request, HttpServletResponse response, HttpSession session, boolean rememberMe) {
        Cookie cookie = new Cookie("JSESSIONID", session.getId());
        cookie.setHttpOnly(true);
        cookie.setSecure(request.isSecure());
        cookie.setPath("/");
        if (rememberMe) {
            cookie.setMaxAge(Math.max(1, session.getMaxInactiveInterval()));
        }
        response.addCookie(cookie);
    }

    private void clearSessionCookie(HttpServletRequest request, HttpServletResponse response) {
        Cookie cookie = new Cookie("JSESSIONID", "");
        cookie.setHttpOnly(true);
        cookie.setSecure(request.isSecure());
        cookie.setPath("/");
        cookie.setMaxAge(0);
        response.addCookie(cookie);
    }
}
