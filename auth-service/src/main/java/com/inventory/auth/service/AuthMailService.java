package com.inventory.auth.service;

import com.inventory.auth.entity.User;
import com.inventory.auth.exception.NotificationDeliveryException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.web.util.HtmlUtils;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;

@Service
@RequiredArgsConstructor
public class AuthMailService {

    private final JavaMailSender mailSender;

    @Value("${app.mail.from:${spring.mail.username:no-reply@warex.local}}")
    private String fromAddress;

    public void sendPasswordResetOtp(User user, String otp, int expiryMinutes) {
        send(
                user.getEmail(),
                "WareX password reset OTP",
                buildEmailLayout(
                        "Password Reset Request",
                        "Use the one-time password below to reset your WareX account password.",
                        """
                        <p style="margin:0 0 16px;color:#334155;font-size:15px;line-height:1.7;">
                          Hello %s,
                        </p>
                        <p style="margin:0 0 20px;color:#334155;font-size:15px;line-height:1.7;">
                          We received a request to reset your WareX password. Enter the following OTP on the reset screen:
                        </p>
                        <div style="margin:0 0 24px;padding:18px 20px;border-radius:16px;background:#0f172a;color:#f8fafc;
                                    text-align:center;font-size:30px;font-weight:800;letter-spacing:8px;">
                          %s
                        </div>
                        <p style="margin:0 0 14px;color:#475569;font-size:14px;line-height:1.7;">
                          This OTP expires in <strong>%d minutes</strong>.
                        </p>
                        <p style="margin:0;color:#64748b;font-size:13px;line-height:1.7;">
                          If you did not request this reset, you can safely ignore this email.
                        </p>
                        """.formatted(escape(user.getFullName()), escape(otp), expiryMinutes)
                )
        );
    }

    public void sendPasswordResetSuccess(User user) {
        send(
                user.getEmail(),
                "WareX password reset successful",
                buildEmailLayout(
                        "Password Reset Successful",
                        "Your WareX account password has been updated.",
                        """
                        <p style="margin:0 0 16px;color:#334155;font-size:15px;line-height:1.7;">
                          Hello %s,
                        </p>
                        <p style="margin:0 0 16px;color:#334155;font-size:15px;line-height:1.7;">
                          Your WareX password was reset successfully.
                        </p>
                        <div style="margin:0 0 22px;padding:14px 16px;border-radius:14px;background:#ecfdf5;border:1px solid #a7f3d0;
                                    color:#065f46;font-size:14px;font-weight:600;">
                          Security confirmation: your new password is now active.
                        </div>
                        <p style="margin:0;color:#64748b;font-size:13px;line-height:1.7;">
                          If you did not perform this action, please contact your administrator immediately.
                        </p>
                        """.formatted(escape(user.getFullName()))
                )
        );
    }

    private void send(String to, String subject, String body) {
        MimeMessage message = mailSender.createMimeMessage();

        try {
            MimeMessageHelper helper = new MimeMessageHelper(message, "UTF-8");
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(body, true);
            helper.setFrom(fromAddress);
            mailSender.send(message);
        } catch (MailException | MessagingException ex) {
            throw new NotificationDeliveryException("Unable to send email right now. Please try again shortly.", ex);
        }
    }

    private String buildEmailLayout(String title, String subtitle, String content) {
        return """
                <!DOCTYPE html>
                <html lang="en">
                <body style="margin:0;padding:32px 16px;background:#f8fafc;font-family:Arial,'Segoe UI',sans-serif;">
                  <div style="max-width:680px;margin:0 auto;">
                    <div style="margin-bottom:18px;text-align:center;">
                      <div style="display:inline-block;padding:12px 16px;border-radius:18px;background:#0f172a;color:#ffffff;
                                  font-size:22px;font-weight:900;letter-spacing:0.08em;">
                        WareX
                      </div>
                    </div>
                    <div style="background:#ffffff;border:1px solid #e2e8f0;border-radius:24px;overflow:hidden;
                                box-shadow:0 20px 40px rgba(15,23,42,0.08);">
                      <div style="padding:32px;background:linear-gradient(135deg,#0f172a,#1e293b);color:#ffffff;">
                        <div style="font-size:28px;font-weight:900;letter-spacing:-0.02em;margin:0 0 8px;">%s</div>
                        <div style="font-size:15px;line-height:1.7;color:#cbd5e1;">%s</div>
                      </div>
                      <div style="padding:32px;">
                        %s
                      </div>
                    </div>
                    <div style="padding:18px 6px 0;text-align:center;color:#94a3b8;font-size:12px;line-height:1.7;">
                      WareX inventory platform notification
                    </div>
                  </div>
                </body>
                </html>
                """.formatted(escape(title), escape(subtitle), content);
    }

    private String escape(String value) {
        return HtmlUtils.htmlEscape(value == null ? "" : value);
    }
}
