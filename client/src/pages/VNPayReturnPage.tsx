import { useEffect, useState } from 'react';
import { useSearchParams, Link } from 'react-router-dom';
import { CheckCircle2, XCircle, Wallet, PackageCheck, RefreshCw } from 'lucide-react';
import { walletApi } from '../api/walletApi';
import { getApiError } from '../api/ApiError';
import type { VNPayReturnResponse } from '../types/payment';
import '../styles/payment.css';

export default function VNPayReturnPage() {
  const [searchParams] = useSearchParams();
  const [loading, setLoading] = useState(true);
  const [result, setResult] = useState<VNPayReturnResponse | null>(null);
  const [error, setError] = useState<string | null>(null);

  const verifyPayment = async () => {
    try {
      setLoading(true);
      setError(null);
      const params: Record<string, string> = {};
      searchParams.forEach((val, key) => {
        params[key] = val;
      });
      const res = await walletApi.verifyVNPayReturn(params);
      setResult(res.data);
    } catch (err) {
      const apiErr = getApiError(err);
      setError(apiErr.message);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    verifyPayment();
  }, [searchParams]);

  if (loading) {
    return (
      <div className="payment-result-container">
        <div className="payment-result-card">
          <RefreshCw className="spin" size={36} color="var(--primary)" style={{ margin: '0 auto var(--space-4)' }} />
          <h2 className="payment-result-title">Đang kiểm tra kết quả giao dịch</h2>
          <p className="payment-result-message">Vui lòng chờ trong khi hệ thống xác thực chữ ký bảo mật từ VNPay…</p>
        </div>
      </div>
    );
  }

  const isSuccess = result?.success;

  return (
    <div className="payment-result-container">
      <div className="payment-result-card">
        {isSuccess ? (
          <div className="payment-result-icon success">
            <CheckCircle2 size={40} />
          </div>
        ) : (
          <div className="payment-result-icon failed">
            <XCircle size={40} />
          </div>
        )}

        <h2 className="payment-result-title">
          {isSuccess ? 'Giao dịch thành công!' : 'Giao dịch không thành công'}
        </h2>

        <p className="payment-result-message">
          {result?.message || error || 'Đã có lỗi xảy ra trong quá trình xử lý giao dịch qua cổng VNPay.'}
        </p>

        {result && (
          <table className="payment-receipt-table">
            <tbody>
              <tr>
                <td>Mã tham chiếu hệ thống</td>
                <td>{result.orderCode}</td>
              </tr>
              {result.amount && (
                <tr>
                  <td>Số tiền giao dịch</td>
                  <td style={{ color: 'var(--text-accent)' }}>
                    {(result.amount / 100).toLocaleString('vi-VN')} VNĐ
                  </td>
                </tr>
              )}
              {result.transactionNo && (
                <tr>
                  <td>Mã giao dịch VNPay</td>
                  <td>{result.transactionNo}</td>
                </tr>
              )}
              {result.bankCode && (
                <tr>
                  <td>Ngân hàng / Kênh thanh toán</td>
                  <td>{result.bankCode}</td>
                </tr>
              )}
              {result.cardType && (
                <tr>
                  <td>Loại thẻ / Tài khoản</td>
                  <td>{result.cardType}</td>
                </tr>
              )}
              <tr>
                <td>Trạng thái phản hồi</td>
                <td>
                  <span style={{ color: isSuccess ? 'var(--accent-emerald)' : 'var(--accent-rose)' }}>
                    {result.responseCode === '00' ? 'Thành công (00)' : `Lỗi (${result.responseCode})`}
                  </span>
                </td>
              </tr>
            </tbody>
          </table>
        )}

        <div className="ui-form-actions" style={{ justifyContent: 'center' }}>
          <Link to="/wallet" className="ui-button ui-button-primary">
            <Wallet size={16} /> Đến trang Ví FPTPost
          </Link>
          <Link to="/orders/payment" className="ui-button ui-button-secondary">
            <PackageCheck size={16} /> Thanh toán Đơn hàng khác
          </Link>
        </div>
      </div>
    </div>
  );
}
