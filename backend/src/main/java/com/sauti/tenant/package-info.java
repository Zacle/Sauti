/**
 * Workspaces (called tenants in older code): profile, settings, members, roles and invitations.
 *
 * <p>New code is layered: {@code domain} (memberships, roles, permissions, invitations and their
 * rules), {@code application} (use cases and ports), {@code infrastructure} (Spring Data and email
 * adapters). {@code Tenant}, {@code TenantRepository} and the settings services at the package root
 * predate the layering and are shared with other modules.
 */
package com.sauti.tenant;
