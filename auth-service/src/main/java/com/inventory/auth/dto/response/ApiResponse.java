package com.inventory.auth.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiResponse<T>(
        String timestamp,
        int status,
        String message,
        T data
) {
    public static <T> ApiResponse<T> success(T data, String message) {
        return new ApiResponse<>(Instant.now().toString(), 200, message, data);
    }

    public static <T> ApiResponse<T> success(T data) {
        return success(data, "Success");
    }

    public static <T> ApiResponse<T> error(int status, String message) {
        return new ApiResponse<>(Instant.now().toString(), status, message, null);
    }

}