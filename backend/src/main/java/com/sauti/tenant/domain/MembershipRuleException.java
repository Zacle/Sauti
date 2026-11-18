package com.sauti.tenant.domain;

/** The action would break a workspace rule, such as leaving it without an Owner. Maps to 409. */
public class MembershipRuleException extends RuntimeException {
    public MembershipRuleException(String message) {
        super(message);
    }
}
