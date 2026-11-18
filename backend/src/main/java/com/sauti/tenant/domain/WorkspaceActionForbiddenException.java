package com.sauti.tenant.domain;

/** The member is in the workspace but their role does not allow the action. Maps to 403. */
public class WorkspaceActionForbiddenException extends RuntimeException {
    public WorkspaceActionForbiddenException(String message) {
        super(message);
    }
}
