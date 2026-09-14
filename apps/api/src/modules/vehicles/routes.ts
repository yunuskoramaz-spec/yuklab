import type { FastifyInstance } from "fastify";
import { prisma } from "../../lib/prisma";
import { requireRole } from "../auth/guard";

const VEHICLE_TYPES = new Set([
  "MOTORCYCLE",
  "VAN",
  "TRUCK",
  "KIRKAYAK",
  "TIR",
  "TOW_TRUCK",
  "REFRIGERATED",
  "DUMP_TRUCK",
  "FLATBED",
  "LOWBED",
]);

function finiteNumber(value: unknown): number | undefined {
  if (value === undefined || value === null || value === "") return undefined;
  const number = typeof value === "number" ? value : Number(value);
  return Number.isFinite(number) ? number : undefined;
}

function serializeDecimal(value: unknown) {
  return typeof value === "object" && value !== null && "toString" in value
    ? value.toString()
    : value;
}

function serializeVehicle<T extends Record<string, unknown>>(vehicle: T) {
  return {
    ...vehicle,
    capacityKg: serializeDecimal(vehicle.capacityKg),
    volumeM3: serializeDecimal(vehicle.volumeM3),
  };
}

function validateVehicleBody(body: Record<string, unknown>) {
  const type = typeof body.type === "string" ? body.type.trim().toUpperCase() : "";
  if (!VEHICLE_TYPES.has(type)) return { error: "Invalid vehicle type" };

  const subtypeValue = body.subtype;
  const subtype: string | undefined =
    subtypeValue === undefined || subtypeValue === null || subtypeValue === ""
      ? undefined
      : typeof subtypeValue === "string"
        ? subtypeValue.trim().toUpperCase()
        : "__INVALID__";
  if (subtype === "__INVALID__" || (subtype !== undefined && subtype.length > 80)) {
    return { error: "Invalid vehicle subtype" };
  }

  const capacityKg = finiteNumber(body.capacityKg);
  if (body.capacityKg !== undefined && body.capacityKg !== null && capacityKg === undefined) return { error: "Invalid capacityKg" };
  if (capacityKg !== undefined && (capacityKg < 0 || capacityKg > 1000000)) return { error: "Invalid capacityKg" };

  const volumeM3 = finiteNumber(body.volumeM3);
  if (body.volumeM3 !== undefined && body.volumeM3 !== null && volumeM3 === undefined) return { error: "Invalid volumeM3" };
  if (volumeM3 !== undefined && (volumeM3 < 0 || volumeM3 > 100000)) return { error: "Invalid volumeM3" };

  const refrigerated = body.refrigerated === undefined ? undefined : body.refrigerated;
  if (refrigerated !== undefined && typeof refrigerated !== "boolean") return { error: "Invalid refrigerated" };

  const plateValue = body.plateNumber;
  const plateNumber: string | undefined =
    plateValue === undefined || plateValue === null || plateValue === ""
      ? undefined
      : typeof plateValue === "string"
        ? plateValue.trim().toUpperCase()
        : "__INVALID__";
  if (plateNumber === "__INVALID__" || (plateNumber && (plateNumber.length < 2 || plateNumber.length > 20))) {
    return { error: "Invalid plateNumber" };
  }

  let details: Record<string, string | number> | undefined;
  if (body.details != null) {
    if (typeof body.details !== "object" || Array.isArray(body.details)) return { error: "INVALID_VEHICLE_DETAILS" };
    details = {};
    const source = body.details as Record<string, unknown>;
    for (const field of ["brandModel", "ownership", "city", "destination", "bodyType"] as const) {
      if (source[field] !== undefined) {
        if (typeof source[field] !== "string" || source[field].length > 160) return { error: "INVALID_VEHICLE_DETAILS" };
        details[field] = source[field].trim();
      }
    }
    if (source.year !== undefined) {
      if (typeof source.year !== "number" || !Number.isInteger(source.year) || source.year < 1950 || source.year > new Date().getFullYear() + 1) return { error: "INVALID_VEHICLE_YEAR" };
      details.year = source.year;
    }
  }
  return { type, subtype, capacityKg, volumeM3, refrigerated, plateNumber, details };
}

export async function vehicleRoutes(app: FastifyInstance) {
  app.get("/v1/vehicles", { preHandler: requireRole("DRIVER", "SERVICE_PROVIDER") }, async (request) => {
    const vehicles = await prisma.vehicle.findMany({
      where: { ownerId: request.user!.id },
      orderBy: { createdAt: "desc" },
    });
    return { vehicles: vehicles.map((vehicle) => serializeVehicle(vehicle as unknown as Record<string, unknown>)) };
  });

  app.post("/v1/vehicles", { preHandler: requireRole("DRIVER", "SERVICE_PROVIDER") }, async (request, reply) => {
    const validation = validateVehicleBody((request.body ?? {}) as Record<string, unknown>);
    if ("error" in validation) return reply.code(400).send(validation);

    try {
      const vehicle = await prisma.vehicle.create({
        data: {
          ownerId: request.user!.id,
          type: validation.type,
          subtype: validation.subtype,
          details: validation.details,
          plateNumber: validation.plateNumber,
          capacityKg: validation.capacityKg,
          volumeM3: validation.volumeM3,
          refrigerated: validation.refrigerated ?? false,
        },
      });
      return reply.code(201).send({ vehicle: serializeVehicle(vehicle as unknown as Record<string, unknown>) });
    } catch (error) {
      if ((error as { code?: string }).code === "P2002") {
        return reply.code(409).send({ error: "Vehicle plate number already exists" });
      }
      throw error;
    }
  });

  app.patch("/v1/vehicles/:id", { preHandler: requireRole("DRIVER", "SERVICE_PROVIDER") }, async (request, reply) => {
    const id = (request.params as { id?: string }).id;
    if (!id) return reply.code(400).send({ error: "Vehicle id is required" });

    const existing = await prisma.vehicle.findFirst({ where: { id, ownerId: request.user!.id } });
    if (!existing) return reply.code(404).send({ error: "Vehicle not found" });

    const body = (request.body ?? {}) as Record<string, unknown>;
    const merged = { ...existing, ...body };
    const validation = validateVehicleBody(merged);
    if ("error" in validation) return reply.code(400).send(validation);

    try {
      const vehicle = await prisma.vehicle.update({
        where: { id },
        data: {
          type: validation.type,
          subtype: validation.subtype,
          details: validation.details,
          plateNumber: validation.plateNumber,
          capacityKg: validation.capacityKg,
          volumeM3: validation.volumeM3,
          refrigerated: validation.refrigerated ?? existing.refrigerated,
          ...(typeof body.active === "boolean" ? { active: body.active } : {}),
        },
      });
      return { vehicle: serializeVehicle(vehicle as unknown as Record<string, unknown>) };
    } catch (error) {
      if ((error as { code?: string }).code === "P2002") {
        return reply.code(409).send({ error: "Vehicle plate number already exists" });
      }
      throw error;
    }
  });

  app.delete("/v1/vehicles/:id", { preHandler: requireRole("DRIVER", "SERVICE_PROVIDER") }, async (request, reply) => {
    const id = (request.params as { id?: string }).id;
    if (!id) return reply.code(400).send({ error: "Vehicle id is required" });
    const result = await prisma.vehicle.updateMany({
      where: { id, ownerId: request.user!.id },
      data: { active: false },
    });
    if (result.count === 0) return reply.code(404).send({ error: "Vehicle not found" });
    return reply.code(204).send();
  });
}
