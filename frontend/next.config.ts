import type { NextConfig } from "next";

// Endereço da API usado pelo servidor do Next.js (nunca exposto ao navegador).
// O navegador sempre chama /api/* na mesma origem; o Next repassa para o back-end,
// o que permite cookies httpOnly SameSite sem CORS.
// Atenção: rewrites são resolvidos no build — em Docker, passe API_INTERNAL_URL como build arg.
const apiInternalUrl = process.env.API_INTERNAL_URL ?? "http://localhost:8080";

const securityHeaders = [
  { key: "X-Content-Type-Options", value: "nosniff" },
  { key: "X-Frame-Options", value: "DENY" },
  { key: "Referrer-Policy", value: "strict-origin-when-cross-origin" },
  { key: "Permissions-Policy", value: "camera=(), microphone=(), geolocation=()" },
];

const nextConfig: NextConfig = {
  cacheComponents: true,
  partialPrefetching: true,
  output: "standalone",
  poweredByHeader: false,
  compiler: {
    styledComponents: true,
  },
  async rewrites() {
    return [{ source: "/api/:path*", destination: `${apiInternalUrl}/api/:path*` }];
  },
  async headers() {
    return [{ source: "/:path*", headers: securityHeaders }];
  },
};

export default nextConfig;
