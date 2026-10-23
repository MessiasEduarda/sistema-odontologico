import react from "@vitejs/plugin-react";
import { fileURLToPath } from "node:url";
import { defineConfig } from "vitest/config";

const staticImages = {
  name: "next-static-images",
  enforce: "pre" as const,
  load(id: string) {
    if (/\.(png|jpe?g|webp)$/.test(id)) return `export default { src: "/test-image.png", width: 1, height: 1 };`;
  },
};

export default defineConfig({
  plugins: [staticImages, react()],
  resolve: {
    alias: { "@": fileURLToPath(new URL("./src", import.meta.url)) },
  },
  test: {
    setupFiles: ["./src/test/setup.ts"],
    css: false,
    projects: [
      {
        extends: true,
        test: {
          name: "dom",
          environment: "jsdom",
          include: ["src/**/*.test.{ts,tsx}"],
          exclude: ["src/lib/pdf/**"],
        },
      },
      {
        extends: true,
        test: { name: "pdf", environment: "node", include: ["src/lib/pdf/**/*.test.{ts,tsx}"] },
      },
    ],
  },
});
