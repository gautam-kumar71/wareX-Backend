package com.inventory.auth.controller;

import com.inventory.auth.dto.request.OAuth2TokenRequest;
import com.inventory.auth.dto.response.AuthResponse;
import com.inventory.auth.service.OAuth2AuthService;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

class OAuth2AuthControllerTest {

    @Test
    void googleLogin_delegatesAndWrapsResponse() {
        OAuth2AuthService service = mock(OAuth2AuthService.class);
        OAuth2AuthController controller = new OAuth2AuthController(service);
        AuthResponse response = new AuthResponse("access", "refresh", 900);
        given(service.loginWithGoogle(new OAuth2TokenRequest("id-token"))).willReturn(response);

        assertThat(controller.googleLogin(new OAuth2TokenRequest("id-token")).getBody().message())
                .isEqualTo("Google authentication successful");
    }
}
