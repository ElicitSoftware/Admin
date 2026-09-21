package com.elicitsoftware.admin.flow;

/*-
 * ***LICENSE_START***
 * Elicit Survey
 * %%
 * Copyright (C) 2025 - 2026 The Regents of the University of Michigan - Rogel Cancer Center
 * %%
 * PolyForm Noncommercial License 1.0.0
 * <https://polyformproject.org/licenses/noncommercial/1.0.0>
 * ***LICENSE_END***
 */

import com.elicitsoftware.diagnostics.CheckResult;
import com.elicitsoftware.diagnostics.MailerDiagnostics;
import com.elicitsoftware.security.ElicitRoles;
import com.elicitsoftware.service.EmailService;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.GridVariant;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.H4;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.EmailField;
import com.vaadin.flow.router.HasDynamicTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.theme.lumo.LumoUtility;
import io.quarkus.oidc.IdToken;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.security.RolesAllowed;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import org.eclipse.microprofile.jwt.JsonWebToken;

import java.util.List;

/**
 * Email diagnostics (UC-024): the effective mail settings and a test send through the same
 * path invitations use (BR-093). The password is shown as present or absent only (BR-094).
 */
@Route(value = "system/email", layout = MainLayout.class)
@RolesAllowed(ElicitRoles.ADMIN)
public class SystemEmailView extends VerticalLayout implements HasDynamicTitle {

    static final String SETTINGS_GRID_ID = "system-email-settings";
    static final String RECIPIENT_ID = "system-email-recipient";
    static final String SEND_BUTTON_ID = "system-email-send";

    record Row(String label, String value) {
    }

    @Inject
    MailerDiagnostics mailer;

    @Inject
    EmailService emailService;

    @Inject
    SecurityIdentity identity;

    @Inject
    @IdToken
    Instance<JsonWebToken> idTokenInstance;

    final EmailField recipient = new EmailField("Recipient");

    public SystemEmailView() {
        // Size to the content, not the viewport, so the bottom padding follows the last grid.
        setWidthFull();
        setPadding(true);
        addClassName(LumoUtility.Padding.Bottom.XLARGE);
    }

    @PostConstruct
    void init() {
        add(new H3("Email"));
        add(new Paragraph("These are the mail settings the service started with; they cannot be changed "
                + "here. The test message is sent by the same service, sender and timeout that "
                + "invitations use, so a passing test means invitations will send."));

        MailerDiagnostics.MailerReport report = mailer.report();

        add(new H4("Effective settings"));
        Grid<Row> settings = new Grid<>();
        settings.setId(SETTINGS_GRID_ID);
        settings.setItems(List.of(
                new Row("Sender (quarkus.mailer.from)", report.from() != null ? report.from() : "absent"),
                new Row("Host (quarkus.mailer.host)", report.host()),
                new Row("Port (quarkus.mailer.port)", Integer.toString(report.port())),
                new Row("TLS (quarkus.mailer.tls)", Boolean.toString(report.tls())),
                new Row("STARTTLS (quarkus.mailer.start-tls)", report.startTls()),
                new Row("Authentication methods (quarkus.mailer.auth-methods)",
                        report.authMethods() != null ? report.authMethods() : "not set (relay default)"),
                new Row("Username (quarkus.mailer.username)", report.usernamePresent() ? "present" : "absent"),
                new Row("Password (quarkus.mailer.password)", report.passwordPresent() ? "present" : "absent"),
                new Row("Mocked (quarkus.mailer.mock)", Boolean.toString(report.mock()))));
        settings.addColumn(Row::label).setHeader("Setting").setAutoWidth(true);
        settings.addColumn(Row::value).setHeader("Value").setFlexGrow(1);
        settings.setAllRowsVisible(true);
        settings.addThemeVariants(GridVariant.LUMO_COMPACT, GridVariant.LUMO_WRAP_CELL_CONTENT);
        add(settings);

        add(new H4("Send a test message"));
        recipient.setId(RECIPIENT_ID);
        recipient.setWidth("24em");
        recipient.setClearButtonVisible(true);
        recipient.setErrorMessage("Enter a valid email address");
        String own = ownEmail();
        if (own != null) {
            recipient.setValue(own);
        }

        Button send = new Button("Send test email", e -> send());
        send.setId(SEND_BUTTON_ID);
        send.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        if (!report.canSend()) {
            send.setEnabled(false);
            add(new Paragraph("Sending is disabled because no sender address is configured. Set "
                    + "quarkus.mailer.from and restart the service."));
        }
        HorizontalLayout form = new HorizontalLayout(recipient, send);
        form.setAlignItems(Alignment.BASELINE);
        add(form);
    }

    void send() {
        String to = recipient.getValue();
        if (to == null || to.isBlank() || recipient.isInvalid()) {
            recipient.setInvalid(true);
            Notification.show("Enter a valid recipient address first.", 5000, Notification.Position.MIDDLE);
            return;
        }
        CheckResult result = emailService.sendTestEmail(to.trim(), identity.getPrincipal().getName());
        if (result.isUp()) {
            Notification.show("Test email sent to " + to.trim() + " (" + result.detail() + ").", 3000,
                    Notification.Position.MIDDLE);
        } else {
            Notification.show("Test email failed: " + result.detail(), 5000, Notification.Position.MIDDLE);
        }
    }

    /** The administrator's own address from the ID token, when the identity provider supplied one. */
    private String ownEmail() {
        try {
            if (idTokenInstance.isResolvable()) {
                Object email = idTokenInstance.get().getClaim("email");
                if (email instanceof String s && s.contains("@")) {
                    return s;
                }
            }
        } catch (RuntimeException e) {
            // No token in this session (database authorization, tests); the field stays empty.
        }
        String principal = identity.getPrincipal().getName();
        return principal != null && principal.contains("@") ? principal : null;
    }

    @Override
    public String getPageTitle() {
        return "System Email";
    }
}
