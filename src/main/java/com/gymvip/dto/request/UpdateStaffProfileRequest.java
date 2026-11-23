package com.gymvip.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateStaffProfileRequest(
        @NotBlank @Email String email,
        @Size(max = 120) String firstName,
        @Size(max = 120) String lastName,
        @Size(max = 30) String phone
) {}
