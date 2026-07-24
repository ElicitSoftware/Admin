# Use Case: Send Invitation or Reminder Email

## Overview

**Use Case ID:** UC-004
**Use Case Name:** Send Invitation or Reminder Email
**Primary Actor:** Survey User
**Supporting Actor:** Scheduler
**Goal:** Deliver invitation and reminder emails to subjects so they can access and complete their survey.
**Status:** Implemented

## Preconditions

- For manual send: the user is authenticated with the `elicit_user` or `elicit_admin` role and is viewing a subject (UC-002).
- The subject has an email address, and the department has default message templates configured.
- A mail server is configured and reachable.

## Main Success Scenario

1. The user selects a subject and chooses to send an email.
2. The system looks up the department's default message templates.
3. The system builds each message, substituting the subject's access token into the message body.
4. The system sends the message to the subject's email address from the department's configured sender address.
5. The system confirms the email was sent.

## Alternative Flows

### A1: Scheduled batch send

**Trigger:** The periodic scheduler runs.
**Flow:**

1. The system selects a batch of not-yet-sent messages.
2. For each message with a valid recipient, the system sends the email and records the sent time.
3. Messages that fail to send remain unsent and are retried on a later run.

### A2: Send failure

**Trigger:** The mail server rejects the message or is unreachable (step 4 or A1).
**Flow:**

1. The system reports the failure to the user (manual send) or logs it and leaves the message unsent for retry (scheduled send).

### A3: Subject has no email

**Trigger:** The subject record has no email address.
**Flow:**

1. The system skips the message; it is not sent.

## Postconditions

### Success Postconditions

- The email is delivered to the mail server and the message's sent time is recorded.

### Failure Postconditions

- The message is not marked sent and remains eligible for a later retry.

## Business Rules

### BR-015: Token embedded in message body

Each message body has the subject's survey access token substituted in before sending.

### BR-016: Scheduled send batch limit

Each scheduled run processes at most 100 unsent messages.

### BR-017: Recipient required

A message is only sent if the subject has an email address; otherwise it is skipped.

### BR-018: Content type honored

A message is sent as HTML or plain text according to its declared MIME type.

---

## Reference

Derived from `EmailService`, `Message`, `MessageTemplate`, `Department`, and `Status`.
