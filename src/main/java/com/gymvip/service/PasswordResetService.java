package com.gymvip.service;

import com.gymvip.dto.request.PasswordResetConfirmRequest;
import com.gymvip.dto.request.PasswordResetRequest;
import com.gymvip.dto.response.PasswordResetConfirmResponse;
import com.gymvip.dto.response.PasswordResetResponse;

public interface PasswordResetService {

    PasswordResetResponse requestReset(PasswordResetRequest request, String ipAddress);

    PasswordResetConfirmResponse confirmReset(PasswordResetConfirmRequest request, String ipAddress);
}
