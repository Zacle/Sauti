import type { Metadata } from "next";
import { AdminWorkspaces } from "@/features/admin/presentation/AdminWorkspaces";

export const metadata: Metadata = { title: "Workspaces · Sauti Admin" };
export default function AdminWorkspacesPage() { return <AdminWorkspaces/>; }
