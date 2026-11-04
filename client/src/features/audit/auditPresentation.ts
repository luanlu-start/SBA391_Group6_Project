import type { AuditJsonValue, AuditLog } from '../../types/audit';

const dateFormat = new Intl.DateTimeFormat('vi-VN', {
  day: '2-digit', month: '2-digit', year: 'numeric', hour: '2-digit', minute: '2-digit', second: '2-digit',
  hourCycle: 'h23', timeZone: 'Asia/Ho_Chi_Minh',
});

export function formatAuditDate(value: string) {
  const date = new Date(value);
  return Number.isFinite(date.getTime()) ? dateFormat.format(date) : 'Không có thời điểm';
}

/** The filter always means Vietnam time, independent of the browser's operating-system time zone. */
export function vietnamDateTimeToIso(value: string): string | undefined {
  if (!value) return undefined;
  if (!/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}(?::\d{2})?$/.test(value)) return undefined;
  const complete = value.length === 16 ? `${value}:00` : value;
  const date = new Date(`${complete}+07:00`);
  if (!Number.isFinite(date.getTime())) return undefined;
  // Reject normalized dates such as February 30, rather than querying another calendar day.
  const local = new Date(date.getTime() + 7 * 60 * 60 * 1000).toISOString().slice(0, 19);
  return local === complete ? date.toISOString() : undefined;
}

export function formatAuditJson(value: AuditJsonValue | Record<string, AuditJsonValue>) {
  return JSON.stringify(value, null, 2);
}

function comparable(value: AuditJsonValue): string {
  if (value === null || typeof value !== 'object') return JSON.stringify(value);
  if (Array.isArray(value)) return `[${value.map(comparable).join(',')}]`;
  return `{${Object.keys(value).sort().map(key => `${JSON.stringify(key)}:${comparable(value[key])}`).join(',')}}`;
}

export interface AuditChange {
  field: string;
  before?: AuditJsonValue;
  after?: AuditJsonValue;
}

export function changedAuditFields(event: AuditLog): AuditChange[] {
  const before = event.before ?? {};
  const after = event.after ?? {};
  const owns = (value: Record<string, AuditJsonValue>, field: string) => Object.prototype.hasOwnProperty.call(value, field);
  return [...new Set([...Object.keys(before), ...Object.keys(after)])].sort()
    .filter(field => !owns(before, field) || !owns(after, field) || comparable(before[field]) !== comparable(after[field]))
    .map(field => ({ field, before: owns(before, field) ? before[field] : undefined,
      after: owns(after, field) ? after[field] : undefined }));
}
