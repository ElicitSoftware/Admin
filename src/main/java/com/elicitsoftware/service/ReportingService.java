package com.elicitsoftware.service;

import com.elicitsoftware.model.Status;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class ReportingService {

    public void printReports(Status status) {
        // Implement report generation logic here
        // For example: generate PDF reports for finished surveys
        System.out.println("Generating reports for status: " + status.getToken());
    }
}
