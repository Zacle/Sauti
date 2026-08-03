import type { Metadata } from "next";
import { AdminOverview } from "@/features/admin/presentation/AdminOverview";

export const metadata: Metadata = { title: "Sauti Admin" };
export default function AdminPage() { return <AdminOverview />; }
