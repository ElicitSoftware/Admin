package com.elicitsoftware.service;

import com.elicitsoftware.model.Message;
import com.elicitsoftware.model.Status;
import io.quarkus.mailer.Mail;
import io.quarkus.mailer.reactive.ReactiveMailer;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.Date;
import java.util.List;

@ApplicationScoped
public class EmailService {

    @Inject
    ReactiveMailer mailer;

    @ConfigProperty(name = "quarkus.mailer.from")
    String fromEmail;

    public boolean sendEmail(Status status) {
        System.out.println("Sending email for status: " + status.getToken());
        try {
            mailer.send(
                    Mail.withText(status.getEmail(),
                            "Ahoy from Quarkus",
                            "A simple email sent from a Quarkus application."
                    ).setFrom(fromEmail)
            ).await().indefinitely();
            System.out.println("Email sent successfully");
            return true;
        } catch (Exception ex) {
            System.out.println("Failed to send email: " + ex.getMessage());
            return false;
        }
    }

    @Scheduled(every = "5m")
    @Transactional
    public void processUnsentMessages() {
        System.out.println("Processing unsent messages...");

        try {
            // Get up to 100 unsent messages
            List<Message> unsentMessages = Message.find("sentDt is null")
                    .page(0, 100)
                    .list();

            System.out.println("Found " + unsentMessages.size() + " unsent messages");

            for (Message message : unsentMessages) {
                if (sendMessage(message)) {
                    // Mark as sent
                    message.sentDt = new Date();
                    message.persist();
                    System.out.println("Message " + message.id + " sent successfully");
                } else {
                    System.out.println("Failed to send message " + message.id);
                }
            }
        } catch (Exception e) {
            System.err.println("Error processing unsent messages: " + e.getMessage());
        }
    }

    private boolean sendMessage(Message message) {
        try {
            // Validate required fields
            if (message.subject == null || message.subject.getEmail() == null) {
                System.err.println("Message " + message.id + " has no valid recipient email");
                return false;
            }

            Mail mail;
            if ("text/html".equals(message.mimeType)) {
                mail = Mail.withHtml(
                        message.subject.getEmail(),
                        message.subjectLine,
                        message.body
                ).setFrom(fromEmail);
            } else {
                mail = Mail.withText(
                        message.subject.getEmail(),
                        message.subjectLine,
                        message.body
                ).setFrom(fromEmail);
            }

            mailer.send(mail).await().indefinitely();
            return true;
        } catch (Exception ex) {
            System.err.println("Failed to send message " + message.id + ": " + ex.getMessage());
            return false;
        }
    }
}
