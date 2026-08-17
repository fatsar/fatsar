import "dotenv/config";
import { defineConfig } from "prisma/config";

export default defineConfig({
  schema: "prisma/schema.prisma",
  migrations: {
    path: "prisma/migrations",
    seed: "tsx prisma/seed.ts",
  },
  datasource: {
    // Relative SQLite paths resolve against the project root (CLI and runtime
    // both run from the project root).
    url: process.env["DATABASE_URL"] ?? "file:./prisma/dev.db",
  },
});
