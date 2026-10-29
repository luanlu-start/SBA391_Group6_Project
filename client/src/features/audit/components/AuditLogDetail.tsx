import type { AuditLog } from '../../../types/audit';
import { changedAuditFields, formatAuditDate, formatAuditJson } from '../auditPresentation';
import type { AuditChange } from '../auditPresentation';
import Alert from '../../../components/ui/Alert';
import DataTable from '../../../components/ui/DataTable';
import type { TableColumn } from '../../../components/ui/DataTable';

export default function AuditLogDetail({ event }: { event: AuditLog }) {
  const changes = changedAuditFields(event);
  const columns: TableColumn<AuditChange>[] = [
    { key: 'field', header: 'Trường thay đổi', render: row => <code className="audit-token">{row.field}</code> },
    { key: 'before', header: 'Trước', render: row => row.before === undefined ? <span className="audit-muted">Không có</span>
      : <pre className="audit-value">{formatAuditJson(row.before)}</pre> },
    { key: 'after', header: 'Sau', render: row => row.after === undefined ? <span className="audit-muted">Không có</span>
      : <pre className="audit-value">{formatAuditJson(row.after)}</pre> },
  ];
  const metadata: [string, string][] = [
    ['Mã sự kiện', event.eventId], ['Thời điểm (giờ Việt Nam)', formatAuditDate(event.at)],
    ['Thời điểm gốc (ISO)', event.at], ['Loại người thực hiện', event.actorType],
    ['Người thực hiện (UUID)', event.actorPublicId ?? 'Không ghi nhận'], ['Thao tác', event.action],
    ['Loại đối tượng', event.entityType], ['Mã đối tượng', event.entityId],
    ['Địa chỉ IP', event.ip ?? 'Không ghi nhận'], ['Mã truy vết', event.correlationId ?? 'Không ghi nhận'],
  ];

  return <div className="ui-stack">
    <dl className="ui-summary-list audit-metadata">
      {metadata.map(([label, value]) => <div key={label}><dt>{label}</dt><dd>{value}</dd></div>)}
    </dl>
    <section className="audit-detail-section" aria-labelledby="audit-changes-title">
      <h3 id="audit-changes-title">Các trường thay đổi</h3>
      {changes.length ? <DataTable caption="So sánh dữ liệu trước và sau" columns={columns}
        rows={changes} rowKey={row => row.field} />
        : <Alert tone="info">Sự kiện không có khác biệt trong dữ liệu trước và sau được ghi nhận.</Alert>}
    </section>
    <details className="audit-raw">
      <summary>Dữ liệu trước và sau (JSON)</summary>
      <div className="audit-json-grid">
        <div><h3>Trước</h3><pre tabIndex={0} aria-label="Dữ liệu trước dạng JSON">{formatAuditJson(event.before ?? {})}</pre></div>
        <div><h3>Sau</h3><pre tabIndex={0} aria-label="Dữ liệu sau dạng JSON">{formatAuditJson(event.after ?? {})}</pre></div>
      </div>
    </details>
  </div>;
}
