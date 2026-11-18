export const PILOT_INVITATION_SESSION_KEY = "sauti-pilot-invitation";
export const WORKSPACE_INVITATION_SESSION_KEY = "sauti-workspace-invitation";

type InvitationTokenStorage = Pick<Storage, "getItem" | "setItem" | "removeItem">;

/**
 * Invitation tokens arrive in the URL fragment (never sent to a server) and are kept in
 * sessionStorage so the page still works after a reload or a detour through sign-in.
 */
export function retainInvitationToken(
  hash: string,
  storage: InvitationTokenStorage,
  key: string = PILOT_INVITATION_SESSION_KEY,
) {
  const fragment = new URLSearchParams(hash.replace(/^#/, ""));
  const fragmentToken = fragment.get("token")?.trim() ?? "";

  if (fragmentToken) {
    storage.setItem(key, fragmentToken);
    return fragmentToken;
  }

  return storage.getItem(key)?.trim() ?? "";
}

export function clearInvitationToken(storage: InvitationTokenStorage, key: string = PILOT_INVITATION_SESSION_KEY) {
  storage.removeItem(key);
}
