import type { FastifyInstance } from "fastify";
import { prisma } from "../../lib/prisma";
import { requireAuth } from "../auth/guard";
import { publicPerson, publicSelect, objectValue } from "./profile";

export async function directoryRoutes(app: FastifyInstance) {
  app.get("/v1/directory/providers", { preHandler: requireAuth }, async () => {
    const people = await prisma.user.findMany({ where: { role: { in: ["DRIVER", "SERVICE_PROVIDER"] }, status: "ACTIVE" }, select: publicSelect, orderBy: { createdAt: "desc" }, take: 100 });
    return { providers: people.map(publicPerson) };
  });
  app.get<{ Params: { id: string } }>("/v1/directory/providers/:id", { preHandler: requireAuth }, async (req, reply) => {
    const person = await prisma.user.findFirst({ where: { id: req.params.id, status: "ACTIVE" }, select: publicSelect });
    if (!person) return reply.code(404).send({ error: "USER_NOT_FOUND" });
    return { person: publicPerson(person) };
  });
  app.get("/v1/directory/vehicles", { preHandler: requireAuth }, async () => {
    const vehicles = await prisma.vehicle.findMany({
      where: { active: true, owner: { status: "ACTIVE", role: { in: ["DRIVER", "SERVICE_PROVIDER"] }, assignedOrders: { none: { status: { in: ["DRIVER_ASSIGNED", "EN_ROUTE_PICKUP", "ARRIVED_PICKUP", "LOADED", "IN_TRANSIT", "ARRIVED_DELIVERY", "DELIVERED"] } } }, OR: [{ driverProfile: { is: { isOnline: true, isAvailable: true } } }, { serviceProvider: { is: { isOnline: true, isAvailable: true } } }] } },
      select: { id: true, type: true, subtype: true, capacityKg: true, volumeM3: true, refrigerated: true, details: true, owner: { select: publicSelect } }, take: 100, orderBy: { updatedAt: "desc" },
    });
    return { vehicles: vehicles.map(v => ({ ...v, capacityKg: v.capacityKg?.toString() ?? null, volumeM3: v.volumeM3?.toString() ?? null, owner: publicPerson(v.owner) })) };
  });
  app.get("/v1/contacts", { preHandler: requireAuth }, async (req) => {
    const contacts = await prisma.contact.findMany({ where: { ownerId: req.user!.id, contact: { status: "ACTIVE" } }, include: { contact: { select: publicSelect } }, orderBy: { updatedAt: "desc" }, take: 200 });
    return { contacts: contacts.map(c => ({ id: c.id, contactId: c.contactId, note: c.note, person: publicPerson(c.contact) })) };
  });
  app.put<{ Params: { id: string }; Body: { note?: string } }>("/v1/contacts/:id", { preHandler: requireAuth }, async (req, reply) => {
    const body = objectValue(req.body);
    if (body.note !== undefined && (typeof body.note !== "string" || body.note.length > 500)) return reply.code(400).send({ error: "INVALID_NOTE" });
    if (req.params.id === req.user!.id) return reply.code(400).send({ error: "CANNOT_SAVE_SELF" });
    const person = await prisma.user.findFirst({ where: { id: req.params.id, status: "ACTIVE" }, select: { id: true } });
    if (!person) return reply.code(404).send({ error: "USER_NOT_FOUND" });
    const note = typeof body.note === "string" ? body.note.trim() : undefined;
    const contact = await prisma.contact.upsert({ where: { ownerId_contactId: { ownerId: req.user!.id, contactId: req.params.id } }, create: { ownerId: req.user!.id, contactId: req.params.id, note: note ?? "" }, update: note === undefined ? {} : { note } });
    return { contact: { id: contact.id, contactId: contact.contactId, note: contact.note } };
  });
  app.delete<{ Params: { id: string } }>("/v1/contacts/:id", { preHandler: requireAuth }, async (req, reply) => {
    await prisma.contact.deleteMany({ where: { ownerId: req.user!.id, contactId: req.params.id } });
    return reply.code(204).send();
  });
}
