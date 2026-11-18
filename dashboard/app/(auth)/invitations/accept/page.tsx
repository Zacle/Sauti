import type { Metadata } from "next";
import { AuthProvider } from "@/features/auth/AuthProvider/AuthProvider";
import { JoinWorkspace } from "@/features/workspaces/presentation/JoinWorkspace";

export const metadata: Metadata = {
  title: "Join a workspace on Sauti",
};

/** Needs the session provider: a signed-in person joins with one click, a new person signs in here. */
export default function AcceptWorkspaceInvitationPage() {
  return (
    <AuthProvider>
      <JoinWorkspace />
    </AuthProvider>
  );
}
