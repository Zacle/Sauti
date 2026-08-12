import type { Metadata } from "next";
import { AdminLaunchReadiness } from "@/features/admin/presentation/AdminLaunchReadiness";

export const metadata: Metadata = { title: "Launch readiness · Sauti Admin" };

export default function AdminLaunchReadinessPage() {
  return <AdminLaunchReadiness />;
}
