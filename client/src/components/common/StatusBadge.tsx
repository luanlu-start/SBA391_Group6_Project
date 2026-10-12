import Badge from '../ui/Badge';

export default function StatusBadge({ status }: { status?: string }) {
  const isHealthy = status === 'UP' || status === 'ACTIVE' || status === 'SUCCESS';
  const isPending = status === 'PENDING' || status === 'CONNECTING';
  
  const tone = isHealthy ? 'success' : isPending ? 'warning'
    : status === 'DOWN' || status === 'INACTIVE' || status === 'ERROR' ? 'danger' : 'neutral';
  return <Badge tone={tone} dot>{status || 'Chưa xác định'}</Badge>;
}
