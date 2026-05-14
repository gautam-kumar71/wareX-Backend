package com.inventory.auth.security.oauth2;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GoogleOAuth2UserInfoTest {

    @Test
    void wrapsGooglePayloadClaims() {
        GoogleIdToken.Payload payload = new GoogleIdToken.Payload();
        payload.setSubject("sub-1");
        payload.setEmail("john@test.com");
        payload.setEmailVerified(true);
        payload.put("name", "John");
        payload.put("picture", "pic");

        GoogleOAuth2UserInfo info = new GoogleOAuth2UserInfo(payload);

        assertThat(info.sub()).isEqualTo("sub-1");
        assertThat(info.email()).isEqualTo("john@test.com");
        assertThat(info.emailVerified()).isTrue();
        assertThat(info.name()).isEqualTo("John");
        assertThat(info.pictureUrl()).isEqualTo("pic");
    }
}
