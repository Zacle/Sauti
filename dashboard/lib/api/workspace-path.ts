/**
 * API areas that are about the person or the platform, not one workspace. Every other path is a
 * workspace feature and is sent as /api/v1/workspaces/{workspaceId}/...
 */
const ACCOUNT_LEVEL_PREFIXES = ["/auth", "/me", "/workspaces", "/invitations", "/public", "/admin", "/operator", "/voices"];

export function isAccountLevelPath(path: string) {
  return ACCOUNT_LEVEL_PREFIXES.some(
    (prefix) => path === prefix || path.startsWith(`${prefix}/`) || path.startsWith(`${prefix}?`),
  );
}

/** The backend URL for an API path, addressed to the given workspace when the path is a workspace feature. */
export function workspaceApiUrl(path: string, workspaceId: string | null) {
  if (isAccountLevelPath(path) || !workspaceId) return `/api/v1${path}`;
  return `/api/v1/workspaces/${encodeURIComponent(workspaceId)}${path}`;
}
