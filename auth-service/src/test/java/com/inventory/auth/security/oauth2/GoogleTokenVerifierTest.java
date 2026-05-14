package com.inventory.auth.security.oauth2;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.json.webtoken.JsonWebSignature;
import com.inventory.auth.exception.OAuth2AuthenticationException;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

class GoogleTokenVerifierTest {

    @Test
    void verify_returnsPayloadWhenVerifierAcceptsToken() throws Exception {
        GoogleTokenVerifier verifier = new GoogleTokenVerifier("client-id");
        GoogleIdTokenVerifier delegate = mock(GoogleIdTokenVerifier.class);
        GoogleIdToken.Payload payload = new GoogleIdToken.Payload();
        payload.setSubject("sub-1");
        GoogleIdToken token = new GoogleIdToken(new JsonWebSignature.Header(), payload, new byte[0], new byte[0]);
        ReflectionTestUtils.setField(verifier, "verifier", delegate);
        given(delegate.verify("id-token")).willReturn(token);

        assertThat(verifier.verify("id-token").getSubject()).isEqualTo("sub-1");
    }

    @Test
    void verify_nullTokenThrowsFriendlyError() throws Exception {
        GoogleTokenVerifier verifier = new GoogleTokenVerifier("client-id");
        GoogleIdTokenVerifier delegate = mock(GoogleIdTokenVerifier.class);
        ReflectionTestUtils.setField(verifier, "verifier", delegate);
        given(delegate.verify("id-token")).willReturn(null);

        assertThatThrownBy(() -> verifier.verify("id-token"))
                .isInstanceOf(OAuth2AuthenticationException.class)
                .hasMessageContaining("invalid");
    }

    @Test
    void verify_unexpectedFailureIsWrapped() throws Exception {
        GoogleTokenVerifier verifier = new GoogleTokenVerifier("client-id");
        GoogleIdTokenVerifier delegate = mock(GoogleIdTokenVerifier.class);
        ReflectionTestUtils.setField(verifier, "verifier", delegate);
        given(delegate.verify("id-token")).willThrow(new RuntimeException("network down"));

        assertThatThrownBy(() -> verifier.verify("id-token"))
                .isInstanceOf(OAuth2AuthenticationException.class)
                .hasMessageContaining("network down");
    }
}
