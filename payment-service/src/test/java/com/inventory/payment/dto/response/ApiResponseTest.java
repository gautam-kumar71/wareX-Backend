package com.inventory.payment.dto.response;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ApiResponseTest {

    @Test
    void successFactoriesPopulateDefaults() {
        ApiResponse<String> explicit = ApiResponse.success("data", "done");
        ApiResponse<String> implicit = ApiResponse.success("data");

        assertThat(explicit.status()).isEqualTo(200);
        assertThat(explicit.message()).isEqualTo("done");
        assertThat(explicit.data()).isEqualTo("data");
        assertThat(explicit.timestamp()).isNotBlank();

        assertThat(implicit.message()).isEqualTo("Success");
        assertThat(implicit.data()).isEqualTo("data");
    }

    @Test
    void errorFactoryLeavesDataNull() {
        ApiResponse<Void> response = ApiResponse.error(500, "failed");

        assertThat(response.status()).isEqualTo(500);
        assertThat(response.message()).isEqualTo("failed");
        assertThat(response.data()).isNull();
        assertThat(response.timestamp()).isNotBlank();
    }
}
