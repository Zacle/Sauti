import type { Metadata } from "next";
import { AdminAnalytics } from "@/features/admin/presentation/AdminAnalytics";

export const metadata: Metadata = { title: "Platform analytics · Sauti Admin" };
export default function AdminAnalyticsPage() { return <AdminAnalytics/>; }
