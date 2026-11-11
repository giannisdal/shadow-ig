package com.giannisdal.shadowig.notification;

import com.giannisdal.shadowig.exception.NotificationDeliveryException;
import com.resend.Resend;
import com.resend.core.exception.ResendException;
import com.resend.services.emails.model.CreateEmailOptions;
import org.springframework.stereotype.Component;

@Component
public class ResendSdkClient implements ResendClient {

    private final Resend resend;

    public ResendSdkClient(Resend resend) {
        this.resend = resend;
    }

    @Override
    public String sendEmail(String from, String to, String subject, String htmlBody) throws NotificationDeliveryException {
        try {
            var response = resend.emails().send(CreateEmailOptions.builder()
                    .from(from)
                    .to(to)
                    .subject(subject)
                    .html(htmlBody)
                    .build());
            return response.getId();
        } catch (ResendException exception) {
            var message = exception.getMessage() == null ? "Resend request failed" : exception.getMessage();
            boolean retryable = message.contains("429") || message.contains("50");
            throw new NotificationDeliveryException(message, retryable, exception);
        }
    }
}
