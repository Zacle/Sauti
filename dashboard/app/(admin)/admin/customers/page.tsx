import type { Metadata } from "next";
import { AdminCustomers } from "@/features/admin/presentation/AdminCustomers";

export const metadata: Metadata = { title: "Customers · Sauti Admin" };
export default function AdminCustomersPage() { return <AdminCustomers/>; }
