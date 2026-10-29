import { z } from 'zod';
import type { AuditQuery } from '../../../types/audit';
import { vietnamDateTimeToIso } from '../auditPresentation';

const uuid = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;
const datetime = z.string().refine(value => !value || !!vietnamDateTimeToIso(value), 'Nhập thời điểm hợp lệ theo giờ Việt Nam.');

export const auditFilterSchema = z.object({
  actor: z.string().trim().refine(value => !value || uuid.test(value), 'Chọn người thực hiện hợp lệ trong danh sách.'),
  action: z.string().trim().max(100, 'Thao tác tối đa 100 ký tự.'),
  entityType: z.string().trim().max(100, 'Loại đối tượng tối đa 100 ký tự.'),
  entityId: z.string().trim().max(200, 'Mã đối tượng tối đa 200 ký tự.'),
  from: datetime,
  to: datetime,
  size: z.enum(['20', '50', '100']),
}).superRefine((values, context) => {
  const from = vietnamDateTimeToIso(values.from);
  const to = vietnamDateTimeToIso(values.to);
  if (from && to && from > to) context.addIssue({
    code: 'custom', path: ['to'], message: 'Thời điểm đến phải bằng hoặc sau thời điểm từ.',
  });
});

export type AuditFilterValues = z.infer<typeof auditFilterSchema>;

export const emptyAuditFilters: AuditFilterValues = {
  actor: '', action: '', entityType: '', entityId: '', from: '', to: '', size: '20',
};

export function auditFiltersToQuery(values: AuditFilterValues): AuditQuery {
  return {
    actor: values.actor.trim().toLowerCase() || undefined,
    action: values.action.trim() || undefined,
    entityType: values.entityType.trim() || undefined,
    entityId: values.entityId.trim() || undefined,
    from: vietnamDateTimeToIso(values.from),
    to: vietnamDateTimeToIso(values.to),
    page: 0,
    size: Number(values.size),
  };
}
