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

    final EmailField recipient = new EmailField();

    public SystemEmailView() {
        // Size to the content, not the viewport, so the bottom padding follows the last grid.
        setWidthFull();
        setPadding(true);
        addClassName(LumoUtility.Padding.Bottom.XLARGE);
    }

    @PostConstruct
    void init() {
        add(new H3(getTranslation("systemEmailView.title")));
        add(new Paragraph(getTranslation("systemEmailView.intro")));

        MailerDiagnostics.MailerReport report = mailer.report();

        add(new H4(getTranslation("systemEmailView.effectiveSettings")));
        Grid<Row> settings = new Grid<>();
        settings.setId(SETTINGS_GRID_ID);
        settings.setItems(List.of(
                new Row(getTranslation("systemEmailView.row.sender"),
                        report.from() != null ? report.from() : getTranslation("systemEmailView.value.absent")),
                new Row(getTranslation("systemEmailView.row.host"), report.host()),
                new Row(getTranslation("systemEmailView.row.port"), Integer.toString(report.port())),
                new Row(getTranslation("systemEmailView.row.tls"), yesNo(report.tls())),
                new Row(getTranslation("systemEmailView.row.startTls"), report.startTls()),
                new Row(getTranslation("systemEmailView.row.authMethods"),
                        report.authMethods() != null ? report.authMethods()
                                : getTranslation("systemEmailView.value.relayDefault")),
                new Row(getTranslation("systemEmailView.row.username"), presence(report.usernamePresent())),
                new Row(getTranslation("systemEmailView.row.password"), presence(report.passwordPresent())),
                new Row(getTranslation("systemEmailView.row.mock"), yesNo(report.mock()))));
        settings.addColumn(Row::label).setHeader(getTranslation("systemEmailView.grid.setting")).setAutoWidth(true);
        settings.addColumn(Row::value).setHeader(getTranslation("system.grid.value")).setFlexGrow(1);
        settings.setAllRowsVisible(true);
        settings.addThemeVariants(GridVariant.LUMO_COMPACT, GridVariant.LUMO_WRAP_CELL_CONTENT);
        add(settings);

        add(new H4(getTranslation("systemEmailView.sendTest")));
        recipient.setLabel(getTranslation("systemEmailView.recipient"));
        recipient.setId(RECIPIENT_ID);
        recipient.setWidth("24em");
        recipient.setClearButtonVisible(true);
        recipient.setErrorMessage(getTranslation("systemEmailView.recipient.invalid"));
        String own = ownEmail();
        if (own != null) {
            recipient.setValue(own);
        }

        Button send = new Button(getTranslation("systemEmailView.send"), e -> send());
        send.setId(SEND_BUTTON_ID);
        send.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        if (!report.canSend()) {
            send.setEnabled(false);
            add(new Paragraph(getTranslation("systemEmailView.sendingDisabled")));
        }
        HorizontalLayout form = new HorizontalLayout(recipient, send);
        form.setAlignItems(Alignment.BASELINE);
        add(form);
    }

    void send() {
        String to = recipient.getValue();
        if (to == null || to.isBlank() || recipient.isInvalid()) {
            recipient.setInvalid(true);
            Notification.show(getTranslation("systemEmailView.recipientRequired"), 5000, Notification.Position.MIDDLE);
            return;
        }
        CheckResult result = emailService.sendTestEmail(to.trim(), identity.getPrincipal().getName());
        if (result.isUp()) {
            Notification.show(getTranslation("systemEmailView.sent", to.trim(), result.detail()), 3000,
                    Notification.Position.MIDDLE);
        } else {
            Notification.show(getTranslation("systemEmailView.failed", result.detail()), 5000,
                    Notification.Position.MIDDLE);
        }
    }

    private String yesNo(boolean value) {
        return getTranslation(value ? "common.yes" : "common.no");
    }

    private String presence(boolean present) {
        return getTranslation(present ? "systemEmailView.value.present" : "systemEmailView.value.absent");
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
        return getTranslation("systemEmailView.pageTitle");
    }
}
