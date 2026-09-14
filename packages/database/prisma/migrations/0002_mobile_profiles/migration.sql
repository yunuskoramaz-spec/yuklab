ALTER TABLE "User" ADD COLUMN "profile" JSONB;
ALTER TABLE "Vehicle" ADD COLUMN "details" JSONB;
CREATE TABLE "Contact" (
  "id" TEXT NOT NULL,
  "ownerId" TEXT NOT NULL,
  "contactId" TEXT NOT NULL,
  "note" TEXT NOT NULL DEFAULT '',
  "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
  "updatedAt" TIMESTAMP(3) NOT NULL,
  CONSTRAINT "Contact_pkey" PRIMARY KEY ("id"),
  CONSTRAINT "Contact_ownerId_fkey" FOREIGN KEY ("ownerId") REFERENCES "User"("id") ON DELETE CASCADE ON UPDATE CASCADE,
  CONSTRAINT "Contact_contactId_fkey" FOREIGN KEY ("contactId") REFERENCES "User"("id") ON DELETE CASCADE ON UPDATE CASCADE
);
CREATE UNIQUE INDEX "Contact_ownerId_contactId_key" ON "Contact"("ownerId", "contactId");
CREATE INDEX "Contact_ownerId_updatedAt_idx" ON "Contact"("ownerId", "updatedAt");
