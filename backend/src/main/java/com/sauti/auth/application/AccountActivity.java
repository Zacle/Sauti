package com.sauti.auth.application;

import java.util.UUID;

/** A security event on one account, such as a sign-in or a password change. */
public record AccountActivity(UUID userId, String action, String summary) {
}
