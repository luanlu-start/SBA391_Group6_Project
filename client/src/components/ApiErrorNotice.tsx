import { useState } from 'react';
import type { ApiError } from '../api/ApiError';
import Alert from './ui/Alert';
import Button from './ui/Button';
import '../styles/api-error.css';

interface Props {
  error: Pick<ApiError, 'message' | 'status' | 'correlationId'>;
  message?: string;
}

export default function ApiErrorNotice({ error, message }: Props) {
  const showSupportDetails = error.status !== undefined && error.status >= 500 && error.status < 600;

  return (
    <Alert tone="danger">
      <p>{message ?? error.message}</p>
      {showSupportDetails && error.correlationId &&
        <ErrorSupportDetails key={error.correlationId} correlationId={error.correlationId} />}
    </Alert>
  );
}

function ErrorSupportDetails({ correlationId }: { correlationId: string }) {
  const [copyStatus, setCopyStatus] = useState('');

  async function copyId() {
    try {
      await navigator.clipboard.writeText(correlationId);
      setCopyStatus('Đã sao chép');
    } catch {
      setCopyStatus('Anh/chị có thể chọn và sao chép mã bên dưới.');
    }
  }

  return (
    <details className="api-error-support">
      <summary>Chi tiết hỗ trợ</summary>
      <div className="api-error-reference">
        <span>Mã theo dõi: <code>{correlationId}</code></span>
        <Button variant="ghost" size="sm" onClick={copyId}>Sao chép mã theo dõi</Button>
        {copyStatus && <span role="status">{copyStatus}</span>}
      </div>
    </details>
  );
}
