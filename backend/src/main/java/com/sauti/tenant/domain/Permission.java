package com.sauti.tenant.domain;

/**
 * What a member may do inside a workspace. Code checks permissions, never role names, so a new
 * role is a mapping change in {@link WorkspaceRole} rather than a change across controllers.
 */
public enum Permission {
    /** Read anything in the workspace: calls, bookings, agents, analytics, settings. */
    VIEW,
    /** Reply to customer conversations and mark one's own notifications as read. */
    HANDLE_CONVERSATIONS,
    /** Change agents, numbers, campaigns, integrations and workspace settings. */
    CONFIGURE,
    /** Invite, remove and change the role of members below Owner. */
    MANAGE_MEMBERS,
    /** Change the plan, buy minutes, manage the subscription. */
    MANAGE_BILLING,
    /** Close the workspace or hand ownership to someone else. */
    OWN_WORKSPACE
}
