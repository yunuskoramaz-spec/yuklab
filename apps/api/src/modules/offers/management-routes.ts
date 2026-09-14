import type { FastifyInstance } from "fastify";
import { prisma } from "../../lib/prisma";
import { requireRole } from "../auth/guard";

const MAX_ETA_MINUTES = 7 * 24 * 60;
const MAX_NOTE_LENGTH = 1000;
const MAX_OFFER_AMOUNT_MINOR = 9223372036854775807n;

function serializeBigInt<T>(value: T): T {
  return JSON.parse(JSON.stringify(value, (_, v) => (typeof v === "bigint" ? v.toString() : v))) as T;
}

function parseAmount(value: unknown): bigint | undefined | null {
  if (value === undefined) return undefined;
  try {
    if (typeof value === "number" && !Number.isSafeInteger(value)) return null;
    if (typeof value !== "number" && typeof value !== "string") return null;
    const parsed = BigInt(value);
    return parsed > 0n && parsed <= MAX_OFFER_AMOUNT_MINOR ? parsed : null;
  } catch {
    return null;
  }
}

export async function offerManagementRoutes(app: FastifyInstance) {
  app.patch<{
    Params: { orderId: string; offerId: string };
    Body: { amountMinor?: string | number; etaMinutes?: number | null; note?: string | null; expiresAt?: string | null };
  }>("/v1/orders/:orderId/offers/:offerId", { preHandler: requireRole("DRIVER", "SERVICE_PROVIDER") }, async (request, reply) => {
    const existing = await prisma.offer.findFirst({
      where: { id: request.params.offerId, orderId: request.params.orderId, providerId: request.user!.id },
      include: { order: { select: { status: true } } },
    });
    if (!existing) return reply.code(404).send({ error: "OFFER_NOT_FOUND" });
    if (existing.status !== "PENDING" || !["PUBLISHED", "OFFERING"].includes(existing.order.status)) return reply.code(409).send({ error: "OFFER_NOT_EDITABLE" });

    const amountMinor = parseAmount(request.body?.amountMinor);
    if (amountMinor === null) return reply.code(400).send({ error: "INVALID_AMOUNT" });

    const etaMinutes: number | null | undefined = request.body?.etaMinutes;
    if (etaMinutes !== undefined && etaMinutes !== null) {
      if (!Number.isInteger(etaMinutes) || etaMinutes < 1 || etaMinutes > MAX_ETA_MINUTES) return reply.code(400).send({ error: "INVALID_ETA" });
    }

    let note: string | null | undefined = request.body?.note;
    if (note !== undefined && note !== null) {
      if (typeof note !== "string") return reply.code(400).send({ error: "INVALID_NOTE" });
      note = note.trim();
      if (note.length > MAX_NOTE_LENGTH) return reply.code(400).send({ error: "NOTE_TOO_LONG" });
      if (!note) note = null;
    }

    let expiresAt: Date | null | undefined;
    if (request.body?.expiresAt === null) expiresAt = null;
    else if (request.body?.expiresAt !== undefined) {
      const parsed = new Date(request.body.expiresAt);
      if (Number.isNaN(parsed.getTime()) || parsed <= new Date()) return reply.code(400).send({ error: "INVALID_EXPIRY" });
      expiresAt = parsed;
    }

    const offer = await prisma.offer.update({
      where: { id: existing.id },
      data: {
        ...(amountMinor !== undefined ? { amountMinor } : {}),
        ...(etaMinutes !== undefined ? { etaMinutes } : {}),
        ...(note !== undefined ? { note } : {}),
        ...(expiresAt !== undefined ? { expiresAt } : {}),
      },
    });
    return { offer: serializeBigInt(offer) };
  });

  app.delete<{ Params: { orderId: string; offerId: string } }>(
    "/v1/orders/:orderId/offers/:offerId",
    { preHandler: requireRole("DRIVER", "SERVICE_PROVIDER") },
    async (request, reply) => {
      const result = await prisma.offer.updateMany({
        where: {
          id: request.params.offerId,
          orderId: request.params.orderId,
          providerId: request.user!.id,
          status: "PENDING",
          order: { status: { in: ["PUBLISHED", "OFFERING"] } },
        },
        data: { status: "WITHDRAWN" },
      });
      if (result.count === 0) return reply.code(409).send({ error: "OFFER_NOT_WITHDRAWABLE" });
      return reply.code(204).send();
    },
  );
}
