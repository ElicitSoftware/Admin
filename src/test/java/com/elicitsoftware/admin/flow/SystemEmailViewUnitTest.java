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
import com.elicitsoftware.diagnostics.MailerDiagnostics.MailerReport;
import com.elicitsoftware.service.EmailService;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.H4;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.textfield.EmailField;
import jakarta.enterprise.inject.Instance;
import org.eclipse.microprofile.jwt.JsonWebToken;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * UC-024: Diagnose Email — {@link SystemEmailView} in a plain browserless UI with stand-ins for
 * the mail diagnostics, the email service, the signed-in identity and its ID token. Covers the
 * effective settings with the secrets shown only as present or absent (BR-094), the recipient
 * prefilled from the administrator's own address, the test sent through the real path and
 * attributed to the administrator (BR-093, BR-095), and the disabled send without a sender.
 */
class SystemEmailViewUnitTest extends PlainSmallViewTestSupport {

    private MailerReport report = new MailerReport("ops@example.org", "smtp.example.org", 587, true, "REQUIRED",
            "LOGIN", true, true, false);
    private Object emailClaim;
    private boolean tokenResolvable = true;
    private boolean tokenThrows;
    private String principal = "admin";
    private CheckResult sendAnswer = CheckResult.up("Email", "accepted by smtp", 12);
    private final List<String> sends = new ArrayList<>();
    private SystemEmailView view;

    @BeforeEach
    void stubs() {
        view = new SystemEmailView();
    }

    @SuppressWarnings("unchecked")
    private Instance<JsonWebToken> tokenInstance() {
        JsonWebToken token = (JsonWebToken) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[]{JsonWebToken.class}, (proxy, method, args) -> {
                    if (method.getName().equals("getClaim") && "email".equals(args[0])) {
                        return emailClaim;
                    }
                    throw new UnsupportedOperationException(method.getName());
                });
        return (Instance<JsonWebToken>) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[]{Instance.class}, (proxy, method, args) -> switch (method.getName()) {
                    case "isResolvable" -> {
                        if (tokenThrows) {
                            throw new IllegalStateException("no request context");
                        }
                        yield tokenResolvable;
                    }
                    case "get" -> token;
                    default -> throw new UnsupportedOperationException(method.getName());
                });
    }

    private SystemEmailView init() {
        view.mailer = new MailerDiagnostics() {
            @Override
            public MailerReport report() {
                return report;
            }
        };
        view.emailService = new EmailService() {
            @Override
            public CheckResult sendTestEmail(String to, String requestedBy) {
                sends.add(to + " by " + requestedBy);
                return sendAnswer;
            }
        };
        view.identity = identity(principal);
        view.idTokenInstance = tokenInstance();
        view.init();
        UI.getCurrent().add(view);
        return view;
    }

    @SuppressWarnings("unchecked")
    private Grid<SystemEmailView.Row> settings() {
        return byId(view, SystemEmailView.SETTINGS_GRID_ID, Grid.class);
    }

    private EmailField recipient() {
        return byId(view, SystemEmailView.RECIPIENT_ID, EmailField.class);
    }

    private Button sendButton() {
        return byId(view, SystemEmailView.SEND_BUTTON_ID, Button.class);
    }

    private Notification onlyNotification() {
        return $(Notification.class).single();
    }

    @Test
    void given_fullSettings_when_opened_then_eachIsShownWithSecretsAsPresence() {
        init();

        List<Component> parts = view.getChildren().toList();
        assertEquals(t("systemEmailView.title"), ((H3) parts.get(0)).getText());
        assertEquals(t("systemEmailView.intro"), ((Paragraph) parts.get(1)).getText());
        assertEquals(t("systemEmailView.effectiveSettings"), ((H4) parts.get(2)).getText());
        assertEquals(t("systemEmailView.sendTest"), ((H4) parts.get(4)).getText());
        assertEquals(6, parts.size(), "no sending-disabled paragraph when a sender is set");
        assertTrue(view.hasClassName("system-view"));
        assertEquals(t("systemEmailView.pageTitle"), view.getPageTitle());

        Grid<SystemEmailView.Row> grid = settings();
        assertSummaryGrid(grid);
        assertEquals(List.of(t("systemEmailView.grid.setting"), t("system.grid.value")), headers(grid));
        assertEquals(List.of(t("systemEmailView.row.sender"), t("systemEmailView.row.host"),
                t("systemEmailView.row.port"), t("systemEmailView.row.tls"), t("systemEmailView.row.startTls"),
                t("systemEmailView.row.authMethods"), t("systemEmailView.row.username"),
                t("systemEmailView.row.password"), t("systemEmailView.row.mock")), column(grid, 0));
        assertEquals(List.of("ops@example.org", "smtp.example.org", "587", t("common.yes"), "REQUIRED", "LOGIN",
                t("systemEmailView.value.present"), t("systemEmailView.value.present"), t("common.no")),
                column(grid, 1));

        EmailField recipient = recipient();
        assertEquals(t("systemEmailView.recipient"), recipient.getLabel());
        assertEquals("24em", recipient.getWidth());
        assertTrue(recipient.isClearButtonVisible());
        assertEquals(t("systemEmailView.recipient.invalid"), recipient.getErrorMessage());
        Button send = sendButton();
        assertEquals(t("systemEmailView.send"), send.getText());
        assertTrue(send.isEnabled());
        assertTrue(send.getThemeNames().contains(ButtonVariant.LUMO_PRIMARY.getVariantName()));
        HorizontalLayout form = (HorizontalLayout) send.getParent().orElseThrow();
        assertEquals(FlexComponent.Alignment.BASELINE, form.getAlignItems());
    }

    @Test
    void given_noSenderAndDefaults_when_opened_then_absentValuesAreWordedAndSendingIsDisabled() {
        report = new MailerReport(null, "localhost", 25, false, "OPTIONAL", null, false, false, true);
        init();

        assertEquals(List.of(t("systemEmailView.value.absent"), "localhost", "25", t("common.no"), "OPTIONAL",
                t("systemEmailView.value.relayDefault"), t("systemEmailView.value.absent"),
                t("systemEmailView.value.absent"), t("common.yes")), column(settings(), 1));
        assertFalse(sendButton().isEnabled());
        List<Component> parts = view.getChildren().toList();
        assertEquals(7, parts.size());
        assertEquals(t("systemEmailView.sendingDisabled"), ((Paragraph) parts.get(5)).getText());
    }

    @Test
    void given_idTokenWithEmail_when_opened_then_recipientIsPrefilledWithIt() {
        emailClaim = "ada@example.org";
        init();

        assertEquals("ada@example.org", recipient().getValue());
    }

    @Test
    void given_idTokenClaimWithoutAt_when_opened_then_principalIsConsidered() {
        emailClaim = "not-an-address";
        principal = "boss@example.org";
        init();

        assertEquals("boss@example.org", recipient().getValue());
    }

    @Test
    void given_noResolvableTokenAndPlainPrincipal_when_opened_then_recipientStaysEmpty() {
        tokenResolvable = false;
        emailClaim = "ada@example.org";
        init();

        assertEquals("", recipient().getValue());
    }

    @Test
    void given_tokenLookupFails_when_opened_then_principalAddressIsUsed() {
        tokenThrows = true;
        principal = "ops@example.org";
        init();

        assertEquals("ops@example.org", recipient().getValue());
    }

    @Test
    void given_noTokenAndNoPrincipalName_when_opened_then_recipientStaysEmpty() {
        tokenResolvable = false;
        principal = null;
        init();

        assertEquals("", recipient().getValue());
    }

    @Test
    void given_nonStringClaim_when_opened_then_itIsIgnored() {
        emailClaim = 42;
        init();

        assertEquals("", recipient().getValue());
    }

    @Test
    void given_emptyRecipient_when_sendClicked_then_fieldIsMarkedAndNothingIsSent() {
        init();

        test(sendButton()).click();

        assertTrue(recipient().isInvalid());
        assertEquals(List.of(), sends);
        Notification n = onlyNotification();
        assertEquals(t("systemEmailView.recipientRequired"), test(n).getText());
        assertEquals(5000, n.getDuration());
        assertEquals(Notification.Position.MIDDLE, n.getPosition());
    }

    @Test
    void given_blankRecipient_when_send_then_nothingIsSent() {
        init();
        recipient().setValue("   ");

        view.send();

        assertEquals(List.of(), sends);
        assertTrue(recipient().isInvalid());
    }

    @Test
    void given_invalidRecipient_when_send_then_nothingIsSent() {
        init();
        recipient().setValue("ada@example.org");
        recipient().setInvalid(true);

        view.send();

        assertEquals(List.of(), sends);
        assertEquals(List.of(t("systemEmailView.recipientRequired")), notifications());
    }

    @Test
    void given_acceptedTest_when_sent_then_addressIsAttributedAndSuccessShown() {
        init();
        recipient().setValue("ada@example.org");

        view.send();

        assertEquals(List.of("ada@example.org by admin"), sends);
        Notification n = onlyNotification();
        assertEquals(t("systemEmailView.sent", "ada@example.org", "accepted by smtp"), test(n).getText());
        assertEquals(3000, n.getDuration());
        assertEquals(Notification.Position.MIDDLE, n.getPosition());
    }

    @Test
    void given_failedTest_when_sent_then_failureIsShown() {
        sendAnswer = CheckResult.down("Email", "relay refused", 5);
        init();
        recipient().setValue("ada@example.org");

        view.send();

        Notification n = onlyNotification();
        assertEquals(t("systemEmailView.failed", "relay refused"), test(n).getText());
        assertEquals(5000, n.getDuration());
        assertEquals(Notification.Position.MIDDLE, n.getPosition());
    }
}
