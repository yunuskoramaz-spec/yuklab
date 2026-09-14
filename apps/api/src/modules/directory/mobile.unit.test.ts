import { beforeEach, describe, expect, it, vi } from "vitest";
import Fastify, { type FastifyRequest, type FastifyReply } from "fastify";
import { cleanProfile, publicPerson } from "./profile";
import { directoryRoutes } from "./routes";
import { userRoutes } from "../../routes/users";
import { vehicleRoutes } from "../vehicles/routes";
const db = vi.hoisted(() => ({ user: { findMany: vi.fn(), findFirst: vi.fn(), findUniqueOrThrow: vi.fn(), update: vi.fn(), count: vi.fn() }, contact: { findMany: vi.fn(), upsert: vi.fn(), deleteMany: vi.fn() }, vehicle: { findMany: vi.fn(), findFirst: vi.fn(), create: vi.fn(), update: vi.fn() } }));
vi.mock("../../lib/prisma", () => ({ prisma: db }));
vi.mock("../auth/guard", () => {
  const guard = async (req: FastifyRequest, reply: FastifyReply) => {
    if (!req.headers.authorization) { await reply.code(401).send({ error: "UNAUTHORIZED" }); return; }
    req.user = { id: "alice", role: "DRIVER" };
  };
  return { requireAuth: guard, requireRole: () => guard };
});
const person = { id: "bob", firstName: "Test", lastName: "Provider", role: "DRIVER", createdAt: new Date(), profile: { companyName: "Test", contactPhone: "5551234567", publicPhone: false }, driverProfile: null };
const headers = { authorization: "test-fixture" };
async function app() { const a = Fastify(); a.register(directoryRoutes); a.register(userRoutes); a.register(vehicleRoutes); await a.ready(); return a; }
beforeEach(() => vi.resetAllMocks());
describe("mobile profile and portfolio contracts with isolated persistence", () => {
  it("hides phone until explicit opt-in and rejects malformed profile fields", () => {
    expect(publicPerson(person).contactPhone).toBeNull();
    expect(publicPerson({ ...person, profile: { ...person.profile, publicPhone: true } }).contactPhone).toBe("5551234567");
    expect(publicPerson({ ...person, profile: { ...person.profile, publicPhone: "true" } }).contactPhone).toBeNull();
    expect(cleanProfile({ companyName: " A ", role: "SUPER_ADMIN", passwordHash: "secret" })).toEqual({ companyName: "A" });
    expect(() => cleanProfile({ publicPhone: "true" })).toThrow(); expect(() => cleanProfile({ bio: "x".repeat(1001) })).toThrow();
  });
  it("requires auth across profile, directory and portfolio", async () => {
    const a=await app(); for(const url of ["/v1/users/me","/v1/contacts","/v1/directory/providers","/v1/directory/vehicles"]) expect((await a.inject({url})).statusCode).toBe(401); expect(db.user.findMany).not.toHaveBeenCalled(); await a.close();
  });
  it("profile updates cannot elevate a role or alter ownership", async () => {
    db.user.findUniqueOrThrow.mockResolvedValue({profile:{}}); db.user.update.mockResolvedValue({id:"alice",role:"DRIVER"}); const a=await app();
    expect((await a.inject({method:"PATCH",url:"/v1/users/me",headers,payload:{id:"bob",role:"SUPER_ADMIN",firstName:" Alice ",profile:{companyName:"A"}}})).statusCode).toBe(200);
    expect(db.user.update.mock.calls[0][0]).toMatchObject({where:{id:"alice"},data:{firstName:"Alice",profile:{companyName:"A"}}}); expect(db.user.update.mock.calls[0][0].data).not.toHaveProperty("role"); await a.close();
  });
  it("scopes private notes to their author and preserves a note when resaving a contact", async () => {
    db.user.findFirst.mockResolvedValue({id:"bob"}); db.contact.upsert.mockResolvedValue({id:"c",contactId:"bob",note:"Private"}); const a=await app();
    expect((await a.inject({method:"PUT",url:"/v1/contacts/bob",headers,payload:{note:"Private",ownerId:"mallory"}})).statusCode).toBe(200);
    expect(db.contact.upsert.mock.calls[0][0].where).toEqual({ownerId_contactId:{ownerId:"alice",contactId:"bob"}});
    await a.inject({method:"PUT",url:"/v1/contacts/bob",headers,payload:{}}); expect(db.contact.upsert.mock.calls[1][0].update).toEqual({}); await a.close();
  });
  it("rejects oversized notes and self-contact entries", async () => {
    const a=await app(); expect((await a.inject({method:"PUT",url:"/v1/contacts/bob",headers,payload:{note:"x".repeat(501)}})).statusCode).toBe(400); expect((await a.inject({method:"PUT",url:"/v1/contacts/alice",headers,payload:{}})).statusCode).toBe(400); expect(db.contact.upsert).not.toHaveBeenCalled(); await a.close();
  });
  it("reads and removes only the current owner's contacts", async () => {
    db.contact.findMany.mockResolvedValue([{id:"c",contactId:"bob",note:"private",contact:person}]); db.contact.deleteMany.mockResolvedValue({count:1}); const a=await app();
    const r=await a.inject({url:"/v1/contacts",headers}); expect(r.json().contacts[0].person.contactPhone).toBeNull(); expect(db.contact.findMany.mock.calls[0][0].where.ownerId).toBe("alice");
    expect((await a.inject({method:"DELETE",url:"/v1/contacts/bob",headers})).statusCode).toBe(204); expect(db.contact.deleteMany).toHaveBeenCalledWith({where:{ownerId:"alice",contactId:"bob"}}); await a.close();
  });
  it("excludes assigned vehicles from the empty-vehicle directory", async () => {
    db.vehicle.findMany.mockResolvedValue([]); const a=await app(); await a.inject({url:"/v1/directory/vehicles",headers}); expect(db.vehicle.findMany.mock.calls[0][0].where.owner.assignedOrders.none.status.in).toContain("IN_TRANSIT"); await a.close();
  });
  it("allows lowbed details without accepting unrecognized fields or invalid years", async () => {
    db.vehicle.create.mockResolvedValue({id:"v",capacityKg:null,volumeM3:null}); const a=await app();
    expect((await a.inject({method:"POST",url:"/v1/vehicles",headers,payload:{type:"LOWBED",details:{brandModel:"Test",year:2022,ownerId:"mallory"}}})).statusCode).toBe(201);
    expect(db.vehicle.create.mock.calls[0][0].data.details).toEqual({brandModel:"Test",year:2022}); expect((await a.inject({method:"POST",url:"/v1/vehicles",headers,payload:{type:"LOWBED",details:{year:1000}}})).statusCode).toBe(400); await a.close();
  });
});
