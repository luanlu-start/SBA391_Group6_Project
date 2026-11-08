import { Link, useParams } from 'react-router-dom';
import { ArrowLeft } from 'lucide-react';
import ComplaintCase from '../features/complaints/components/ComplaintCase';

export default function ComplaintDetailPage() {
  const { id = '' } = useParams();
  return <div className="ui-stack">
    <Link to="/me/complaints" className="complaint-back"><ArrowLeft size={16} aria-hidden="true" /> Khiếu nại của tôi</Link>
    <ComplaintCase key={id} id={id} staffView={false} />
  </div>;
}
