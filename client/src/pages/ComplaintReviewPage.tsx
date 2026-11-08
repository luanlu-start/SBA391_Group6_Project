import { Link, useParams } from 'react-router-dom';
import { ArrowLeft } from 'lucide-react';
import ComplaintCase from '../features/complaints/components/ComplaintCase';

export default function ComplaintReviewPage() {
  const { id = '' } = useParams();
  return <div className="ui-stack">
    <Link to="/mod/complaints" className="complaint-back"><ArrowLeft size={16} aria-hidden="true" /> Hàng đợi khiếu nại</Link>
    <ComplaintCase key={id} id={id} staffView />
  </div>;
}
