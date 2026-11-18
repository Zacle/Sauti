package com.sauti.tenant.domain;

/**
 * The workspace does not exist, is closed, or the caller is not a member. All three return the
 * same 404 so workspace ids cannot be probed.
 */
public class WorkspaceNotFoundException extends RuntimeException {
    public WorkspaceNotFoundException() {
        super("Workspace not found");
    }
}
