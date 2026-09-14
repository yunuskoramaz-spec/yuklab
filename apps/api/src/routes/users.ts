import type { FastifyInstance } from "fastify";
import type { Prisma } from "@yuklab/database";
import { prisma } from "../lib/prisma";
import { requireAuth } from "../modules/auth/guard";
import { hashPassword, verifyPassword } from "../modules/auth/service";

const MAX_NAME = 100;
const MAX_EMAIL = 254;
const MAX_PHONE = 32;
const MAX_LANG = 16;
const MAX_URL = 2048;
const MAX_COMPANY = 180;
const MAX_ADDRESS = 500;
const MAX_REGIONS = 25;

function optionalString(value: unknown, maxLength: number): string | null | undefined {
  if (value === undefined) return undefined;
  if (value === null) return null;
  if (typeof value !== "string") return undefined;
  const normalized = value.trim();
  if (!normalized) return null;
  return normalized.length <= maxLength ? normalized : undefined;
}

const profileInclude: Prisma.UserInclude = {
  companyProfile: true,
  preferences: true,
  driverProfile: true,
  serviceProvider: true,
  workRegions: { orderBy: [{ country: "asc" }, { city: "asc" }] },
};

function publicProfile(user: {
  id: string;
  email: string | null;
  phone: string | null;
  firstName: string;
  lastName: string;
  avatarUrl: string | null;
  preferredLanguage: string;
  role: string;
  status: string;
  createdAt: Date;
  updatedAt: Date;
  companyProfile?: unknown;
  preferences?: unknown;
  driverProfile?: unknown;
  serviceProvider?: unknown;
  workRegions?: unknown;
}) {
  return {
    id: user.id,
    email: user.email,
    phone: user.phone,
    firstName: user.firstName,
    lastName: user.lastName,
    avatarUrl: user.avatarUrl,
    preferredLanguage: user.preferredLanguage,
    role: user.role,
    status: user.status,
    createdAt: user.createdAt,
    updatedAt: user.updatedAt,
    companyProfile: user.companyProfile ?? null,
    preferences: user.preferences ?? null,
    driverProfile: user.driverProfile ?? null,
    serviceProvider: user.serviceProvider ?? null,
    workRegions: user.workRegions ?? [],
  };
}

export async function userRoutes(app: FastifyInstance) {
  app.get("/v1/users/me", { preHandler: requireAuth }, async (request, reply) => {
    const user = await prisma.user.findUnique({ where: { id: request.user!.id }, include: profileInclude });
    if (!user || user.status !== "ACTIVE") return reply.code(404).send({ error: "USER_NOT_FOUND" });
    return { user: publicProfile(user) };
  });

  app.patch<{
    Body: {
      firstName?: string;
      lastName?: string;
      email?: string | null;
      phone?: string | null;
      avatarUrl?: string | null;
      preferredLanguage?: string;
      company?: { companyName?: string; taxNumber?: string | null; website?: string | null; address?: string | null; city?: string | null; district?: string | null } | null;
      preferences?: { theme?: "system" | "light" | "dark"; locale?: string; pushEnabled?: boolean; marketingEnabled?: boolean };
    };
  }>("/v1/users/me", { preHandler: requireAuth }, async (request, reply) => {
    const body = request.body ?? {};
    const data: Prisma.UserUpdateInput = {};

    if (body.firstName !== undefined) {
      const value = optionalString(body.firstName, MAX_NAME);
      if (!value) return reply.code(400).send({ error: "INVALID_FIRST_NAME" });
      data.firstName = value;
    }
    if (body.lastName !== undefined) {
      const value = optionalString(body.lastName, MAX_NAME);
      if (!value) return reply.code(400).send({ error: "INVALID_LAST_NAME" });
      data.lastName = value;
    }
    if (body.email !== undefined) {
      const value = optionalString(body.email, MAX_EMAIL);
      if (value === undefined || (value !== null && !value.includes("@"))) return reply.code(400).send({ error: "INVALID_EMAIL" });
      data.email = value?.toLowerCase() ?? null;
    }
    if (body.phone !== undefined) {
      const value = optionalString(body.phone, MAX_PHONE);
      if (value === undefined) return reply.code(400).send({ error: "INVALID_PHONE" });
      data.phone = value;
    }
    if (body.avatarUrl !== undefined) {
      const value = optionalString(body.avatarUrl, MAX_URL);
      if (value === undefined) return reply.code(400).send({ error: "INVALID_AVATAR_URL" });
      data.avatarUrl = value;
    }
    if (body.preferredLanguage !== undefined) {
      const value = optionalString(body.preferredLanguage, MAX_LANG);
      if (!value) return reply.code(400).send({ error: "INVALID_LANGUAGE" });
      data.preferredLanguage = value;
    }

    const company = body.company;
    if (company !== undefined && company !== null && typeof company !== "object") return reply.code(400).send({ error: "INVALID_COMPANY" });
    if (company) {
      const companyName = optionalString(company.companyName, MAX_COMPANY);
      const existing = await prisma.companyProfile.findUnique({ where: { userId: request.user!.id } });
      if (!existing && !companyName) return reply.code(400).send({ error: "COMPANY_NAME_REQUIRED" });
      if (company.companyName !== undefined && !companyName) return reply.code(400).send({ error: "INVALID_COMPANY_NAME" });
      const optionalFields = {
        taxNumber: optionalString(company.taxNumber, 64),
        website: optionalString(company.website, MAX_URL),
        address: optionalString(company.address, MAX_ADDRESS),
        city: optionalString(company.city, 120),
        district: optionalString(company.district, 120),
      };
      for (const [key, value] of Object.entries(optionalFields)) {
        if (value === undefined && (company as Record<string, unknown>)[key] !== undefined) return reply.code(400).send({ error: `INVALID_${key.toUpperCase()}` });
      }
    }

    const preferences = body.preferences;
    if (preferences) {
      if (preferences.theme !== undefined && !["system", "light", "dark"].includes(preferences.theme)) return reply.code(400).send({ error: "INVALID_THEME" });
      if (preferences.locale !== undefined && !optionalString(preferences.locale, MAX_LANG)) return reply.code(400).send({ error: "INVALID_LOCALE" });
      if (preferences.pushEnabled !== undefined && typeof preferences.pushEnabled !== "boolean") return reply.code(400).send({ error: "INVALID_PUSH_SETTING" });
      if (preferences.marketingEnabled !== undefined && typeof preferences.marketingEnabled !== "boolean") return reply.code(400).send({ error: "INVALID_MARKETING_SETTING" });
    }

    try {
      const user = await prisma.$transaction(async (tx) => {
        if (Object.keys(data).length > 0) await tx.user.update({ where: { id: request.user!.id }, data });

        if (company) {
          const existing = await tx.companyProfile.findUnique({ where: { userId: request.user!.id } });
          const companyName = optionalString(company.companyName, MAX_COMPANY) ?? existing?.companyName;
          if (!companyName) throw new Error("COMPANY_NAME_REQUIRED");
          const companyData = {
            companyName,
            taxNumber: optionalString(company.taxNumber, 64) ?? (company.taxNumber === null ? null : existing?.taxNumber),
            website: optionalString(company.website, MAX_URL) ?? (company.website === null ? null : existing?.website),
            address: optionalString(company.address, MAX_ADDRESS) ?? (company.address === null ? null : existing?.address),
            city: optionalString(company.city, 120) ?? (company.city === null ? null : existing?.city),
            district: optionalString(company.district, 120) ?? (company.district === null ? null : existing?.district),
          };
          await tx.companyProfile.upsert({ where: { userId: request.user!.id }, create: { userId: request.user!.id, ...companyData }, update: companyData });
        }

        if (preferences) {
          await tx.userPreference.upsert({
            where: { userId: request.user!.id },
            create: { userId: request.user!.id, theme: preferences.theme ?? "system", locale: preferences.locale ?? "tr-TR", pushEnabled: preferences.pushEnabled ?? true, marketingEnabled: preferences.marketingEnabled ?? false },
            update: {
              ...(preferences.theme !== undefined ? { theme: preferences.theme } : {}),
              ...(preferences.locale !== undefined ? { locale: preferences.locale } : {}),
              ...(preferences.pushEnabled !== undefined ? { pushEnabled: preferences.pushEnabled } : {}),
              ...(preferences.marketingEnabled !== undefined ? { marketingEnabled: preferences.marketingEnabled } : {}),
            },
          });
        }

        return tx.user.findUniqueOrThrow({ where: { id: request.user!.id }, include: profileInclude });
      });
      return { user: publicProfile(user) };
    } catch (error) {
      if ((error as { code?: string }).code === "P2002") return reply.code(409).send({ error: "PROFILE_VALUE_ALREADY_IN_USE" });
      if (error instanceof Error && error.message === "COMPANY_NAME_REQUIRED") return reply.code(400).send({ error: "COMPANY_NAME_REQUIRED" });
      throw error;
    }
  });

  app.put<{ Body: { regions: Array<{ country?: string; city: string; district?: string | null; radiusKm?: number | null }> } }>(
    "/v1/users/me/work-regions",
    { preHandler: requireAuth },
    async (request, reply) => {
      const regions = request.body?.regions;
      if (!Array.isArray(regions) || regions.length > MAX_REGIONS) return reply.code(400).send({ error: "INVALID_REGIONS" });
      const normalized: Array<{ country: string; city: string; district?: string; radiusKm?: number }> = [];
      for (const region of regions) {
        const country = (optionalString(region.country ?? "TR", 2) ?? "TR").toUpperCase();
        const city = optionalString(region.city, 120);
        const district = optionalString(region.district, 120);
        const radiusKm = region.radiusKm === undefined || region.radiusKm === null ? undefined : Number(region.radiusKm);
        if (country.length !== 2 || !city || (region.district !== undefined && district === undefined) || (radiusKm !== undefined && (!Number.isFinite(radiusKm) || radiusKm < 1 || radiusKm > 1000))) return reply.code(400).send({ error: "INVALID_REGION" });
        normalized.push({ country, city, ...(district ? { district } : {}), ...(radiusKm !== undefined ? { radiusKm } : {}) });
      }
      await prisma.$transaction(async (tx) => {
        await tx.workRegion.deleteMany({ where: { userId: request.user!.id } });
        if (normalized.length) await tx.workRegion.createMany({ data: normalized.map((region) => ({ userId: request.user!.id, ...region })) });
      });
      return { workRegions: await prisma.workRegion.findMany({ where: { userId: request.user!.id }, orderBy: [{ country: "asc" }, { city: "asc" }] }) };
    },
  );

  app.post<{ Body: { currentPassword: string; newPassword: string } }>("/v1/users/me/password", { preHandler: requireAuth }, async (request, reply) => {
    const currentPassword = typeof request.body?.currentPassword === "string" ? request.body.currentPassword : "";
    const newPassword = typeof request.body?.newPassword === "string" ? request.body.newPassword : "";
    if (!currentPassword || newPassword.length < 8 || newPassword.length > 128) return reply.code(400).send({ error: "INVALID_PASSWORD" });
    const user = await prisma.user.findUnique({ where: { id: request.user!.id }, select: { passwordHash: true } });
    if (!user?.passwordHash || !(await verifyPassword(currentPassword, user.passwordHash))) return reply.code(401).send({ error: "INVALID_CURRENT_PASSWORD" });
    await prisma.$transaction([
      prisma.user.update({ where: { id: request.user!.id }, data: { passwordHash: await hashPassword(newPassword) } }),
      prisma.authSession.updateMany({ where: { userId: request.user!.id, revokedAt: null }, data: { revokedAt: new Date() } }),
    ]);
    return reply.code(204).send();
  });

  app.delete("/v1/users/me", { preHandler: requireAuth }, async (request, reply) => {
    const userId = request.user!.id;
    await prisma.$transaction(async (tx) => {
      await tx.authSession.updateMany({ where: { userId, revokedAt: null }, data: { revokedAt: new Date() } });
      await tx.driverProfile.updateMany({ where: { userId }, data: { isOnline: false, isAvailable: false } });
      await tx.serviceProvider.updateMany({ where: { userId }, data: { isOnline: false, isAvailable: false } });
      await tx.vehicle.updateMany({ where: { ownerId: userId }, data: { active: false, plateNumber: null } });
      await tx.document.deleteMany({ where: { userId } });
      await tx.notification.deleteMany({ where: { userId } });
      await tx.workRegion.deleteMany({ where: { userId } });
      await tx.companyProfile.deleteMany({ where: { userId } });
      await tx.userPreference.deleteMany({ where: { userId } });
      await tx.user.update({ where: { id: userId }, data: { email: null, phone: null, passwordHash: null, avatarUrl: null, firstName: "Deleted", lastName: "User", status: "DELETED", role: "CUSTOMER" } });
    });
    return reply.code(204).send();
  });

  app.get("/v1/users/count", async () => ({ count: await prisma.user.count({ where: { status: { not: "DELETED" } } }) }));
}
