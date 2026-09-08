import type { NextConfig } from "next";

const nextConfig: NextConfig = {
  reactStrictMode: true,
  output: "export",
  basePath: "/assets/site",
  assetPrefix: "/assets/site/",
  trailingSlash: true,
  images: { unoptimized: true },
};

export default nextConfig;
