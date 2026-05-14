package com.inventory.auth.service;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.inventory.auth.dto.request.OAuth2TokenRequest;
import com.inventory.auth.dto.response.AuthResponse;
import com.inventory.auth.entity.OAuth2Account;
import com.inventory.auth.entity.User;
import com.inventory.auth.enums.Role;
import com.inventory.auth.exception.OAuth2AuthenticationException;
import com.inventory.auth.repository.OAuth2AccountRepository;
import com.inventory.auth.repository.UserRepository;
import com.inventory.auth.security.oauth2.GoogleOAuth2UserInfo;
import com.inventory.auth.security.oauth2.GoogleTokenVerifier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class OAuth2AuthServiceTest {

    private static final String ID_TOKEN = "id-token";
    private static final String GOOGLE_PROVIDER = "google";

    @Mock
    private GoogleTokenVerifier googleTokenVerifier;

    @Mock
    private UserRepository userRepo;

    @Mock
    private OAuth2AccountRepository oauthRepo;

    @Mock
    private AuthService authService;

    @InjectMocks
    private OAuth2AuthService service;

    @Test
    void loginWithGoogle_unverifiedEmailThrows() {
        mockVerifiedToken("sub-1", "john@test.com", false, "John", null);

        assertThatThrownBy(() -> service.loginWithGoogle(request()))
                .isInstanceOf(OAuth2AuthenticationException.class)
                .hasMessageContaining("not verified");
    }

    @Test
    void loginWithGoogle_returningUserUpdatesPictureAndBuildsTokens() {
        UUID userId = UUID.randomUUID();
        OAuth2Account account = account(userId, "sub-1", "john@test.com", "old-pic");
        User user = user(userId, "john@test.com", true);
        AuthResponse response = new AuthResponse("access", "refresh", 900);

        mockVerifiedToken("sub-1", "john@test.com", true, "John", "new-pic");
        given(oauthRepo.findByProviderAndProviderId(GOOGLE_PROVIDER, "sub-1")).willReturn(Optional.of(account));
        given(userRepo.findById(userId)).willReturn(Optional.of(user));
        given(authService.buildTokensForUser(user)).willReturn(response);

        assertThat(service.loginWithGoogle(request())).isSameAs(response);
        assertThat(account.getPictureUrl()).isEqualTo("new-pic");
        verify(oauthRepo).save(account);
    }

    @Test
    void loginWithGoogle_returningUserWithoutPictureChangeSkipsSave() {
        UUID userId = UUID.randomUUID();
        OAuth2Account account = account(userId, "sub-1", "john@test.com", "same-pic");
        User user = user(userId, "john@test.com", true);
        AuthResponse response = new AuthResponse("access", "refresh", 900);

        mockVerifiedToken("sub-1", "john@test.com", true, "John", "same-pic");
        given(oauthRepo.findByProviderAndProviderId(GOOGLE_PROVIDER, "sub-1")).willReturn(Optional.of(account));
        given(userRepo.findById(userId)).willReturn(Optional.of(user));
        given(authService.buildTokensForUser(user)).willReturn(response);

        assertThat(service.loginWithGoogle(request())).isSameAs(response);
        verify(oauthRepo, never()).save(any(OAuth2Account.class));
    }

    @Test
    void loginWithGoogle_returningDisabledUserThrows() {
        UUID userId = UUID.randomUUID();
        OAuth2Account account = account(userId, "sub-1", "john@test.com", null);
        User user = user(userId, "john@test.com", false);

        mockVerifiedToken("sub-1", "john@test.com", true, "John", null);
        given(oauthRepo.findByProviderAndProviderId(GOOGLE_PROVIDER, "sub-1")).willReturn(Optional.of(account));
        given(userRepo.findById(userId)).willReturn(Optional.of(user));

        assertThatThrownBy(() -> service.loginWithGoogle(request()))
                .isInstanceOf(OAuth2AuthenticationException.class)
                .hasMessageContaining("disabled");
    }

    @Test
    void loginWithGoogle_missingLinkedUserThrows() {
        UUID userId = UUID.randomUUID();
        OAuth2Account account = account(userId, "sub-1", "john@test.com", null);

        mockVerifiedToken("sub-1", "john@test.com", true, "John", null);
        given(oauthRepo.findByProviderAndProviderId(GOOGLE_PROVIDER, "sub-1")).willReturn(Optional.of(account));
        given(userRepo.findById(userId)).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.loginWithGoogle(request()))
                .isInstanceOf(OAuth2AuthenticationException.class)
                .hasMessageContaining("Linked user account not found");
    }

    @Test
    void loginWithGoogle_existingPasswordUserLinksAccount() {
        UUID userId = UUID.randomUUID();
        User user = user(userId, "john@test.com", true);
        AuthResponse response = new AuthResponse("access", "refresh", 900);

        mockVerifiedToken("sub-1", " JOHN@test.com ", true, "John", "pic");
        given(oauthRepo.findByProviderAndProviderId(GOOGLE_PROVIDER, "sub-1")).willReturn(Optional.empty());
        given(userRepo.findByEmail("john@test.com")).willReturn(Optional.of(user));
        given(authService.buildTokensForUser(user)).willReturn(response);

        assertThat(service.loginWithGoogle(request())).isSameAs(response);

        ArgumentCaptor<OAuth2Account> captor = ArgumentCaptor.forClass(OAuth2Account.class);
        verify(oauthRepo).save(captor.capture());
        assertThat(captor.getValue().getUserId()).isEqualTo(userId);
        assertThat(captor.getValue().getProviderId()).isEqualTo("sub-1");
    }

    @Test
    void loginWithGoogle_newUserCreatesAccountAndLinksGoogle() {
        AuthResponse response = new AuthResponse("access", "refresh", 900);
        mockVerifiedToken("sub-2", "new@test.com", true, "New User", "pic");
        given(oauthRepo.findByProviderAndProviderId(GOOGLE_PROVIDER, "sub-2")).willReturn(Optional.empty());
        given(userRepo.findByEmail("new@test.com")).willReturn(Optional.empty());
        given(userRepo.save(any(User.class))).willAnswer(invocation -> invocation.getArgument(0));
        given(authService.buildTokensForUser(any(User.class))).willReturn(response);

        assertThat(service.loginWithGoogle(request())).isSameAs(response);

        verify(userRepo).save(any(User.class));
        verify(oauthRepo).save(any(OAuth2Account.class));
    }

    @Test
    void loginWithGoogle_newUserFallsBackToNormalizedEmailWhenNameMissing() {
        AuthResponse response = new AuthResponse("access", "refresh", 900);
        mockVerifiedToken("sub-2", "New@Test.com", true, "   ", "pic");
        given(oauthRepo.findByProviderAndProviderId(GOOGLE_PROVIDER, "sub-2")).willReturn(Optional.empty());
        given(userRepo.findByEmail("new@test.com")).willReturn(Optional.empty());
        given(userRepo.save(any(User.class))).willAnswer(invocation -> invocation.getArgument(0));
        given(authService.buildTokensForUser(any(User.class))).willReturn(response);

        service.loginWithGoogle(request());

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepo).save(captor.capture());
        assertThat(captor.getValue().getFullName()).isEqualTo("new@test.com");
    }

    @Test
    void loginWithGoogle_alreadyLinkedDuringNewFlowSkipsDuplicateInsert() {
        UUID userId = UUID.randomUUID();
        User user = user(userId, "john@test.com", true);
        AuthResponse response = new AuthResponse("access", "refresh", 900);

        mockVerifiedToken("sub-3", "john@test.com", true, "John", "pic");
        given(oauthRepo.findByProviderAndProviderId(GOOGLE_PROVIDER, "sub-3"))
                .willReturn(Optional.empty(), Optional.of(OAuth2Account.builder().build()));
        given(userRepo.findByEmail("john@test.com")).willReturn(Optional.of(user));
        given(authService.buildTokensForUser(user)).willReturn(response);

        service.loginWithGoogle(request());

        verify(oauthRepo, never()).save(any(OAuth2Account.class));
    }

    private OAuth2TokenRequest request() {
        return new OAuth2TokenRequest(ID_TOKEN);
    }

    private void mockVerifiedToken(String sub, String email, boolean verified, String name, String picture) {
        given(googleTokenVerifier.verify(ID_TOKEN)).willReturn(payload(sub, email, verified, name, picture));
    }

    private OAuth2Account account(UUID userId, String providerId, String email, String pictureUrl) {
        return OAuth2Account.builder()
                .userId(userId)
                .provider(GOOGLE_PROVIDER)
                .providerId(providerId)
                .email(email)
                .pictureUrl(pictureUrl)
                .build();
    }

    private GoogleIdToken.Payload payload(String sub, String email, boolean verified, String name, String picture) {
        GoogleIdToken.Payload payload = new GoogleIdToken.Payload();
        payload.setSubject(sub);
        payload.setEmail(email);
        payload.setEmailVerified(verified);
        payload.put("name", name);
        payload.put("picture", picture);
        return payload;
    }

    private User user(UUID id, String email, boolean enabled) {
        return User.builder()
                .id(id)
                .email(email)
                .fullName("John")
                .role(Role.WAREHOUSE_STAFF)
                .enabled(enabled)
                .build();
    }
}
