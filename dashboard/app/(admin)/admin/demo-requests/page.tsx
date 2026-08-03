import type { Metadata } from "next";
import { AdminDemoRequests } from "@/features/admin/presentation/AdminDemoRequests";

export const metadata: Metadata = { title: "Demo requests | Sauti Admin" };
export default function AdminDemoRequestsPage() { return <AdminDemoRequests />; }
