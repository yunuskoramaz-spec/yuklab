import type { FastifyInstance } from "fastify";
import { prisma } from "../../lib/prisma";
import { requireAuth } from "../auth/guard";

const MAX_PAGE_SIZE = 100;

export async function notificationRoutes(app: FastifyInstance) {
  app.get<{ Querystring: { unreadOnly?: string; limit?: string } }>(
    "/v1/notifications",
    { preHandler: requireAuth },
    async (request) => {
      const unreadOnly = request.query?.unreadOnly === "true";
      const parsedLimit = Number(request.query?.limit ?? 50);
      const limit = Number.isFinite(parsedLimit) ? Math.max(1, Math.min(MAX_PAGE_SIZE, Math.trunc(parsedLimit))) : 50;
      const notifications = await prisma.notification.findMany({
        where: { userId: request.user!.id, ...(unreadOnly ? { readAt: null } : {}) },
        orderBy: { createdAt: "desc" },
        take: limit,
      });
      const unreadCount = await prisma.notification.count({ where: { userId: request.user!.id, readAt: null } });
      return { notifications, unreadCount };
    },
  );

  app.patch<{ Params: { id: string } }>(
    "/v1/notifications/:id/read",
    { preHandler: requireAuth },
    async (request, reply) => {
      const result = await prisma.notification.updateMany({
        where: { id: request.params.id, userId: request.user!.id },
        data: { readAt: new Date() },
      });
      if (result.count === 0) return reply.code(404).send({ error: "NOTIFICATION_NOT_FOUND" });
      const notification = await prisma.notification.findUniqueOrThrow({ where: { id: request.params.id } });
      return { notification };
    },
  );

  app.post("/v1/notifications/read-all", { preHandler: requireAuth }, async (request) => {
    const result = await prisma.notification.updateMany({
      where: { userId: request.user!.id, readAt: null },
      data: { readAt: new Date() },
    });
    return { updated: result.count };
  });
}
