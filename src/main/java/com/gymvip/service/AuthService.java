package com.gymvip.service;

import com.gymvip.dto.request.LoginRequest;
import com.gymvip.dto.response.AuthResponse;

public interface AuthService {
    AuthResponse login(LoginRequest request, String ipAddress, String userAgent);
    void logout(String token);
}
