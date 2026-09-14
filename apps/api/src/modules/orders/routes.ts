import type { FastifyInstance } from "fastify";
import { prisma } from "../../lib/prisma";
import { requireAuth } from "../auth/guard";
import type { Prisma } from "@yuklab/database";

type OrderCreateBody = {
  serviceType: string;
  pickupAddress: string;
  deliveryAddress?: string;
  pickupLat?: number;
  pickupLng?: number;
  deliveryLat?: number;
  deliveryLng?: number;
  scheduledAt?: string;
  budgetMinor?: string | number;
  currency?: string;
  urgency?: number;
  payload?: Prisma.InputJsonValue;
};

type OrderUpdateBody = Partial<OrderCreateBody>;

const CURRENCY_RE = /^[A-Z]{3}$/;
const MAX_ADDRESS_LENGTH = 500;
const MAX_SERVICE_TYPE_LENGTH = 80;
const MAX_BUDGET_MINOR = 9223372036854775807n;
const EDITABLE_STATUSES = ["DRAFT", "PUBLISHED", "OFFERING"] as const;

const orderSelect = {
  id: true,
  customerId: true,
  assignedDriverId: true,
  vehicleId: true,
  serviceType: true,
  status: true,
  pickupAddress: true,
  deliveryAddress: true,
  pickupLat: true,
  pickupLng: true,
  deliveryLat: true,
  deliveryLng: true,
  scheduledAt: true,
  budgetMinor: true,
  currency: true,
  urgency: true,
  payload: true,
  createdAt: true,
  updatedAt: true,
} as const;

export async function orderRoutes(app: FastifyInstance) {
  app.post<{ Body: OrderCreateBody }>(
    "/v1/orders",
    { preHandler: requireAuth },
    async (request, reply) => {
      const body = request.body;
      const serviceType = body.serviceType?.trim();
      const pickupAddress = body.pickupAddress?.trim();
      const deliveryAddress = body.deliveryAddress?.trim();

      if (!serviceType || !pickupAddress || serviceType.length > MAX_SERVICE_TYPE_LENGTH || pickupAddress.length > MAX_ADDRESS_LENGTH || (deliveryAddress !== undefined && deliveryAddress.length > MAX_ADDRESS_LENGTH)) {
        return reply.code(400).send({ error: "INVALID_INPUT" });
      }

      const coordinates = validateCoordinates(body);
      if (!coordinates.ok) return reply.code(400).send({ error: "INVALID_COORDINATES" });

      const budgetMinor = parseBudgetMinor(body.budgetMinor);
      if (body.budgetMinor !== undefined && budgetMinor === null) return reply.code(400).send({ error: "INVALID_BUDGET" });

      const currency = (body.currency ?? "TRY").trim().toUpperCase();
      if (!CURRENCY_RE.test(currency)) return reply.code(400).send({ error: "INVALID_CURRENCY" });

      const urgency = Number(body.urgency ?? 0);
      if (!Number.isFinite(urgency)) return reply.code(400).send({ error: "INVALID_URGENCY" });
      const normalizedUrgency = Math.trunc(Math.max(0, Math.min(100, urgency)));

      const scheduledAt = parseScheduledAt(body.scheduledAt);
      if (body.scheduledAt !== undefined && scheduledAt === null) return reply.code(400).send({ error: "INVALID_SCHEDULED_AT" });

      const order = await prisma.order.create({
        data: {
          customerId: request.user!.id,
          serviceType,
          pickupAddress,
          deliveryAddress: deliveryAddress || undefined,
          pickupLat: coordinates.pickupLat,
          pickupLng: coordinates.pickupLng,
          deliveryLat: coordinates.deliveryLat,
          deliveryLng: coordinates.deliveryLng,
          scheduledAt: scheduledAt ?? undefined,
          budgetMinor: budgetMinor ?? undefined,
          currency,
          urgency: normalizedUrgency,
          payload: body.payload,
          status: "PUBLISHED",
        },
        select: orderSelect,
      });

      return reply.code(201).send({ order: serializeBigInt(order) });
    },
  );

  app.get("/v1/orders", { preHandler: requireAuth }, async (request) => {
    const orders = await prisma.order.findMany({
      where: { customerId: request.user!.id },
      orderBy: { createdAt: "desc" },
      take: 50,
      select: orderSelect,
    });
    return { orders: orders.map(serializeBigInt) };
  });

  app.get<{ Params: { id: string } }>("/v1/orders/:id", { preHandler: requireAuth }, async (request, reply) => {
    const order = await prisma.order.findFirst({ where: { id: request.params.id, customerId: request.user!.id }, select: orderSelect });
    if (!order) return reply.code(404).send({ error: "ORDER_NOT_FOUND" });
    return { order: serializeBigInt(order) };
  });

  app.patch<{ Params: { id: string }; Body: OrderUpdateBody }>(
    "/v1/orders/:id",
    { preHandler: requireAuth },
    async (request, reply) => {
      const current = await prisma.order.findFirst({ where: { id: request.params.id, customerId: request.user!.id } });
      if (!current) return reply.code(404).send({ error: "ORDER_NOT_FOUND" });
      if (!EDITABLE_STATUSES.includes(current.status as (typeof EDITABLE_STATUSES)[number])) return reply.code(409).send({ error: "ORDER_NOT_EDITABLE" });

      const body = request.body ?? {};
      const data: Prisma.OrderUpdateInput = {};

      if (body.serviceType !== undefined) {
        const value = body.serviceType.trim();
        if (!value || value.length > MAX_SERVICE_TYPE_LENGTH) return reply.code(400).send({ error: "INVALID_SERVICE_TYPE" });
        data.serviceType = value;
      }
      if (body.pickupAddress !== undefined) {
        const value = body.pickupAddress.trim();
        if (!value || value.length > MAX_ADDRESS_LENGTH) return reply.code(400).send({ error: "INVALID_PICKUP_ADDRESS" });
        data.pickupAddress = value;
      }
      if (body.deliveryAddress !== undefined) {
        const value = body.deliveryAddress.trim();
        if (value.length > MAX_ADDRESS_LENGTH) return reply.code(400).send({ error: "INVALID_DELIVERY_ADDRESS" });
        data.deliveryAddress = value || null;
      }
      if (body.currency !== undefined) {
        const value = body.currency.trim().toUpperCase();
        if (!CURRENCY_RE.test(value)) return reply.code(400).send({ error: "INVALID_CURRENCY" });
        data.currency = value;
      }
      if (body.budgetMinor !== undefined) {
        const value = parseBudgetMinor(body.budgetMinor);
        if (value === null) return reply.code(400).send({ error: "INVALID_BUDGET" });
        data.budgetMinor = value;
      }
      if (body.urgency !== undefined) {
        const urgency = Number(body.urgency);
        if (!Number.isFinite(urgency)) return reply.code(400).send({ error: "INVALID_URGENCY" });
        data.urgency = Math.trunc(Math.max(0, Math.min(100, urgency)));
      }
      if (body.scheduledAt !== undefined) {
        const scheduledAt = parseScheduledAt(body.scheduledAt);
        if (scheduledAt === null) return reply.code(400).send({ error: "INVALID_SCHEDULED_AT" });
        data.scheduledAt = scheduledAt;
      }
      if (body.payload !== undefined) data.payload = body.payload;

      const coordinatePatch = {
        pickupLat: body.pickupLat !== undefined ? body.pickupLat : decimalToNumber(current.pickupLat),
        pickupLng: body.pickupLng !== undefined ? body.pickupLng : decimalToNumber(current.pickupLng),
        deliveryLat: body.deliveryLat !== undefined ? body.deliveryLat : decimalToNumber(current.deliveryLat),
        deliveryLng: body.deliveryLng !== undefined ? body.deliveryLng : decimalToNumber(current.deliveryLng),
      };
      if ([body.pickupLat, body.pickupLng, body.deliveryLat, body.deliveryLng].some((value) => value !== undefined)) {
        const coordinates = validateCoordinates(coordinatePatch);
        if (!coordinates.ok) return reply.code(400).send({ error: "INVALID_COORDINATES" });
        data.pickupLat = coordinates.pickupLat;
        data.pickupLng = coordinates.pickupLng;
        data.deliveryLat = coordinates.deliveryLat;
        data.deliveryLng = coordinates.deliveryLng;
      }

      const order = await prisma.order.update({ where: { id: current.id }, data, select: orderSelect });
      return { order: serializeBigInt(order) };
    },
  );

  app.delete<{ Params: { id: string } }>("/v1/orders/:id", { preHandler: requireAuth }, async (request, reply) => {
    const current = await prisma.order.findFirst({ where: { id: request.params.id, customerId: request.user!.id }, select: { id: true, status: true } });
    if (!current) return reply.code(404).send({ error: "ORDER_NOT_FOUND" });
    if (!EDITABLE_STATUSES.includes(current.status as (typeof EDITABLE_STATUSES)[number])) return reply.code(409).send({ error: "ORDER_NOT_CANCELLABLE" });

    await prisma.$transaction(async (tx) => {
      await tx.order.update({ where: { id: current.id }, data: { status: "CANCELLED" } });
      await tx.offer.updateMany({ where: { orderId: current.id, status: "PENDING" }, data: { status: "REJECTED" } });
      await tx.trackingEvent.create({ data: { orderId: current.id, actorId: request.user!.id, eventType: "ORDER_CANCELLED" } });
      await tx.auditLog.create({ data: { actorId: request.user!.id, action: "ORDER_CANCELLED", entityType: "Order", entityId: current.id } });
    });
    return reply.code(204).send();
  });
}

function parseBudgetMinor(value: string | number | undefined): bigint | null {
  if (value === undefined) return null;
  if (typeof value === "number") {
    if (!Number.isSafeInteger(value) || value < 0) return null;
    const parsed = BigInt(value);
    return parsed <= MAX_BUDGET_MINOR ? parsed : null;
  }
  if (!/^\d+$/.test(value)) return null;
  try {
    const parsed = BigInt(value);
    return parsed <= MAX_BUDGET_MINOR ? parsed : null;
  } catch {
    return null;
  }
}

function parseScheduledAt(value: string | undefined): Date | null {
  if (value === undefined) return null;
  const parsed = new Date(value);
  return Number.isFinite(parsed.getTime()) ? parsed : null;
}

function validateCoordinate(value: number | undefined, min: number, max: number): number | undefined | null {
  if (value === undefined) return undefined;
  if (!Number.isFinite(value) || value < min || value > max) return null;
  return value;
}

function validateCoordinates(body: { pickupLat?: number; pickupLng?: number; deliveryLat?: number; deliveryLng?: number }): { ok: true; pickupLat?: number; pickupLng?: number; deliveryLat?: number; deliveryLng?: number } | { ok: false } {
  const pickupLat = validateCoordinate(body.pickupLat, -90, 90);
  const pickupLng = validateCoordinate(body.pickupLng, -180, 180);
  const deliveryLat = validateCoordinate(body.deliveryLat, -90, 90);
  const deliveryLng = validateCoordinate(body.deliveryLng, -180, 180);

  if (pickupLat === null || pickupLng === null || deliveryLat === null || deliveryLng === null) return { ok: false };
  if ((pickupLat === undefined) !== (pickupLng === undefined) || (deliveryLat === undefined) !== (deliveryLng === undefined)) return { ok: false };
  return { ok: true, pickupLat, pickupLng, deliveryLat, deliveryLng };
}

function decimalToNumber(value: unknown): number | undefined {
  if (value === null || value === undefined) return undefined;
  const parsed = Number(typeof value === "object" && value !== null && "toString" in value ? value.toString() : value);
  return Number.isFinite(parsed) ? parsed : undefined;
}

function serializeBigInt<T>(value: T): T {
  return JSON.parse(JSON.stringify(value, (_, v) => (typeof v === "bigint" ? v.toString() : v))) as T;
}
