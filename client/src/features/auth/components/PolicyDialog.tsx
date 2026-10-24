import type { RegistrationPolicy } from '../../../types/identity';
import Modal from '../../../components/ui/Modal';
import Button from '../../../components/ui/Button';

export default function PolicyDialog({ policy, onClose }: {
  policy: RegistrationPolicy | null;
  onClose: () => void;
}) {
  return <Modal isOpen={!!policy} onClose={onClose} title={policy?.title} size="lg">
    <div className="ui-stack">
      <div className="policy-content">{policy?.paragraphs.map((paragraph, index) => <p key={index}>{paragraph}</p>)}</div>
      <div className="ui-form-actions"><Button onClick={onClose}>Đóng</Button></div>
    </div>
  </Modal>;
}
