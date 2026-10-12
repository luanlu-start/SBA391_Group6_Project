export default function StatusBadge({ status }: { status?: string }) {
  const isHealthy = status === 'UP' || status === 'ACTIVE' || status === 'SUCCESS';
  const isPending = status === 'PENDING' || status === 'CONNECTING';
  
  let badgeClass = 'badge-default';
  if (isHealthy) badgeClass = 'badge-success';
  else if (isPending) badgeClass = 'badge-warning';
  else if (status === 'DOWN' || status === 'INACTIVE' || status === 'ERROR') badgeClass = 'badge-danger';

  return (
    <span className={`status-badge ${badgeClass}`}>
      <span className="status-dot"></span>
      {status || 'Unknown'}
    </span>
  );
}
