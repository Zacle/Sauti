"use client";

import { useState, type FormEvent } from "react";
import { LoaderCircle } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Alert, Input, Label } from "@/components/ui/field";
import { Dialog, DialogContent } from "@/components/ui/overlays";
import { Select } from "@/components/ui/select";
import { useAuth } from "@/hooks/useAuth";
import { authApi, workspacesApi } from "@/lib/api/auth";
import { COUNTRIES } from "@/lib/countries";

const COUNTRY_OPTIONS = COUNTRIES.map((country) => ({ value: country.code, label: country.name, keywords: [country.code] }));

/**
 * Creates another workspace with the signed-in person as Owner and opens it. Phone numbers and
 * live calls stay blocked until Sauti approves the new workspace, which the copy says up front.
 */
export function CreateWorkspaceDialog({
  open,
  onOpenChange,
}: {
  open: boolean;
  onOpenChange: (open: boolean) => void;
}) {
  const { session, updateSession, selectWorkspace } = useAuth();
  const [name, setName] = useState("");
  const [country, setCountry] = useState(session?.tenant?.countryCode || "KE");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");

  async function submit(event: FormEvent) {
    event.preventDefault();
    if (!session) return;
    setBusy(true);
    setError("");
    try {
      const created = await workspacesApi.create(name.trim(), country);
      const account = await authApi.me();
      updateSession({ ...session, account });
      selectWorkspace(created.workspace.id);
      window.location.assign("/agents");
    } catch (caught) {
      setError(caught instanceof Error ? caught.message : "Unable to create the workspace.");
      setBusy(false);
    }
  }

  return (
    <Dialog open={open} onOpenChange={(next) => !busy && onOpenChange(next)}>
      <DialogContent
        title="Create a workspace"
        description="A separate business with its own agents, numbers, calls and billing. You'll be its Owner."
      >
        <form className="grid gap-4" onSubmit={submit}>
          <Label>
            Business name
            <Input
              autoFocus
              required
              maxLength={120}
              value={name}
              onChange={(event) => setName(event.target.value)}
              placeholder="North Clinic"
              autoComplete="organization"
            />
          </Label>
          <Label>
            Country
            <Select ariaLabel="Country" value={country} onValueChange={setCountry} options={COUNTRY_OPTIONS} searchPlaceholder="Search countries…" />
          </Label>
          <p className="m-0 text-[0.8rem] leading-relaxed text-fg-muted">
            You can build and test agents straight away. Phone numbers and live calls start once Sauti approves the workspace.
          </p>
          {error ? <Alert tone="error">{error}</Alert> : null}
          <div className="mt-1 flex justify-end gap-2">
            <Button type="button" variant="secondary" size="sm" onClick={() => onOpenChange(false)} disabled={busy}>
              Cancel
            </Button>
            <Button type="submit" size="sm" disabled={busy || !name.trim()}>
              {busy ? <LoaderCircle className="animate-spin" aria-hidden="true" /> : null}
              {busy ? "Creating…" : "Create workspace"}
            </Button>
          </div>
        </form>
      </DialogContent>
    </Dialog>
  );
}
