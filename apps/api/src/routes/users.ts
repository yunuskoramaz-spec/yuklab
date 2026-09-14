import type { FastifyInstance } from "fastify";
import { prisma } from "../lib/prisma";
import { requireAuth } from "../modules/auth/guard";
import { cleanProfile, objectValue } from "../modules/directory/profile";

const ownSelect = { id: true, firstName: true, lastName: true, email: true, phone: true, preferredLanguage: true, role: true, status: true, createdAt: true, profile: true } as const;
export async function userRoutes(app: FastifyInstance) {
  app.get("/v1/users/me", { preHandler: requireAuth }, async (request) => {
    const user = await prisma.user.findUniqueOrThrow({ where: { id: request.user!.id }, select: ownSelect });
    return { user };
  });
  app.patch("/v1/users/me", { preHandler: requireAuth }, async (request, reply) => {
    const body = objectValue(request.body);
    const data: { firstName?: string; lastName?: string; profile?: Record<string, string | boolean> } = {};
    for (const key of ["firstName", "lastName"] as const) {
      if (body[key] !== undefined) {
        if (typeof body[key] !== "string" || !body[key].trim() || body[key].length > 100) return reply.code(400).send({ error: "INVALID_INPUT" });
        data[key] = body[key].trim();
      }
    }
    if (body.profile !== undefined) {
      if (body.profile === null || typeof body.profile !== "object" || Array.isArray(body.profile)) return reply.code(400).send({ error: "INVALID_PROFILE" });
      try {
        const existing = await prisma.user.findUniqueOrThrow({ where: { id: request.user!.id }, select: { profile: true } });
        data.profile = { ...cleanProfile(existing.profile), ...cleanProfile(body.profile) };
      } catch (error) {
        if (error instanceof Error && error.message === "INVALID_PROFILE") return reply.code(400).send({ error: "INVALID_PROFILE" });
        throw error;
      }
    }
    const user = await prisma.user.update({ where: { id: request.user!.id }, data, select: ownSelect });
    return { user };
  });
  app.get("/v1/users/count", async () => ({ count: await prisma.user.count() }));
}
