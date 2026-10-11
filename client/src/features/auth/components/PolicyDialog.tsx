import { useEffect, useId, useRef } from 'react';
import { X } from 'lucide-react';
import type { RegistrationPolicy } from '../../../types/identity';

export default function PolicyDialog({ policy, onClose }: {
  policy: RegistrationPolicy | null;
  onClose: () => void;
}) {
  const dialog = useRef<HTMLDialogElement>(null);
  const titleId = useId();

  useEffect(() => {
    const modal = dialog.current;
    if (!modal) return;
    if (policy && !modal.open) modal.showModal();
    if (!policy && modal.open) modal.close();
  }, [policy]);

  useEffect(() => {
    if (!policy) return;
    const previousOverflow = document.body.style.overflow;
    document.body.style.overflow = 'hidden';
    return () => { document.body.style.overflow = previousOverflow; };
  }, [policy]);

  return (
    <dialog ref={dialog} className="policy-dialog" aria-labelledby={titleId}
      onCancel={onClose} onClose={onClose}
      onClick={event => { if (event.target === event.currentTarget) onClose(); }}>
      {policy && <div className="policy-dialog-card">
        <header className="policy-dialog-header">
          <h2 id={titleId}>{policy.title}</h2>
          <button className="policy-dialog-close" type="button" aria-label="Đóng chính sách" onClick={onClose}>
            <X size={21} aria-hidden="true" />
          </button>
        </header>
        <div className="policy-dialog-body">
          {policy.paragraphs.map((paragraph, index) => <p key={index}>{paragraph}</p>)}
        </div>
        <footer className="policy-dialog-footer">
          <button className="login-submit" type="button" onClick={onClose}>Đóng</button>
        </footer>
      </div>}
    </dialog>
  );
}
