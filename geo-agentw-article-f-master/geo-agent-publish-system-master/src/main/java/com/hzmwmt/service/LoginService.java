package com.hzmwmt.service;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import com.hzmwmt.model.dto.LoginRequest;
import com.hzmwmt.model.dto.LoginResponse;

public interface LoginService {

    LoginResponse login(LoginRequest loginRequest, HttpServletRequest request, HttpSession session);

    void logout(HttpSession session);

    boolean isLoggedIn(HttpSession session);

    LoginResponse status(HttpSession session);
}
