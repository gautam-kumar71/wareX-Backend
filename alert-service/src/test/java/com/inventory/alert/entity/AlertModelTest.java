package com.inventory.alert.entity;

import com.inventory.alert.dto.response.AlertResponse;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AlertModelTest {

    @Test
    void onCreate_setsCreatedAt() {
        Alert alert = Alert.builder()
                .title("Title")
                .message("Message")
                .type("SYSTEM")
                .build();

        alert.onCreate();

        assertThat(alert.getCreatedAt()).isNotNull();
    }

    @Test
    void alertResponse_normalizesTypes() {
        Alert alert = Alert.builder()
                .id(8L)
                .title("Payment")
                .message("Issue")
                .type("payment")
                .isRead(true)
                .build();

        AlertResponse response = AlertResponse.from(alert);

        assertThat(response.type()).isEqualTo("CRITICAL");
        assertThat(response.read()).isTrue();
    }
}
