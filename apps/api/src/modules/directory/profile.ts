/** Public profile data is explicitly allowlisted; no email, documents or auth fields. */
export function objectValue(value: unknown): Record<string, unknown> {
  return value !== null && typeof value === "object" && !Array.isArray(value) ? value as Record<string, unknown> : {};
}
export function cleanProfile(value: unknown) {
  const input = objectValue(value);
  const result: Record<string, string | boolean> = {};
  for (const [key, max] of Object.entries({ companyName: 160, city: 80, district: 80, bio: 1000, services: 500, contactPhone: 32 })) {
    if (input[key] !== undefined) {
      if (typeof input[key] !== "string" || input[key].length > max) throw new Error("INVALID_PROFILE");
      result[key] = input[key].trim();
    }
  }
  if (input.publicPhone !== undefined) {
    if (typeof input.publicPhone !== "boolean") throw new Error("INVALID_PROFILE");
    result.publicPhone = input.publicPhone;
  }
  return result;
}
export const publicSelect = {
  id: true, firstName: true, lastName: true, role: true, createdAt: true, profile: true,
  driverProfile: { select: { rating: true, completedJobs: true, isOnline: true, isAvailable: true } },
  serviceProvider: { select: { rating: true, completedJobs: true, isOnline: true, isAvailable: true } },
} as const;
export function publicPerson(user: { id: string; firstName: string; lastName: string; role: string; createdAt: Date; profile: unknown; driverProfile?: unknown; serviceProvider?: unknown }) {
  const profile = objectValue(user.profile);
  const provider = objectValue(user.driverProfile ?? user.serviceProvider);
  return {
    id: user.id, firstName: user.firstName, lastName: user.lastName, role: user.role, createdAt: user.createdAt,
    companyName: typeof profile.companyName === "string" ? profile.companyName : "",
    city: typeof profile.city === "string" ? profile.city : "",
    district: typeof profile.district === "string" ? profile.district : "",
    bio: typeof profile.bio === "string" ? profile.bio : "",
    services: typeof profile.services === "string" ? profile.services : "",
    contactPhone: profile.publicPhone === true && typeof profile.contactPhone === "string" ? profile.contactPhone : null,
    rating: Number(provider.rating ?? 0), completedJobs: Number(provider.completedJobs ?? 0),
    isOnline: provider.isOnline === true, isAvailable: provider.isAvailable === true,
  };
}
