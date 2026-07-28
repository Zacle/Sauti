package com.sauti.auth;

/** Emitted only when a workspace owner transitions to a verified account. */
public record WelcomeEmailRequested(String toEmail, String businessName) { }
