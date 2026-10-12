import { useState } from 'react';
import Alert from './ui/Alert';
import Button from './ui/Button';
import '../styles/api-error.css';

interface Props {
  message: string;
  correlationId?: string;
}

export default function ApiErrorNotice({ message, correlationId }: Props) {
  const [copyStatus, setCopyStatus] = useState('');

  async function copyId() {
    if (!correlationId) return;
    try {
      await navigator.clipboard.writeText(correlationId);
      setCopyStatus('Đã sao chép');
    } catch {
      setCopyStatus('Anh/chị có thể chọn và sao chép mã bên dưới.');
    }
  }

  return (
    <Alert tone="danger">
      <p>{message}</p>
      {correlationId && <div className="api-error-reference">
        <span>Mã theo dõi: <code>{correlationId}</code></span>
        <Button variant="ghost" size="sm" onClick={copyId}>Sao chép mã theo dõi</Button>
        {copyStatus && <span role="status">{copyStatus}</span>}
      </div>}
    </Alert>
  );
}
