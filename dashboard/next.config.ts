import type { NextConfig } from "next";

const nextConfig: NextConfig = {
  reactStrictMode: true,
  devIndicators: false,
  async redirects() {
    return [
      { source: "/resources", destination: "/", permanent: true },
      { source: "/resources/:slug*", destination: "/", permanent: true },
    ];
  },
  async rewrites() {
    return [
      {
        source: "/api/:path*",
        destination: `${process.env.SAUTI_API_BASE_URL ?? "http://localhost:8080"}/api/:path*`,
      },
    ];
  },
};

export default nextConfig;
