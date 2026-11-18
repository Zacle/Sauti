package com.sauti.auth;

/** A workspace feature was called without naming the workspace in the URL. Maps to 400. */
public class WorkspaceRequiredException extends RuntimeException {
    public WorkspaceRequiredException() {
        super("Choose a workspace: call this under /api/v1/workspaces/{workspaceId}/");
    }
}
