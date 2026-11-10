import { useParams } from 'react-router-dom';
import PageHeader from '../components/ui/PageHeader';
import Alert from '../components/ui/Alert';
import DeliveryIncidentPanel from '../components/order/DeliveryIncidentPanel';

/** Notification destination available to both order parties and staff; API checks order membership. */
export default function DeliveryIncidentsPage() {
  const { id } = useParams<{ id: string }>();
  const orderId = Number(id);
  if (!Number.isSafeInteger(orderId) || orderId <= 0) return <Alert tone="danger">Mã đơn hàng không hợp lệ.</Alert>;
  return <div className="ui-stack">
    <PageHeader title="Lịch sử sự cố giao hàng" description={`Đơn hàng #${orderId}`} />
    <DeliveryIncidentPanel orderId={orderId} initialOpen />
  </div>;
}
