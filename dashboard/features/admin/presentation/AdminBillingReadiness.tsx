"use client";

import { useCallback, useEffect, useState } from "react";
import {
  AlertTriangle,
  Check,
  CircleDashed,
  Clock3,
  CreditCard,
  KeyRound,
  RefreshCw,
  ShieldCheck,
  Webhook,
} from "lucide-react";
import { getAdminBillingReadiness } from "@/lib/api/admin";
import type { AdminBillingReadiness as Readiness } from "@/types/api";
import styles from "./AdminBillingReadiness.module.css";

export function AdminBillingReadiness() {
  const [data, setData] = useState<Readiness | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const load = useCallback(async () => {
    setLoading(true);
    setError("");
    try {
      setData(await getAdminBillingReadiness());
    } catch (caught) {
      setError(
        caught instanceof Error
          ? caught.message
          : "Unable to load billing readiness.",
      );
    } finally {
      setLoading(false);
    }
  }, []);
  useEffect(() => {
    void load();
  }, [load]);

  return (
    <div className={styles.page}>
      <header className={styles.heading}>
        <div>
          <span>PHASE 3 ACCEPTANCE</span>
          <h1>Billing readiness</h1>
          <p>
            Six-plan configuration plus one safe representative subscription
            lifecycle. Refreshing never contacts Whop or creates a charge.
          </p>
        </div>
        <button disabled={loading} onClick={() => void load()} type="button">
          <RefreshCw className={loading ? styles.spin : ""} size={16} />
          Refresh evidence
        </button>
      </header>
      {error && (
        <div className={styles.error} role="alert">
          {error}
        </div>
      )}
      {data && (
        <>
          <section className={`${styles.summary} ${styles[data.status] ?? ""}`}>
            <span>
              <CreditCard size={22} />
            </span>
            <div>
              <small>OVERALL STATUS</small>
              <strong>{statusLabel(data.status)}</strong>
              <p>{summary(data)}</p>
            </div>
            <b>
              {data.configuredPlans} / {data.variants.length} plans configured
            </b>
          </section>
          <section
            className={styles.setup}
            aria-label="Whop configuration checks"
          >
            <Setup
              icon={KeyRound}
              label="API and company"
              ready={data.apiConfigured}
            />
            <Setup
              icon={Webhook}
              label="Signed webhook"
              ready={data.webhookConfigured}
            />
            <Setup
              icon={ShieldCheck}
              label="Tenant signing"
              ready={data.tenantSigningConfigured}
            />
            <Setup
              icon={CreditCard}
              label="Six base plans"
              ready={data.plansConfigured}
            />
          </section>
          <section className={styles.context}>
            <div>
              <small>Provider</small>
              <strong>
                {human(data.provider)} · {human(data.environment)}
              </strong>
            </div>
            <div>
              <small>Normalized sandbox events</small>
              <strong>{data.normalizedSandboxEvents}</strong>
            </div>
            <div>
              <small>Retrying / failed events</small>
              <strong
                className={data.failedProviderEvents ? styles.danger : ""}
              >
                {data.retryingProviderEvents} / {data.failedProviderEvents}
              </strong>
            </div>
            <div>
              <small>Latest evidence</small>
              <strong>{when(data.lastSandboxEvidenceAt)}</strong>
            </div>
          </section>
          <section className={styles.lifecycle}>
            <header>
              <div>
                <span>REPRESENTATIVE LIFECYCLE</span>
                <h2>
                  {data.representativeLifecycle.plan
                    ? `${human(data.representativeLifecycle.plan)} · ${human(data.representativeLifecycle.interval ?? "")}`
                    : "One new sandbox subscription"}
                </h2>
                <p>
                  Membership {data.representativeLifecycle.membershipReference ?? "not created"}
                </p>
              </div>
              <b className={styles[data.representativeLifecycle.status] ?? ""}>
                {statusLabel(data.representativeLifecycle.status)}
              </b>
            </header>
            <div>
              <Step label="Activated" value={data.representativeLifecycle.membershipActivatedAt}/>
              <Step label="Paid" value={data.representativeLifecycle.paymentSucceededAt}/>
              <Step label="Cancellation scheduled" value={data.representativeLifecycle.cancellationObservedAt}/>
            </div>
            <p>Upgrade and downgrade collection are deliberately excluded from this test. Sauti handles plan changes at the paid-period boundary instead of creating duplicate memberships.</p>
          </section>
          <section className={styles.matrix}>
            <header>
              <div>
                <span>ACCEPTANCE MATRIX</span>
                <h2>Base plan configuration</h2>
              </div>
              <p>
                All six plan IDs must be present. They do not each require a
                paid membership or cancellation cycle.
              </p>
            </header>
            <div className={styles.rows}>
              {data.variants.map((variant) => (
                <article key={`${variant.plan}:${variant.interval}`}>
                  <div className={styles.plan}>
                    <span className={styles[variant.status] ?? ""}>
                      {statusIcon(variant.status)}
                    </span>
                    <div>
                      <strong>{human(variant.plan)}</strong>
                      <small>
                        {human(variant.interval)} ·{" "}
                        {variant.planReference ?? "Plan ID missing"}
                      </small>
                    </div>
                  </div>
                  <Step label="Historical sandbox evidence" value={variant.sandboxEvidenceAt}/>
                  <span
                    className={`${styles.badge} ${styles[variant.status] ?? ""}`}
                  >
                    {statusLabel(variant.status)}
                  </span>
                </article>
              ))}
            </div>
          </section>
          <section className={styles.instructions}>
            <AlertTriangle size={19} />
            <div>
              <strong>How to complete acceptance</strong>
              <p>
                Use one configured plan to create one new sandbox subscription,
                complete its test payment, and schedule cancellation for that
                exact membership. Do not purchase all six variants and do not
                use upgrade or downgrade as an acceptance shortcut.
              </p>
            </div>
          </section>
          <footer>
            Generated {new Date(data.generatedAt).toLocaleString()} · Add-on
            configuration: {data.addOnsConfigured ? "complete" : "incomplete"}
          </footer>
        </>
      )}
      {loading && !data && (
        <div className={styles.loading}>
          <CircleDashed className={styles.spin} />
          Loading stored billing evidence…
        </div>
      )}
    </div>
  );
}

function Setup({
  icon: Icon,
  label,
  ready,
}: {
  icon: typeof KeyRound;
  label: string;
  ready: boolean;
}) {
  return (
    <article>
      <span className={ready ? styles.readyIcon : styles.missingIcon}>
        {ready ? <Check size={17} /> : <AlertTriangle size={17} />}
      </span>
      <Icon size={18} />
      <div>
        <strong>{label}</strong>
        <small>{ready ? "Configured" : "Action required"}</small>
      </div>
    </article>
  );
}
function Step({ label, value }: { label: string; value: string | null }) {
  return (
    <div className={styles.step}>
      {value ? <Check size={15} /> : <Clock3 size={15} />}
      <span>
        <small>{label}</small>
        <strong>{when(value)}</strong>
      </span>
    </div>
  );
}
function statusIcon(status: string) {
  return status === "accepted" ? (
    <Check size={18} />
  ) : status === "configuration_missing" ? (
    <AlertTriangle size={18} />
  ) : (
    <Clock3 size={18} />
  );
}
function statusLabel(status: string) {
  return human(status.replace("awaiting_", "waiting for "));
}
function summary(data: Readiness) {
  if (data.status === "configuration_missing")
    return "Complete the server-only Whop configuration before sandbox acceptance.";
  if (data.status === "attention")
    return "At least one provider event exhausted its retries and needs investigation.";
  if (data.status === "ready")
    return "All six plan IDs are configured and one complete sandbox lifecycle is recorded.";
  return "Complete one representative purchase and cancellation lifecycle below.";
}
function when(value: string | null) {
  return value ? new Date(value).toLocaleString() : "Not recorded";
}
function human(value: string) {
  return value
    .replaceAll("_", " ")
    .replace(/\b\w/g, (letter) => letter.toUpperCase());
}
