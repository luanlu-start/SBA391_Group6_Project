
import { useState } from 'react';
import { orderApi } from '../../api/orderApi';

interface Props {
  orderId: number;
  onSuccess: () => void;
}

export default function ConfirmPickupPanel({
  orderId,
  onSuccess,
}: Props) {
  const [qrToken, setQrToken] = useState('');
  const [photo, setPhoto] = useState<File | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');

  async function confirm() {
    if (!qrToken.trim() || !photo) {
      setError('Vui lòng nhập mã QR và chọn ảnh.');
      return;
    }

    setLoading(true);
    setError('');

    try {
      await orderApi.confirmPickup(
        orderId,
        qrToken.trim(),
        photo
      );
      onSuccess();
    } catch {
      setError(
        'Xác nhận thất bại. Kiểm tra QR, trạng thái đơn và quyền truy cập.'
      );
    } finally {
      setLoading(false);
    }
  }

  return (
    <div className="ui-stack" style={{ gap: 12 }}>
      <label htmlFor={`pickup-qr-${orderId}`}>
        Mã xác nhận lấy hàng
      </label>

      <input
        id={`pickup-qr-${orderId}`}
        className="ui-control"
        value={qrToken}
        onChange={event => setQrToken(event.target.value)}
        placeholder="Nhập mã xác nhận từ Sender"
      />

      <label htmlFor={`pickup-photo-${orderId}`}>
        Ảnh bằng chứng lấy hàng
      </label>

      <input
        id={`pickup-photo-${orderId}`}
        type="file"
        accept="image/jpeg,image/png,image/webp,image/gif"
        capture="environment"
        onChange={event =>
          setPhoto(event.target.files?.[0] ?? null)
        }
      />

      {error && <p role="alert">{error}</p>}

      <button
        type="button"
        className="ui-button ui-button-primary"
        disabled={loading || !photo || !qrToken.trim()}
        onClick={() => void confirm()}
      >
        {loading ? 'Đang xác nhận...' : 'Xác nhận đã lấy hàng'}
      </button>
    </div>
  );
}
