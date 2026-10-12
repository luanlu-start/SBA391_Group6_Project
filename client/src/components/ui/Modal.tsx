import type { ReactNode } from 'react';
import { useEffect, useId, useRef } from 'react';
import { X } from 'lucide-react';
import Button from './Button';

interface ModalProps {
  isOpen: boolean;
  onClose: () => void;
  title?: string;
  children?: ReactNode;
  description?: string;
  closeDisabled?: boolean;
  size?: 'md' | 'lg';
}

export default function Modal({ isOpen, onClose, title = 'Thông tin', description, children,
  closeDisabled = false, size = 'md' }: ModalProps) {
  const dialogRef = useRef<HTMLDialogElement>(null);
  const titleId = useId();
  const descriptionId = useId();
  useEffect(() => {
    const dialog = dialogRef.current;
    if (!dialog) return;
    if (isOpen && !dialog.open) dialog.showModal();
    if (!isOpen && dialog.open) dialog.close();
  }, [isOpen]);
  useEffect(() => {
    if (!isOpen) return;
    const previousOverflow = document.body.style.overflow;
    document.body.style.overflow = 'hidden';
    return () => { document.body.style.overflow = previousOverflow; };
  }, [isOpen]);

  return (
    <dialog ref={dialogRef} className={`ui-modal ui-modal-${size}`} aria-labelledby={titleId}
      aria-describedby={description ? descriptionId : undefined}
      onCancel={event => { event.preventDefault(); if (!closeDisabled) onClose(); }}
      onClick={event => { if (event.target === event.currentTarget && !closeDisabled) onClose(); }}>
      {isOpen && <div className="ui-modal-content">
        <header className="ui-modal-header"><div><h2 id={titleId}>{title}</h2>
          {description && <p id={descriptionId}>{description}</p>}</div>
          <Button variant="ghost" size="sm" aria-label="Đóng hộp thoại" disabled={closeDisabled} onClick={onClose}
            icon={<X size={19} />} />
        </header>
        <div className="ui-modal-body">{children}</div>
      </div>}
    </dialog>
  );
}
