/**
 * Identity: accounts, sign-in (password and Google), email verification, password recovery and
 * sessions.
 *
 * <p>Layers: {@code domain} (entities, rules, storage ports), {@code application} (one use case per
 * class, plus ports for hashing, tokens, codes, email and Google), {@code infrastructure}
 * (adapters and request security). This root package is the module's public surface: the signed-in
 * principal and the exceptions other modules may see.
 */
package com.sauti.auth;
