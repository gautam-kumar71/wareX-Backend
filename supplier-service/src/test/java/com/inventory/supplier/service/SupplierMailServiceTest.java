package com.inventory.supplier.service;

import com.inventory.supplier.entity.Supplier;
import jakarta.mail.Address;
import jakarta.mail.BodyPart;
import jakarta.mail.Message;
import jakarta.mail.Multipart;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
class SupplierMailServiceTest {

    @Mock
    private JavaMailSender mailSender;

    @InjectMocks
    private SupplierMailService supplierMailService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(supplierMailService, "fromAddress", "procurement@warex.test");
    }

    @Test
    void sendSuspensionNotice_buildsAndSendsHtmlMessage() throws Exception {
        MimeMessage message = new MimeMessage(Session.getInstance(new Properties()));
        given(mailSender.createMimeMessage()).willReturn(message);

        supplierMailService.sendSuspensionNotice(supplier("Jane Buyer"));

        then(mailSender).should().send(message);
        assertThat(message.getSubject()).isEqualTo("WareX supplier account suspended");
        assertThat(firstRecipient(message, Message.RecipientType.TO)).isEqualTo("ops@acme.com");
        assertThat(message.getFrom()[0].toString()).isEqualTo("procurement@warex.test");
        assertThat(extractHtml(message)).contains("Supplier Account Suspended", "Hello Jane Buyer,", "Acme &amp; Co");
    }

    @Test
    void sendReactivationNotice_fallsBackToSupplierNameWhenContactPersonIsBlank() throws Exception {
        MimeMessage message = new MimeMessage(Session.getInstance(new Properties()));
        given(mailSender.createMimeMessage()).willReturn(message);

        supplierMailService.sendReactivationNotice(supplier("   "));

        then(mailSender).should().send(message);
        assertThat(message.getSubject()).isEqualTo("WareX supplier account reactivated");
        assertThat(extractHtml(message)).contains("Supplier Account Reactivated", "Hello Acme &amp; Co,");
    }

    @Test
    void sendSuspensionNotice_swallowsMailerFailures() {
        given(mailSender.createMimeMessage()).willThrow(new RuntimeException("mail down"));

        assertThatCode(() -> supplierMailService.sendSuspensionNotice(supplier("Jane Buyer")))
                .doesNotThrowAnyException();
    }

    private Supplier supplier(String contactPerson) {
        return Supplier.builder()
                .id(7L)
                .name("Acme & Co")
                .contactPerson(contactPerson)
                .contactEmail("ops@acme.com")
                .build();
    }

    private String firstRecipient(MimeMessage message, Message.RecipientType type) throws Exception {
        Address[] addresses = message.getRecipients(type);
        return addresses[0].toString();
    }

    private String extractHtml(MimeMessage message) throws Exception {
        Object content = message.getContent();
        if (content instanceof String value) {
            return value;
        }
        Multipart multipart = (Multipart) content;
        BodyPart bodyPart = multipart.getBodyPart(0);
        return bodyPart.getContent().toString();
    }
}
