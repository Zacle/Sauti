"use client";

import { useCallback, useEffect, useState } from "react";
import { LoaderCircle } from "lucide-react";
import { Button } from "@/components/ui/button";
import { workspaceAdminApi } from "@/lib/api/auth";
import type { ActivityEntry } from "@/types/api";
import styles from "./WorkspaceSettings.module.css";

const timeFormat = new Intl.DateTimeFormat("en-GB", { day: "numeric", month: "short", hour: "2-digit", minute: "2-digit" });

/** Settings → Activity: who changed members, invitations, keys and the workspace, newest first. */
export function ActivitySection() {
  const [entries, setEntries] = useState<ActivityEntry[]>([]);
  const [loading, setLoading] = useState(true);
  const [more, setMore] = useState(false);
  const [failure, setFailure] = useState("");

  const load = useCallback(async (before?: string) => {
    setFailure("");
    try {
      const page = await workspaceAdminApi.activity(before);
      setEntries((current) => (before ? [...current, ...page] : page));
      setMore(page.length === 50);
    } catch (caught) {
      setFailure(caught instanceof Error ? caught.message : "Unable to load activity.");
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void load();
  }, [load]);

  return (
    <section className={styles.panel}>
      <div className={styles.panelHeader}>
        <div>
          <h2>Activity</h2>
          <p>Changes to members, invitations, API keys and the workspace itself. Visible to Admins and Owners.</p>
        </div>
      </div>
      {failure ? <div className={styles.inlineError} role="alert">{failure}</div> : null}
      {loading ? (
        <p className={styles.memberEmpty}><LoaderCircle className="animate-spin" size={15} aria-hidden="true" /> Loading activity…</p>
      ) : entries.length === 0 ? (
        <p className={styles.memberEmpty}>Nothing has happened here yet.</p>
      ) : (
        <ul className={styles.memberList}>
          {entries.map((entry) => (
            <li key={entry.id} className={styles.activityRow}>
              <span className={styles.memberIdentity}>
                <strong>{entry.summary}</strong>
                <small>{entry.actor}</small>
              </span>
              <time dateTime={entry.at}>{timeFormat.format(new Date(entry.at))}</time>
            </li>
          ))}
        </ul>
      )}
      {more ? (
        <div className={styles.actionBar}>
          <small>Showing the latest {entries.length} changes.</small>
          <Button size="xs" variant="secondary" onClick={() => void load(entries[entries.length - 1]?.at)}>Show older</Button>
        </div>
      ) : null}
    </section>
  );
}
