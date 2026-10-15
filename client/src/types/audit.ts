export type AuditJsonValue = string | number | boolean | null | AuditJsonValue[] | { [key: string]: AuditJsonValue };

export interface AuditLog {
  eventId: string;
  actorType: string;
  actorPublicId: string | null;
  action: string;
  entityType: string;
  entityId: string;
  before: Record<string, AuditJsonValue>;
  after: Record<string, AuditJsonValue>;
  ip: string | null;
  correlationId: string | null;
  at: string;
}

export interface AuditQuery {
  actor?: string;
  action?: string;
  entityType?: string;
  entityId?: string;
  from?: string;
  to?: string;
  page: number;
  size: number;
}
