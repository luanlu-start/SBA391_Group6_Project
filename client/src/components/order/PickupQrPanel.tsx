
import { useState } from 'react';
import { QRCodeSVG } from 'qrcode.react';
import { orderApi } from '../../api/orderApi';

interface Props {
  orderId: number;
}

export default function PickupQrPanel({ orderId }: Props) {
  const [token, setToken] = useState('');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');

  async function loadQr() {
    setLoading(true);
    setError('');

    try {
      const response = await orderApi.getPickupQr(orderId);
      setToken(response.data.qrToken);
    } catch {
      setError('Không thể tạo QR xác nhận lấy hàng.');
    } finally {
      setLoading(false);
    }
  }

  return (
    <div className="ui-stack" style={{ gap: 16 }}>
      <p>Hiển thị mã QR cho người giao khi bàn giao hàng.</p>

      <button
        type="button"
        className="ui-button ui-button-primary"
        onClick={() => void loadQr()}
        disabled={loading}
      >
        {loading ? 'Đang tạo...' : 'Hiển thị QR lấy hàng'}
      </button>

      {error && <p role="alert">{error}</p>}

      {token && (
        <div
          style={{
            background: '#fff',
            padding: 20,
            width: 'fit-content',
            borderRadius: 12,
          }}
        >
          <QRCodeSVG value={token} size={220} />
          <button
            type="button"
            onClick={() => void navigator.clipboard.writeText(token)}
          >
            Sao chép mã xác nhận (DEV)
          </button>
        </div>
      )}
    </div>
  );
}
