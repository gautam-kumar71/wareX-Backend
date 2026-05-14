package com.inventory.stockmovement.dto.response;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ApiResponseTest {

    @Test
    void success_buildsStandardPayload() {
        ApiResponse<String> response = ApiResponse.success("ok");

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getMessage()).isEqualTo("Success");
        assertThat(response.getData()).isEqualTo("ok");
        assertThat(response.getTimestamp()).isNotBlank();
    }

    @Test
    void error_buildsErrorPayload() {
        ApiResponse<Void> response = ApiResponse.error(400, "bad");

        assertThat(response.getStatus()).isEqualTo(400);
        assertThat(response.getMessage()).isEqualTo("bad");
        assertThat(response.getData()).isNull();
        assertThat(response.getTimestamp()).isNotBlank();
    }
}
