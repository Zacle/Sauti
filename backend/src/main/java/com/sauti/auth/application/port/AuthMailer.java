package com.sauti.auth.application.port;

/** Account emails. Delivery happens off the request thread so timing does not reveal accounts. */
public interface AuthMailer {
    void sendVerificationCode(String toEmail, String displayName, String code);

    void sendPasswordResetCode(String toEmail, String displayName, String code);

    void sendWelcome(String toEmail, String displayName);
}
