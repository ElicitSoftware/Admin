# Use Case: Manage Users

## Overview

**Use Case ID:** UC-008
**Use Case Name:** Manage Users
**Primary Actor:** Survey Administrator
**Goal:** Maintain application user records and control which departments each user is affiliated with.
**Status:** Implemented

## Preconditions

- The administrator is authenticated and holds the `elicit_admin` role (UC-001).

## Main Success Scenario

1. The administrator opens the users view and sees the list of users with username, first and last name, active flag, and assigned departments.
2. The administrator chooses to add a new user or edit an existing one.
3. The administrator enters or changes the username, first name, last name, active flag, and the set of assigned departments.
4. The administrator saves.
5. The system stores the user and their department associations, then returns to the list.

## Alternative Flows

### A1: User not found

**Trigger:** The administrator opens an edit link for a user that no longer exists.
**Flow:**

1. The system reports the user was not found and returns to the list.

## Postconditions

### Success Postconditions

- The user record and its department associations reflect the administrator's changes.

### Failure Postconditions

- No change is stored.

## Business Rules

### BR-029: Username identifies the account

A user's username is the identifier matched against the authenticated identity when resolving the application account and its roles.

### BR-030: New users active by default

A newly created user is marked active unless the administrator clears the active flag.

### BR-031: Inactive users cannot act

An inactive user is not granted database-sourced roles and cannot operate on data, even with a valid identity (see UC-001, BR-003).

### BR-032: Department affiliation governs visibility

The departments assigned to a user determine which subjects, message-template departments, and registration departments that user can see and use.

---

## Reference

Derived from `UsersView`, `EditUserView`, `User`, and the `survey.user_departments` / `survey.user_surveys` join tables. Deleting users is not supported; the active flag is the intended way to disable an account.
