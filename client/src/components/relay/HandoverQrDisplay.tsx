import { useState, useEffect } from 'react';
import { Copy, Check, RefreshCw, Clock, ShieldCheck, MapPin } from 'lucide-react';
import type { HandoverToken } from '../../types/relay';
import Button from '../ui/Button';

interface Props {
  token: HandoverToken;
  onRefresh: () => void;
  refreshing?: boolean;
}

export default function HandoverQrDisplay({ token, onRefresh, refreshing }: Props) {
  const [copied, setCopied] = useState(false);
  const [timeLeft, setTimeLeft] = useState<number>(() => {
    if (!token.expiresAt) return 600;
    const diff = Math.floor((new Date(token.expiresAt).getTime() - Date.now()) / 1000);
    return Math.max(0, diff);
  });

  useEffect(() => {
    const updateCountdown = () => {
      const diff = Math.floor((new Date(token.expiresAt).getTime() - Date.now()) / 1000);
      setTimeLeft(Math.max(0, diff));
    };

    updateCountdown();
    const timer = setInterval(updateCountdown, 1000);
    return () => clearInterval(timer);
  }, [token.expiresAt]);

  const handleCopy = () => {
    navigator.clipboard.writeText(token.tokenCode);
    setCopied(true);
    setTimeout(() => setCopied(false), 2000);
  };

  const minutes = Math.floor(timeLeft / 60);
  const seconds = timeLeft % 60;
  const isExpiringSoon = timeLeft < 120 && timeLeft > 0;
  const isExpired = timeLeft === 0;

  // Sử dụng QR Server API an toàn để tạo ảnh QR
  const qrUrl = `https://api.qrserver.com/v1/create-qr-code/?size=260x260&margin=10&data=${encodeURIComponent(
    token.qrData || token.tokenCode
  )}`;

  return (
    <div className="handover-qr-card">
      <div className="handover-qr-badge">
        <ShieldCheck size={16} />
        <span>Bàn giao an toàn tại điểm hẹn</span>
      </div>

      <div className="handover-qr-frame">
        <div className={`qr-image-wrapper ${isExpired ? 'expired' : ''}`}>
          <img src={qrUrl} alt="Mã QR Bàn Giao Chặng" className="qr-image" />
          {isExpired && (
            <div className="qr-expired-overlay">
              <span>Mã đã hết hạn</span>
              <p>Vui lòng làm mới để tiếp tục bàn giao</p>
            </div>
          )}
        </div>
      </div>

      <div className="handover-code-box">
        <div className="handover-code-label">Mã xác nhận bàn giao:</div>
        <div className="handover-code-row">
          <span className="handover-code-text">{token.tokenCode}</span>
          <button
            type="button"
            className="copy-code-btn"
            onClick={handleCopy}
            title="Sao chép mã xác nhận"
          >
            {copied ? <Check size={16} className="text-success" /> : <Copy size={16} />}
            <span>{copied ? 'Đã sao chép' : 'Sao chép'}</span>
          </button>
        </div>
      </div>

      <div className={`handover-countdown ${isExpiringSoon ? 'warning' : ''} ${isExpired ? 'danger' : ''}`}>
        <Clock size={16} />
        <span>
          {isExpired
            ? 'Mã đã hết hiệu lực'
            : `Mã có hiệu lực trong: ${String(minutes).padStart(2, '0')}:${String(seconds).padStart(2, '0')}`}
        </span>
      </div>

      {token.meetingPointName && (
        <div className="handover-point-hint">
          <MapPin size={15} />
          <div>
            <strong>{token.meetingPointName}</strong>
            {token.meetingPointAddress && <p>{token.meetingPointAddress}</p>}
          </div>
        </div>
      )}

      <div className="handover-actions">
        <Button
          variant="secondary"
          icon={<RefreshCw size={15} className={refreshing ? 'spin' : ''} />}
          onClick={onRefresh}
          disabled={refreshing}
          className="refresh-qr-btn"
        >
          {isExpired ? 'Tạo mã mới ngay' : 'Làm mới mã'}
        </Button>
      </div>

      <p className="handover-note-text">
        Xuất trình mã này cho người giao tiếp theo để quét và hoàn tất thủ tục bàn giao kiện hàng.
      </p>
    </div>
  );
}
