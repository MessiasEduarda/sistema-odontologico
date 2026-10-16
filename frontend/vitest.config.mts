import react from "@vitejs/plugin-react";
import { fileURLToPath } from "node:url";
import { defineConfig } from "vitest/config";

/** Imagens importadas viram o mesmo formato do Next ({ src, width, height }), exigido pelo next/image. */
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
    environment: "jsdom",
    setupFiles: ["./src/test/setup.ts"],
    include: ["src/**/*.test.{ts,tsx}"],
    css: false,
  },
});
