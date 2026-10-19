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

export type AuditFilterField = 'actor' | 'action' | 'entityType' | 'entityId';

export interface AuditFilterOption {
  value: string;
  label: string;
  description?: string;
}

export interface AuditFilterOptions {
  options: AuditFilterOption[];
  hasMore: boolean;
}

export interface AuditFilterOptionsQuery {
  field: AuditFilterField;
  q?: string;
  entityType?: string;
  limit?: number;
}
