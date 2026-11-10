import { useEffect, useState, useId } from 'react';
import type { FormEvent } from 'react';
import { useNavigate } from 'react-router-dom';
import {
  Landmark,
  Plus,
  Trash2,
  ShieldCheck,
  Clock,
  CheckCircle2,
  ArrowLeft,
  RefreshCw,
} from 'lucide-react';
import { useAuthentication } from '../auth/useAuthentication';
import { walletApi } from '../api/walletApi';
import { getApiError } from '../api/ApiError';
import type { ApiError } from '../api/ApiError';
import type { BankAccountResponse } from '../types/payment';
import PageHeader from '../components/ui/PageHeader';
import SectionCard from '../components/ui/SectionCard';
import Button from '../components/ui/Button';
import Input from '../components/ui/Input';
import Alert from '../components/ui/Alert';
import Badge from '../components/ui/Badge';
import EmptyState from '../components/ui/EmptyState';
import Modal from '../components/ui/Modal';
import ApiErrorNotice from '../components/ApiErrorNotice';
import '../styles/payment.css';

const SUPPORTED_BANKS = [
  { code: 'VCB', name: 'Vietcombank' },
  { code: 'TCB', name: 'Techcombank' },
  { code: 'MB', name: 'MB Bank' },
  { code: 'BIDV', name: 'BIDV' },
  { code: 'CTG', name: 'VietinBank' },
  { code: 'ACB', name: 'ACB' },
  { code: 'VPB', name: 'VPBank' },
  { code: 'TPB', name: 'TPBank' },
  { code: 'STB', name: 'Sacombank' },
  { code: 'HDB', name: 'HDBank' },
  { code: 'VIB', name: 'VIB' },
  { code: 'SHB', name: 'SHB' },
  { code: 'MSB', name: 'MSB' },
  { code: 'OCB', name: 'OCB' },
  { code: 'AGRIBANK', name: 'Agribank' },
];

function formatDateTime(isoString?: string) {
  if (!isoString) return '—';
  try {
    return new Date(isoString).toLocaleString('vi-VN', {
      hour: '2-digit',
      minute: '2-digit',
      day: '2-digit',
      month: '2-digit',
      year: 'numeric',
    });
  } catch {
    return isoString;
  }
}

export default function BankAccountsPage() {
  const { user, initializing } = useAuthentication();
  const navigate = useNavigate();

  const [accounts, setAccounts] = useState<BankAccountResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [submitting, setSubmitting] = useState(false);
  const [deletingId, setDeletingId] = useState<number | null>(null);
  const [deleteTarget, setDeleteTarget] = useState<BankAccountResponse | null>(null);
  const [error, setError] = useState<ApiError | null>(null);
  const [successMessage, setSuccessMessage] = useState<string | null>(null);

  const [bankCode, setBankCode] = useState('VCB');
  const [accountNumber, setAccountNumber] = useState('');
  const [holderName, setHolderName] = useState('');

  const accountNoId = useId();
  const holderNameId = useId();

  const loadAccounts = async () => {
    try {
      setLoading(true);
      setError(null);
      const res = await walletApi.getBankAccounts();
      setAccounts(res.data || []);
    } catch (err) {
      setError(getApiError(err));
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    if (user) {
      loadAccounts();
    }
  }, [user]);

  if (initializing) {
    return (
      <section className="ui-stack">
        <p className="ui-muted">Đang kiểm tra phiên đăng nhập…</p>
      </section>
    );
  }

  if (!user) {
    return (
      <div className="ui-stack">
        <PageHeader
          title="Tài khoản ngân hàng nhận tiền"
          description="Vui lòng đăng nhập để quản lý tài khoản ngân hàng nhận tiền."
        />
        <SectionCard title="Yêu cầu xác thực">
          <p className="ui-muted">Bạn cần đăng nhập để quản lý tài khoản ngân hàng liên kết.</p>
          <div className="ui-form-actions" style={{ marginTop: 'var(--space-4)' }}>
            <Button variant="primary" onClick={() => navigate('/login')}>
              Đăng nhập ngay
            </Button>
          </div>
        </SectionCard>
      </div>
    );
  }

  const handleCreate = async (e: FormEvent) => {
    e.preventDefault();
    if (!accountNumber.trim() || !holderName.trim()) return;

    const selectedBank = SUPPORTED_BANKS.find((b) => b.code === bankCode);

    try {
      setSubmitting(true);
      setError(null);
      setSuccessMessage(null);

      const res = await walletApi.createBankAccount({
        bankCode,
        bankName: selectedBank?.name || bankCode,
        accountNumber: accountNumber.trim().replace(/\s+/g, ''),
        holderName: holderName.trim().toUpperCase(),
      });

      setAccountNumber('');
      setHolderName('');
      setSuccessMessage(
        `Đã liên kết tài khoản ${res.data.bankName} (${res.data.maskedAccountNumber}) thành công. Tài khoản sẽ sẵn sàng rút tiền sau 24 giờ chờ bảo mật.`
      );
      await loadAccounts();
    } catch (err) {
      setError(getApiError(err));
    } finally {
      setSubmitting(false);
    }
  };

  const handleConfirmDelete = async () => {
    if (!deleteTarget) return;
    const acc = deleteTarget;

    try {
      setDeletingId(acc.id);
      setError(null);
      await walletApi.deleteBankAccount(acc.id);
      setSuccessMessage(`Đã xóa tài khoản ngân hàng ${acc.bankName} (${acc.maskedAccountNumber}).`);
      setDeleteTarget(null);
      await loadAccounts();
    } catch (err) {
      setError(getApiError(err));
      setDeleteTarget(null);
    } finally {
      setDeletingId(null);
    }
  };

  return (
    <div className="ui-stack" style={{ maxWidth: 960, margin: '0 auto' }}>
      <PageHeader
        eyebrow="Ví FPTPost · UC-39"
        title="Tài khoản ngân hàng nhận tiền"
        description="Liên kết tài khoản ngân hàng chính chủ khớp với hồ sơ KYC để nhận tiền rút từ ví."
        actions={
          <div style={{ display: 'flex', gap: 'var(--space-2)' }}>
            <Button
              variant="secondary"
              size="md"
              onClick={() => navigate('/wallet')}
              icon={<ArrowLeft size={15} />}
            >
              Quay lại Ví
            </Button>
            <Button
              variant="secondary"
              size="md"
              onClick={loadAccounts}
              disabled={loading}
              icon={<RefreshCw size={15} />}
            >
              Làm mới
            </Button>
          </div>
        }
      />

      {error && <ApiErrorNotice error={error} />}

      {successMessage && (
        <Alert
          tone="success"
          action={
            <Button variant="ghost" size="sm" onClick={() => setSuccessMessage(null)}>
              Đóng
            </Button>
          }
        >
          <div style={{ display: 'flex', alignItems: 'center', gap: 'var(--space-2)' }}>
            <CheckCircle2 size={18} color="var(--accent-emerald)" />
            <span>{successMessage}</span>
          </div>
        </Alert>
      )}

      <SectionCard
        title="Liên kết tài khoản ngân hàng mới"
        description="Tên chủ tài khoản phải trùng khớp với họ tên trên hồ sơ KYC đã duyệt (BR-R-058). Số tài khoản được mã hóa AES-256-GCM khi lưu trữ."
        actions={<Badge tone="info" dot>Bảo mật 24h Cooldown</Badge>}
      >
        <form onSubmit={handleCreate} className="ui-stack">
          <div
            style={{
              display: 'grid',
              gridTemplateColumns: 'repeat(auto-fit, minmax(240px, 1fr))',
              gap: 'var(--space-4)',
            }}
          >
            <div>
              <label htmlFor="bank-code-select" className="ui-label" style={{ marginBottom: 'var(--space-1)' }}>
                Ngân hàng thụ hưởng:
              </label>
              <select
                id="bank-code-select"
                className="ui-control ui-select"
                value={bankCode}
                onChange={(e) => setBankCode(e.target.value)}
              >
                {SUPPORTED_BANKS.map((b) => (
                  <option key={b.code} value={b.code}>
                    {b.name} ({b.code})
                  </option>
                ))}
              </select>
            </div>

            <Input
              id={accountNoId}
              label="Số tài khoản ngân hàng:"
              type="text"
              inputMode="numeric"
              value={accountNumber}
              onChange={(e) => setAccountNumber(e.target.value.replace(/\D/g, ''))}
              placeholder="VD: 0123456789"
              hint="Từ 6 đến 25 chữ số"
              required
            />

            <Input
              id={holderNameId}
              label="Họ và tên chủ tài khoản:"
              type="text"
              value={holderName}
              onChange={(e) => setHolderName(e.target.value.toUpperCase())}
              placeholder="VD: LE NHUT HUY"
              hint="Phải khớp họ tên trên CCCD/KYC đã duyệt"
              required
            />
          </div>

          <div
            style={{
              background: 'rgba(255, 255, 255, 0.02)',
              border: '1px solid var(--border-subtle)',
              borderRadius: 'var(--radius-md)',
              padding: 'var(--space-3) var(--space-4)',
              display: 'flex',
              alignItems: 'center',
              gap: 'var(--space-2)',
              fontSize: '0.85rem',
              color: 'var(--text-secondary)',
            }}
          >
            <ShieldCheck size={18} color="var(--primary)" />
            <span>
              Theo quy tắc an toàn <strong>BR-R-058</strong>, tài khoản ngân hàng mới thêm phải chờ đủ{' '}
              <strong>24 giờ</strong> trước khi có thể tạo lệnh rút tiền.
            </span>
          </div>

          <div className="ui-form-actions">
            <Button
              type="submit"
              variant="primary"
              loading={submitting}
              loadingText="Đang đối chiếu KYC & lưu…"
              disabled={submitting || accountNumber.length < 6 || !holderName.trim()}
              icon={<Plus size={16} />}
            >
              Thêm tài khoản ngân hàng
            </Button>
          </div>
        </form>
      </SectionCard>

      <SectionCard
        title="Danh sách tài khoản ngân hàng đã liên kết"
        description="Các tài khoản ngân hàng của bạn được che số bảo mật và chỉ hiển thị 4 số cuối."
      >
        {loading ? (
          <div style={{ padding: 'var(--space-6)', textAlign: 'center', color: 'var(--text-muted)' }}>
            <RefreshCw className="spin" size={24} style={{ margin: '0 auto var(--space-2)' }} />
            <p>Đang tải danh sách tài khoản ngân hàng…</p>
          </div>
        ) : accounts.length === 0 ? (
          <EmptyState
            title="Chưa có tài khoản ngân hàng liên kết"
            description="Hãy thêm tài khoản ngân hàng chính chủ ở biểu mẫu phía trên để sử dụng khi rút tiền từ ví."
            icon={<Landmark size={32} />}
          />
        ) : (
          <div className="tx-table-container">
            <table className="tx-table">
              <thead>
                <tr>
                  <th>Ngân hàng</th>
                  <th>Số tài khoản (Đã che)</th>
                  <th>Chủ tài khoản</th>
                  <th>Trạng thái rút tiền</th>
                  <th>Hiệu lực từ</th>
                  <th>Thao tác</th>
                </tr>
              </thead>
              <tbody>
                {accounts.map((acc) => (
                  <tr key={acc.id}>
                    <td>
                      <strong>{acc.bankName}</strong>
                      <div style={{ fontSize: '0.75rem', color: 'var(--text-muted)' }}>Mã: {acc.bankCode}</div>
                    </td>
                    <td>
                      <span className="tx-code">{acc.maskedAccountNumber}</span>
                    </td>
                    <td>
                      <strong>{acc.holderName}</strong>
                    </td>
                    <td>
                      {acc.readyForWithdrawal ? (
                        <Badge tone="success" dot>
                          Sẵn sàng rút tiền
                        </Badge>
                      ) : (
                        <Badge tone="warning" dot>
                          <Clock size={12} style={{ marginRight: 4 }} />
                          Đang chờ 24h bảo mật
                        </Badge>
                      )}
                    </td>
                    <td>
                      <span className="tx-date">{formatDateTime(acc.cooldownUntil)}</span>
                    </td>
                    <td>
                      <Button
                        variant="ghost"
                        size="md"
                        disabled={deletingId === acc.id}
                        onClick={() => setDeleteTarget(acc)}
                        icon={<Trash2 size={14} />}
                      >
                        {deletingId === acc.id ? 'Đang xóa…' : 'Xóa'}
                      </Button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </SectionCard>

      <Modal
        isOpen={!!deleteTarget}
        onClose={() => deletingId === null && setDeleteTarget(null)}
        closeDisabled={deletingId !== null}
        title="Xác nhận xóa tài khoản ngân hàng"
        description={
          deleteTarget
            ? `Bạn có chắc chắn muốn gỡ liên kết tài khoản ${deleteTarget.bankName} (${deleteTarget.maskedAccountNumber})?`
            : undefined
        }
        size="md"
      >
        {deleteTarget && (
          <div className="ui-stack">
            <Alert tone="warning">
              Sau khi xóa, nếu muốn liên kết lại tài khoản này bạn sẽ cần chờ lại thời gian bảo mật 24 giờ.
            </Alert>
            <div className="ui-form-actions" style={{ justifyContent: 'flex-end', gap: 'var(--space-2)' }}>
              <Button
                type="button"
                variant="secondary"
                disabled={deletingId !== null}
                onClick={() => setDeleteTarget(null)}
              >
                Đóng
              </Button>
              <Button
                type="button"
                variant="danger"
                loading={deletingId === deleteTarget.id}
                loadingText="Đang xóa…"
                onClick={() => void handleConfirmDelete()}
              >
                Xác nhận xóa
              </Button>
            </div>
          </div>
        )}
      </Modal>
    </div>
  );
}
