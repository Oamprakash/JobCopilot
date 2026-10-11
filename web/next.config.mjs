/** @type {import('next').NextConfig} */
const apiUrl = process.env.API_URL || "http://localhost:8080";

const nextConfig = {
  reactStrictMode: true,
  // Resume parsing with Claude can take longer than the 30 s default proxy timeout.
  experimental: { proxyTimeout: 120_000 },
  // The browser only ever talks to this origin; /api/* is proxied to the Ktor server,
  // so the HttpOnly session cookie is first-party and no CORS setup is needed.
  async rewrites() {
    return [{ source: "/api/:path*", destination: `${apiUrl}/:path*` }];
  },
};

export default nextConfig;
