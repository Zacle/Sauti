export const PILOT_INVITATION_SESSION_KEY = "sauti-pilot-invitation";

type InvitationTokenStorage = Pick<Storage, "getItem" | "setItem" | "removeItem">;

export function retainInvitationToken(hash: string, storage: InvitationTokenStorage) {
  const fragment = new URLSearchParams(hash.replace(/^#/, ""));
  const fragmentToken = fragment.get("token")?.trim() ?? "";

  if (fragmentToken) {
    storage.setItem(PILOT_INVITATION_SESSION_KEY, fragmentToken);
    return fragmentToken;
  }

  return storage.getItem(PILOT_INVITATION_SESSION_KEY)?.trim() ?? "";
}

export function clearInvitationToken(storage: InvitationTokenStorage) {
  storage.removeItem(PILOT_INVITATION_SESSION_KEY);
}
