package com.inventory.auth.service;

import com.inventory.auth.entity.User;
import com.inventory.auth.exception.NotificationDeliveryException;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AuthMailServiceTest {

    @Mock
    private JavaMailSender mailSender;

    @InjectMocks
    private AuthMailService service;

    @Test
    void sendPasswordResetOtp_buildsAndSendsHtmlMail() {
        MimeMessage message = new MimeMessage(Session.getInstance(new Properties()));
        ReflectionTestUtils.setField(service, "fromAddress", "no-reply@test.com");
        given(mailSender.createMimeMessage()).willReturn(message);
        User user = User.builder().email("john@test.com").fullName("John").build();

        service.sendPasswordResetOtp(user, "123456", 10);

        verify(mailSender).send(message);
    }

    @Test
    void sendPasswordResetSuccess_buildsAndSendsHtmlMail() {
        MimeMessage message = new MimeMessage(Session.getInstance(new Properties()));
        ReflectionTestUtils.setField(service, "fromAddress", "no-reply@test.com");
        given(mailSender.createMimeMessage()).willReturn(message);
        User user = User.builder().email("john@test.com").fullName("John").build();

        service.sendPasswordResetSuccess(user);

        verify(mailSender).send(message);
    }

    @Test
    void mailFailure_wrapsAsNotificationDeliveryException() {
        MimeMessage message = new MimeMessage(Session.getInstance(new Properties()));
        ReflectionTestUtils.setField(service, "fromAddress", "no-reply@test.com");
        given(mailSender.createMimeMessage()).willReturn(message);
        org.mockito.Mockito.doThrow(new MailSendException("down")).when(mailSender).send(any(MimeMessage.class));
        User user = User.builder().email("john@test.com").fullName("John").build();

        assertThatThrownBy(() -> service.sendPasswordResetSuccess(user))
                .isInstanceOf(NotificationDeliveryException.class)
                .hasMessageContaining("Unable to send email");
    }
}
