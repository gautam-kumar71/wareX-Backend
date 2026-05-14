package com.inventory.auth.entity;

import com.inventory.auth.enums.Role;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class UserTest {

    @Test
    void prePersist_assignsDefaultsAndTimestamps() {
        User user = User.builder()
                .email("john@test.com")
                .fullName("John")
                .build();

        user.prePersist();
        user.preUpdate();

        assertThat(user.getId()).isNotNull();
        assertThat(user.getRole()).isEqualTo(Role.WAREHOUSE_STAFF);
        assertThat(user.isEnabled()).isTrue();
        assertThat(user.getCreatedAt()).isNotNull();
        assertThat(user.getUpdatedAt()).isNotNull();
    }
}
