import type { Metadata } from "next";
import { AdminBillingReadiness } from "@/features/admin/presentation/AdminBillingReadiness";

export const metadata: Metadata = { title: "Billing readiness · Sauti Admin" };
export default function AdminBillingReadinessPage() {
  return <AdminBillingReadiness />;
}
