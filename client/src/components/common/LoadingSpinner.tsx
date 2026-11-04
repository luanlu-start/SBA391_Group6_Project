import { Loader2 } from 'lucide-react';

export default function LoadingSpinner({ text = 'Đang tải…' }: { text?: string }) {
  return (
    <div className="loading-container" role="status">
      <Loader2 className="spinner-icon" size={28} aria-hidden="true" />
      {text && <p className="loading-text">{text}</p>}
    </div>
  );
}
