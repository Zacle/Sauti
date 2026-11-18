package com.sauti.auth.application;

/** Published when an account is activated; the welcome email is sent after the transaction commits. */
public record WelcomeEmailRequested(String toEmail, String displayName) {
}
