package com.inventory.supplier.service;

import com.inventory.supplier.entity.Supplier;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.util.HtmlUtils;

@Service
@RequiredArgsConstructor
@Slf4j
public class SupplierMailService {

    private final JavaMailSender mailSender;

    @Value("${app.mail.from:${spring.mail.username:no-reply@warex.local}}")
    private String fromAddress;

    public void sendSuspensionNotice(Supplier supplier) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, "UTF-8");
            helper.setTo(supplier.getContactEmail());
            helper.setFrom(fromAddress);
            helper.setSubject("WareX supplier account suspended");
            helper.setText(buildSuspensionEmail(supplier), true);
            mailSender.send(message);
        } catch (Exception ex) {
            log.warn("Failed to send supplier suspension mail to supplier {}", supplier.getId(), ex);
        }
    }

    public void sendReactivationNotice(Supplier supplier) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, "UTF-8");
            helper.setTo(supplier.getContactEmail());
            helper.setFrom(fromAddress);
            helper.setSubject("WareX supplier account reactivated");
            helper.setText(buildReactivationEmail(supplier), true);
            mailSender.send(message);
        } catch (Exception ex) {
            log.warn("Failed to send supplier reactivation mail to supplier {}", supplier.getId(), ex);
        }
    }

    private String buildSuspensionEmail(Supplier supplier) {
        String recipientName = StringUtils.hasText(supplier.getContactPerson())
                ? supplier.getContactPerson()
                : supplier.getName();

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
                      <div style="padding:32px;background:linear-gradient(135deg,#7f1d1d,#991b1b);color:#ffffff;">
                        <div style="font-size:28px;font-weight:900;letter-spacing:-0.02em;margin:0 0 8px;">
                          Supplier Account Suspended
                        </div>
                        <div style="font-size:15px;line-height:1.7;color:#fecaca;">
                          This notification confirms that your WareX supplier account is currently inactive.
                        </div>
                      </div>
                      <div style="padding:32px;">
                        <p style="margin:0 0 16px;color:#334155;font-size:15px;line-height:1.7;">
                          Hello %s,
                        </p>
                        <p style="margin:0 0 20px;color:#334155;font-size:15px;line-height:1.7;">
                          Your supplier account with WareX has been suspended.
                        </p>
                        <div style="margin:0 0 24px;padding:18px 20px;border-radius:16px;background:#fff7ed;border:1px solid #fdba74;">
                          <div style="margin:0 0 8px;color:#9a3412;font-size:12px;font-weight:800;letter-spacing:0.08em;text-transform:uppercase;">
                            Supplier Details
                          </div>
                          <div style="margin:0 0 6px;color:#7c2d12;font-size:14px;"><strong>Name:</strong> %s</div>
                          <div style="margin:0;color:#7c2d12;font-size:14px;"><strong>Supplier ID:</strong> %s</div>
                        </div>
                        <p style="margin:0;color:#64748b;font-size:13px;line-height:1.7;">
                          If you believe this change was unexpected, please contact the WareX procurement team.
                        </p>
                      </div>
                    </div>
                    <div style="padding:18px 6px 0;text-align:center;color:#94a3b8;font-size:12px;line-height:1.7;">
                      WareX supplier management notification
                    </div>
                  </div>
                </body>
                </html>
                """.formatted(
                escape(recipientName),
                escape(supplier.getName()),
                supplier.getId()
        );
    }

    private String buildReactivationEmail(Supplier supplier) {
        String recipientName = StringUtils.hasText(supplier.getContactPerson())
                ? supplier.getContactPerson()
                : supplier.getName();

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
                      <div style="padding:32px;background:linear-gradient(135deg,#166534,#15803d);color:#ffffff;">
                        <div style="font-size:28px;font-weight:900;letter-spacing:-0.02em;margin:0 0 8px;">
                          Supplier Account Reactivated
                        </div>
                        <div style="font-size:15px;line-height:1.7;color:#dcfce7;">
                          Your WareX supplier account is active again and ready for business.
                        </div>
                      </div>
                      <div style="padding:32px;">
                        <p style="margin:0 0 16px;color:#334155;font-size:15px;line-height:1.7;">
                          Hello %s,
                        </p>
                        <p style="margin:0 0 20px;color:#334155;font-size:15px;line-height:1.7;">
                          Your supplier account with WareX has been reactivated. You may now resume normal coordination
                          for purchase orders, deliveries, and invoice-related communication.
                        </p>
                        <div style="margin:0 0 24px;padding:18px 20px;border-radius:16px;background:#f0fdf4;border:1px solid #86efac;">
                          <div style="margin:0 0 8px;color:#166534;font-size:12px;font-weight:800;letter-spacing:0.08em;text-transform:uppercase;">
                            Supplier Details
                          </div>
                          <div style="margin:0 0 6px;color:#166534;font-size:14px;"><strong>Name:</strong> %s</div>
                          <div style="margin:0;color:#166534;font-size:14px;"><strong>Supplier ID:</strong> %s</div>
                        </div>
                        <p style="margin:0;color:#64748b;font-size:13px;line-height:1.7;">
                          If you have any questions, please contact the WareX procurement team.
                        </p>
                      </div>
                    </div>
                    <div style="padding:18px 6px 0;text-align:center;color:#94a3b8;font-size:12px;line-height:1.7;">
                      WareX supplier management notification
                    </div>
                  </div>
                </body>
                </html>
                """.formatted(
                escape(recipientName),
                escape(supplier.getName()),
                supplier.getId()
        );
    }

    private String escape(String value) {
        return HtmlUtils.htmlEscape(value == null ? "" : value);
    }
}
