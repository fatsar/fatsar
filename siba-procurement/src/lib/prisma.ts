import { PrismaClient } from "@/generated/prisma/client";
import { PrismaBetterSqlite3 } from "@prisma/adapter-better-sqlite3";

// Relative SQLite URLs resolve against the process working directory, which is
// the project root for `next dev`, `next start` and all Prisma CLI commands.
const databaseUrl = process.env.DATABASE_URL ?? "file:./prisma/dev.db";

function createClient() {
  const adapter = new PrismaBetterSqlite3({ url: databaseUrl });
  return new PrismaClient({ adapter });
}

// Reuse a single client across Next.js hot reloads in development.
const globalForPrisma = globalThis as unknown as { prisma?: PrismaClient };

export const prisma = globalForPrisma.prisma ?? createClient();

if (process.env.NODE_ENV !== "production") {
  globalForPrisma.prisma = prisma;
}
