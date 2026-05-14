package com.inventory.auth.security;

import com.inventory.auth.entity.User;
import com.inventory.auth.enums.Role;
import com.inventory.auth.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class CustomUserDetailsServiceTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private CustomUserDetailsService service;

    @Test
    void loadUserByUsername_mapsUserDetails() {
        User user = User.builder()
                .id(UUID.randomUUID())
                .email("john@test.com")
                .passwordHash("$2a$hashed")
                .role(Role.ADMIN)
                .enabled(true)
                .build();
        given(userRepository.findByEmail("john@test.com")).willReturn(Optional.of(user));

        var userDetails = service.loadUserByUsername("john@test.com");

        assertThat(userDetails.getUsername()).isEqualTo("john@test.com");
        assertThat(userDetails.getAuthorities()).extracting(Object::toString).contains("ROLE_ADMIN");
    }

    @Test
    void loadUserByUsername_forOauthUserUsesEmptyPassword() {
        User user = User.builder()
                .id(UUID.randomUUID())
                .email("oauth@test.com")
                .passwordHash(null)
                .role(Role.WAREHOUSE_STAFF)
                .enabled(true)
                .build();
        given(userRepository.findByEmail("oauth@test.com")).willReturn(Optional.of(user));

        var userDetails = service.loadUserByUsername("oauth@test.com");

        assertThat(userDetails.getPassword()).isEmpty();
    }

    @Test
    void loadUserByUsername_whenMissingThrows() {
        given(userRepository.findByEmail("missing@test.com")).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.loadUserByUsername("missing@test.com"))
                .isInstanceOf(UsernameNotFoundException.class)
                .hasMessageContaining("missing@test.com");
    }
}
