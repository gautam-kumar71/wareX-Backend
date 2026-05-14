package com.inventory.auth;

import com.inventory.auth.repository.OAuth2AccountRepository;
import com.inventory.auth.repository.PasswordResetOtpRepository;
import com.inventory.auth.repository.RefreshTokenRepository;
import com.inventory.auth.repository.UserRepository;
import com.inventory.auth.security.oauth2.GoogleTokenVerifier;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.mail.javamail.JavaMailSender;

@SpringBootTest(properties = {
        "management.health.mail.enabled=false",
        "management.health.redis.enabled=false",
        "spring.main.lazy-initialization=true",
        "spring.cloud.discovery.enabled=false",
        "eureka.client.enabled=false",
        "spring.autoconfigure.exclude="
                + "org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration,"
                + "org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration,"
                + "org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration,"
                + "org.springframework.boot.autoconfigure.data.jpa.JpaRepositoriesAutoConfiguration,"
                + "org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration,"
                + "org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration",
        "app.seed-default-users.enabled=false"
})
@ActiveProfiles("test")
class AuthServiceApplicationTests {

    @MockBean UserRepository userRepository;
    @MockBean RefreshTokenRepository refreshTokenRepository;
    @MockBean PasswordResetOtpRepository passwordResetOtpRepository;
    @MockBean OAuth2AccountRepository oAuth2AccountRepository;
    @MockBean RedisTemplate<String, String> redisTemplate;
    @MockBean JavaMailSender javaMailSender;
    @MockBean GoogleTokenVerifier googleTokenVerifier;

    @Test
    void contextLoads() {
    }

}
