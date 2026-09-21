# Use Case: Diagnose Email

## Overview

**Use Case ID:** UC-024
**Use Case Name:** Diagnose Email
**Primary Actor:** Survey Administrator
**Goal:** See the effective mail settings and prove that the relay works by sending a test message, before any subject is invited.
**Status:** Tested

## Preconditions

- The administrator is authenticated and holds the `elicit_admin` role (UC-001).

## Main Success Scenario

1. The administrator opens Email from the System section.
2. The system shows the effective settings: sender address, host, port, whether TLS is on, the authentication method, and whether a username and a password are configured, each of the last two as present or absent.
3. The system offers a recipient field, filled with the administrator's own address when the identity provider supplied one.
4. The administrator confirms or enters a recipient and chooses Send test email.
5. The system sends a short plain-text message from the configured sender to that recipient, waiting no longer than the configured send timeout.
6. The system reports success, naming the recipient.
7. The administrator confirms the message arrived.

## Alternative Flows

### A1: The send fails

**Trigger:** The relay refuses the message, cannot be reached, or does not answer within the timeout (step 5).
**Flow:**

1. The system reports the failure with the relay's host and port and the reason, so the operator can tell a wrong host from a rejected sender.
2. Use case continues at step 4.

### A2: No sender address is configured

**Trigger:** The sender address setting is absent (step 2).
**Flow:**

1. The system shows the setting as absent, names the property that supplies it, and disables sending.
2. Use case ends.

### A3: The recipient is not a valid address

**Trigger:** The field does not hold a well-formed address (step 4).
**Flow:**

1. The system refuses to send and marks the field.
2. Use case continues at step 4.

## Postconditions

### Success Postconditions

- One test message has been sent; nothing is stored, and no subject record is touched.

### Failure Postconditions

- No message is sent; the settings shown are unchanged.

## Business Rules

### BR-093: The test uses the real path

The test message is sent by the same service, sender and timeout that invitations use (UC-004), so a passing test means invitations will send.

### BR-094: The password is never shown

The relay password is reported as present or absent only (BR-082).

### BR-095: The test is attributed

Every test send is logged with the administrator's username and the masked recipient, like every other send.

---

## Reference

Traces to FR-024. Built on `EmailService`, which already logs host and port on failure.

Implemented by `MailerDiagnostics` (the effective settings), `EmailService.sendTestEmail` (the send, through
the same mailer, sender and timeout as invitations) and `SystemEmailView`. Verified by
`EmailServiceTestEmailTest` and `SystemViewsRenderTest`.
