package com.elicitsoftware.service;

import com.elicitsoftware.model.ReportDefinition;
import com.elicitsoftware.model.Status;
import com.elicitsoftware.model.Survey;
import com.elicitsoftware.report.PDFService;
import com.elicitsoftware.report.ReportRequest;
import com.elicitsoftware.report.ReportResponse;
import com.elicitsoftware.report.ReportService;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.server.StreamResource;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.rest.client.RestClientBuilder;

import java.net.URI;
import java.util.ArrayList;

@ApplicationScoped
public class ReportingService {

    @Inject
    PDFService pdfService;

    @ConfigProperty(name = "fhhs.url")
    String FHHSURL;

    ArrayList<ReportResponse> reportResponses = new ArrayList<>();

    public void printReports(Status status) {
        // Implement report generation logic here
        // For example: generate PDF reports for finished surveys
        try {
            Survey survey = Survey.findById(status.getSurveyId());
            int respondent_id = (int) status.getId();
            //Make sure this is empty
            this.reportResponses.clear();
            ReportResponse reportResponse;
            for (ReportDefinition rpt : survey.reports) {
                reportResponse = callReport(rpt, respondent_id);
                reportResponses.add(reportResponse);
            }
            // Generate the PDF using the pdfService
            StreamResource pdfContent = pdfService.generatePDF(this.reportResponses);

            // Register the StreamResource and get its URL
            String pdfUrl = UI.getCurrent().getSession().getResourceRegistry().registerResource(pdfContent).getResourceUri().toString();

            // Open the PDF in a new browser tab
            UI.getCurrent().getPage().open(pdfUrl, "_blank");
        } catch (Exception e) {
            e.printStackTrace();
            Notification.show("Failed to generate PDF: " + e.getMessage(), 3000, Notification.Position.MIDDLE);
        }
    }
    private ReportResponse callReport(ReportDefinition rpt, int respondent_id) {
        try {
            ReportRequest request = new ReportRequest(respondent_id);
            ReportService reportService = RestClientBuilder.newBuilder()
                    .baseUri(new URI(FHHSURL + rpt.url))
                    .build(ReportService.class);
            ReportResponse reportResponse = reportService.callReport(request);
            return reportResponse;
        } catch (Exception e) {
            ReportResponse reportResponse = new ReportResponse();
            reportResponse.innerHTML = e.getMessage();
            return reportResponse;
        }
    }
}
