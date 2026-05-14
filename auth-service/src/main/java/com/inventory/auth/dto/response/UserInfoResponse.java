package com.inventory.auth.dto.response;

import com.inventory.auth.enums.Role;

public record UserInfoResponse(
        String userId,
        String email,
        String fullName,
        Role   role,
        boolean isOAuth2User
) {}
