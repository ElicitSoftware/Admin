package com.elicitsoftware.service;

import com.elicitsoftware.model.Status;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class EmailService {

    public void sendEmail(Status status) {
        // Implement email sending logic here
        // For example: send notification email to the subject
        System.out.println("Sending email for status: " + status.getToken());
    }
}
