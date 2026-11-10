import { useEffect, useState, useId } from 'react';
import type { FormEvent } from 'react';
import {
  ShieldCheck,
  AlertTriangle,
  Play,
  RefreshCw,
  CheckCircle2,
  FileSearch,
  Wrench,
} from 'lucide-react';
import { reconciliationApi } from '../api/reconciliationApi';
import { getApiError } from '../api/ApiError';
import type { ApiError } from '../api/ApiError';
import type {
  ReconciliationReportResponse,
  ReconciliationItemResponse,
  LedgerIntegrityResponse,
} from '../types/payment';
import PageHeader from '../components/ui/PageHeader';
import SectionCard from '../components/ui/SectionCard';
import Button from '../components/ui/Button';
import Input from '../components/ui/Input';
import Alert from '../components/ui/Alert';
import Badge from '../components/ui/Badge';
import Modal from '../components/ui/Modal';
import Pagination from '../components/ui/Pagination';
import EmptyState from '../components/ui/EmptyState';
import ApiErrorNotice from '../components/ApiErrorNotice';
import '../styles/payment.css';

function formatDateTime(iso?: string) {
  if (!iso) return '—';
  try {
    return new Date(iso).toLocaleString('vi-VN', {
      hour: '2-digit',
      minute: '2-digit',
      day: '2-digit',
      month: '2-digit',
      year: 'numeric',
    });
  } catch {
    return iso;
  }
}

const DISCREPANCY_LABELS: Record<
  ReconciliationItemResponse['discrepancyType'],
  { label: string; tone: 'warning' | 'danger' | 'info' | 'neutral'; summary: string }
> = {
  NONE: { label: 'Khớp', tone: 'neutral', summary: 'Không có sai lệch.' },
  MISSING_LEDGER: {
    label: 'Thiếu sổ cái (VNPay có tiền)',
    tone: 'warning',
    summary: 'Cổng VNPay báo giao dịch thành công nhưng sổ cái nội bộ FPTPost chưa có bút toán ghi nhận.',
  },
  STALE_PENDING: {
    label: 'Giao dịch treo > 15 phút',
    tone: 'warning',
    summary: 'Khách đã mở cổng VNPay nhưng bỏ dở quá 15 phút chưa thanh toán — chưa có tiền thật vào hệ thống.',
  },
  AMOUNT_MISMATCH: {
    label: 'Lệch số tiền',
    tone: 'danger',
    summary: 'Số tiền bên VNPay thu được và số tiền ghi trên sổ cái FPTPost không khớp nhau.',
  },
  MISSING_PAYMENT: {
    label: 'Thiếu giao dịch VNPay',
    tone: 'danger',
    summary: 'Sổ cái nội bộ có ghi nhận tiền nhưng VNPay báo thất bại/hủy hoặc không có giao dịch đối ứng.',
  },
};

interface ResolutionPreset {
  id: string;
  title: string;
  description: string;
  debitAccount: string;
  creditAccount: string;
  defaultNote: string;
  needsUserId?: boolean;
}

function getPresetsForItem(item: ReconciliationItemResponse, userIdInput: string): ResolutionPreset[] {
  const uid = userIdInput.replace(/\D/g, '') || '3';
  const diff = Math.abs(item.vnpayAmount - item.internalAmount).toLocaleString('vi-VN');

  if (item.discrepancyType === 'STALE_PENDING') {
    return [
      {
        id: 'CLOSE_STALE',
        title: 'Xác nhận giao dịch quá hạn & đóng cảnh báo treo',
        description: 'Ghi nhận chốt sổ cho giao dịch khách bỏ dở quá 15 phút (không phát sinh cộng/trừ tiền ví khách).',
        debitAccount: 'RECONCILIATION_SUSPENSE',
        creditAccount: 'VNPAY_CLEARING',
        defaultNote: `Xác nhận đóng cảnh báo giao dịch treo quá hạn ${item.txnRef} (không phát sinh thu tiền).`,
      },
    ];
  }

  if (item.discrepancyType === 'MISSING_LEDGER') {
    return [
      {
        id: 'RECORD_VNPAY_SUSPENSE',
        title: 'Ghi nhận bù tiền từ VNPay vào quỹ treo đối soát (Khuyên dùng)',
        description: 'Dùng khi VNPay đã thu tiền và bạn cần khớp số liệu kế toán trung gian cho giao dịch này.',
        debitAccount: 'VNPAY_CLEARING',
        creditAccount: 'RECONCILIATION_SUSPENSE',
        defaultNote: `Ghi nhận bổ sung khoản thu thành công từ VNPay (${item.txnRef}) vào tài khoản treo đối soát.`,
      },
      {
        id: 'CREDIT_USER_WALLET',
        title: 'Bù tiền trực tiếp vào Ví người dùng',
        description: 'Dùng khi xác minh khách đã trả tiền thật trên VNPay nhưng ví người dùng chưa được cộng tiền.',
        debitAccount: 'VNPAY_CLEARING',
        creditAccount: `USER_WALLET_${uid}`,
        defaultNote: `Ghi nhận bù tiền giao dịch VNPay ${item.txnRef} vào ví người dùng #${uid}.`,
        needsUserId: true,
      },
    ];
  }

  if (item.discrepancyType === 'AMOUNT_MISMATCH') {
    if (item.vnpayAmount >= item.internalAmount) {
      return [
        {
          id: 'FIX_AMT_HIGHER_VNPAY',
          title: `Ghi bù phần chênh lệch thiếu (${diff} đ) khớp với VNPay`,
          description: 'VNPay thu nhiều hơn số tiền đang ghi trên sổ cái nội bộ.',
          debitAccount: 'VNPAY_CLEARING',
          creditAccount: 'RECONCILIATION_SUSPENSE',
          defaultNote: `Điều chỉnh bù phần chênh lệch thiếu ${diff} đ cho giao dịch ${item.txnRef}.`,
        },
      ];
    }
    return [
      {
        id: 'FIX_AMT_LOWER_VNPAY',
        title: `Ghi giảm phần chênh lệch dư (${diff} đ) trên sổ cái`,
        description: 'Sổ cái nội bộ đang ghi cao hơn số tiền thực tế VNPay thu được.',
        debitAccount: 'RECONCILIATION_SUSPENSE',
        creditAccount: 'VNPAY_CLEARING',
        defaultNote: `Điều chỉnh giảm phần chênh lệch dư ${diff} đ cho giao dịch ${item.txnRef}.`,
      },
    ];
  }

  return [
    {
      id: 'REVERSE_SUSPENSE',
      title: 'Ghi bút toán đảo để cân bằng lại sổ cái (Khuyên dùng)',
      description: 'Xác nhận giao dịch không thành công ở VNPay và cân bằng lại tài khoản đối soát trung gian.',
      debitAccount: 'RECONCILIATION_SUSPENSE',
      creditAccount: 'VNPAY_CLEARING',
      defaultNote: `Ghi bút toán đảo cân bằng sổ cái cho giao dịch thiếu đối ứng VNPay ${item.txnRef}.`,
    },
    {
      id: 'DEBIT_USER_WALLET',
      title: 'Thu hồi từ Ví người dùng về tài khoản treo (Nếu đã cộng nhầm ví)',
      description: 'Chỉ dùng khi xác minh hệ thống đã cộng nhầm tiền vào ví nhưng VNPay không thu được tiền.',
      debitAccount: `USER_WALLET_${uid}`,
      creditAccount: 'RECONCILIATION_SUSPENSE',
      defaultNote: `Thu hồi số tiền hạch toán nhầm từ ví người dùng #${uid} cho giao dịch ${item.txnRef}.`,
      needsUserId: true,
    },
  ];
}

export default function AdminReconciliationPage() {
  const todayStr = new Date().toISOString().slice(0, 10);

  const [reports, setReports] = useState<ReconciliationReportResponse[]>([]);
  const [selectedReport, setSelectedReport] = useState<ReconciliationReportResponse | null>(null);
  const [integrity, setIntegrity] = useState<LedgerIntegrityResponse | null>(null);

  const [loading, setLoading] = useState(true);
  const [running, setRunning] = useState(false);
  const [checkingIntegrity, setCheckingIntegrity] = useState(false);
  const [error, setError] = useState<ApiError | null>(null);
  const [successMessage, setSuccessMessage] = useState<string | null>(null);

  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [totalElements, setTotalElements] = useState(0);
  const [reportDate, setReportDate] = useState(todayStr);

  // Adjustment modal state (API-108)
  const [adjustingItem, setAdjustingItem] = useState<ReconciliationItemResponse | null>(null);
  const [selectedPresetId, setSelectedPresetId] = useState<string>('');
  const [targetUserId, setTargetUserId] = useState<string>('3');
  const [debitAccount, setDebitAccount] = useState('VNPAY_CLEARING');
  const [creditAccount, setCreditAccount] = useState('RECONCILIATION_SUSPENSE');
  const [adjustAmount, setAdjustAmount] = useState('');
  const [adjustNote, setAdjustNote] = useState('');
  const [adjustSubmitting, setAdjustSubmitting] = useState(false);

  const userInputId = useId();
  const debitInputId = useId();
  const creditInputId = useId();
  const amountInputId = useId();
  const noteInputId = useId();

  const loadData = async (targetPage = 0) => {
    try {
      setLoading(true);
      setError(null);
      const [reportsRes, integrityRes] = await Promise.all([
        reconciliationApi.getReports(targetPage, 10),
        reconciliationApi.checkLedgerIntegrity(),
      ]);

      const content = reportsRes.data.content || [];
      setReports(content);
      setPage(reportsRes.data.page);
      setTotalPages(reportsRes.data.totalPages);
      setTotalElements(reportsRes.data.totalElements);
      setIntegrity(integrityRes.data);

      if (content.length > 0 && !selectedReport) {
        setSelectedReport(content[0]);
      } else if (selectedReport) {
        const updated = content.find((r) => r.id === selectedReport.id);
        if (updated) setSelectedReport(updated);
      }
    } catch (err) {
      setError(getApiError(err));
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadData(0);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const handleRunReconciliation = async (e: FormEvent) => {
    e.preventDefault();
    try {
      setRunning(true);
      setError(null);
      setSuccessMessage(null);
      const res = await reconciliationApi.runReconciliation(reportDate || undefined);
      setSelectedReport(res.data);
      setSuccessMessage(
        `Đã hoàn tất đối soát ngày ${res.data.reportDate}: tổng ${res.data.totalCount} giao dịch (${res.data.matchedCount} khớp, ${res.data.mismatchCount} lệch).`
      );
      await loadData(0);
    } catch (err) {
      setError(getApiError(err));
    } finally {
      setRunning(false);
    }
  };

  const handleCheckIntegrity = async () => {
    try {
      setCheckingIntegrity(true);
      setError(null);
      const res = await reconciliationApi.checkLedgerIntegrity();
      setIntegrity(res.data);
      setSuccessMessage(res.data.message);
    } catch (err) {
      setError(getApiError(err));
    } finally {
      setCheckingIntegrity(false);
    }
  };

  const openAdjustModal = (item: ReconciliationItemResponse) => {
    setAdjustingItem(item);
    const defaultUid = '3';
    setTargetUserId(defaultUid);
    const diff = Math.abs(item.vnpayAmount - item.internalAmount);
    setAdjustAmount(String(diff > 0 ? diff : item.vnpayAmount || item.internalAmount || 50000));
    const presets = getPresetsForItem(item, defaultUid);
    const first = presets[0];
    setSelectedPresetId(first.id);
    setDebitAccount(first.debitAccount);
    setCreditAccount(first.creditAccount);
    setAdjustNote(first.defaultNote);
  };

  const applyPreset = (preset: ResolutionPreset) => {
    setSelectedPresetId(preset.id);
    setDebitAccount(preset.debitAccount);
    setCreditAccount(preset.creditAccount);
    setAdjustNote(preset.defaultNote);
  };

  const handleTargetUserIdChange = (nextUid: string) => {
    const cleanUid = nextUid.replace(/\D/g, '');
    setTargetUserId(cleanUid);
    if (!adjustingItem) return;
    const presets = getPresetsForItem(adjustingItem, cleanUid || '3');
    const matched = presets.find((p) => p.id === selectedPresetId);
    if (matched) {
      setDebitAccount(matched.debitAccount);
      setCreditAccount(matched.creditAccount);
      setAdjustNote(matched.defaultNote);
    }
  };

  const handleAdjustSubmit = async (e: FormEvent) => {
    e.preventDefault();
    if (!adjustingItem) return;

    try {
      setAdjustSubmitting(true);
      setError(null);
      const res = await reconciliationApi.adjustItem(adjustingItem.id, {
        debitAccount: debitAccount.trim().toUpperCase(),
        creditAccount: creditAccount.trim().toUpperCase(),
        amount: Number(adjustAmount),
        note: adjustNote.trim(),
      });

      setAdjustingItem(null);
      setSuccessMessage(
        `Đã tạo bút toán điều chỉnh ${res.data.adjustmentTxnCode} cho giao dịch ${res.data.txnRef}.`
      );
      await loadData(page);
    } catch (err) {
      setError(getApiError(err));
    } finally {
      setAdjustSubmitting(false);
    }
  };

  return (
    <div className="ui-stack">
      <PageHeader
        eyebrow="Quản trị Tài chính · UC-43"
        title="Đối soát giao dịch hằng ngày & Bất biến sổ cái"
        description="Đối chiếu giao dịch cổng VNPay với sổ cái kép nội bộ, kiểm tra bất biến Tổng Nợ = Tổng Có và xử lý dòng lệch bằng bút toán điều chỉnh."
        actions={
          <div style={{ display: 'flex', gap: 'var(--space-2)' }}>
            <Button
              variant="secondary"
              size="sm"
              onClick={handleCheckIntegrity}
              loading={checkingIntegrity}
              icon={<ShieldCheck size={15} />}
            >
              Kiểm tra bất biến sổ cái
            </Button>
            <Button
              variant="secondary"
              size="sm"
              onClick={() => loadData(page)}
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

      {/* Ledger Integrity Card (API-109) */}
      {integrity && (
        <SectionCard
          title="Trạng thái bất biến Sổ cái kép (Double-Entry Ledger Integrity)"
          description={integrity.message}
          actions={
            <Badge tone={integrity.balanced ? 'success' : 'danger'} dot>
              {integrity.balanced ? 'CÂN BẰNG TUYỆT ĐỐI' : 'PHÁT HIỆN LỆCH SỔ CÁI'}
            </Badge>
          }
        >
          <div className="statement-summary-grid">
            <div className="statement-card">
              <span className="statement-card-label">Tổng giao dịch sổ cái</span>
              <span className="statement-card-value">{integrity.totalTransactions.toLocaleString('vi-VN')}</span>
            </div>
            <div className="statement-card">
              <span className="statement-card-label">Tổng bút toán Nợ/Có</span>
              <span className="statement-card-value">{integrity.totalEntries.toLocaleString('vi-VN')}</span>
            </div>
            <div className="statement-card">
              <span className="statement-card-label">Tổng phát sinh Nợ (Debit)</span>
              <span className="statement-card-value inflow">
                {integrity.totalDebit.toLocaleString('vi-VN')} đ
              </span>
            </div>
            <div className="statement-card">
              <span className="statement-card-label">Tổng phát sinh Có (Credit)</span>
              <span className="statement-card-value inflow">
                {integrity.totalCredit.toLocaleString('vi-VN')} đ
              </span>
            </div>
            <div className="statement-card">
              <span className="statement-card-label">Độ lệch Nợ / Có</span>
              <span
                className="statement-card-value"
                style={{ color: integrity.discrepancyAmount === 0 ? 'var(--accent-emerald)' : 'var(--accent-rose)' }}
              >
                {integrity.discrepancyAmount.toLocaleString('vi-VN')} đ
              </span>
            </div>
          </div>
        </SectionCard>
      )}

      {/* Manual Run Reconciliation Bar (API-107) */}
      <SectionCard
        title="Chạy đối soát giao dịch theo ngày"
        description="Hệ thống đối chiếu toàn bộ giao dịch VNPay và Sổ cái trong ngày báo cáo. Nếu có dòng lệch, hệ thống cảnh báo Admin và không tự động sửa số dư ví (BR-R-062)."
      >
        <form onSubmit={handleRunReconciliation} className="tx-filter-bar">
          <div className="tx-filter-item">
            <label htmlFor="reconcile-date-input">Ngày báo cáo đối soát:</label>
            <input
              id="reconcile-date-input"
              type="date"
              className="ui-control ui-input"
              value={reportDate}
              onChange={(e) => setReportDate(e.target.value)}
              required
            />
          </div>
          <div className="tx-filter-actions">
            <Button
              type="submit"
              variant="primary"
              size="sm"
              loading={running}
              loadingText="Đang đối soát…"
              icon={<Play size={15} />}
            >
              Chạy đối soát ngay
            </Button>
          </div>
        </form>
      </SectionCard>

      {/* Reports List (API-106) */}
      <SectionCard
        title="Danh sách báo cáo đối soát hằng ngày"
        description="Nhấn vào một kỳ báo cáo để xem chi tiết từng dòng giao dịch khớp hoặc lệch."
      >
        {loading ? (
          <div style={{ padding: 'var(--space-6)', textAlign: 'center', color: 'var(--text-muted)' }}>
            <RefreshCw className="spin" size={24} style={{ margin: '0 auto var(--space-2)' }} />
            <p>Đang tải báo cáo đối soát…</p>
          </div>
        ) : reports.length === 0 ? (
          <EmptyState
            title="Chưa có báo cáo đối soát nào"
            description="Hãy chọn ngày báo cáo và nhấn 'Chạy đối soát ngay' để tạo báo cáo đối soát đầu tiên."
            icon={<FileSearch size={32} />}
          />
        ) : (
          <div className="ui-stack">
            <div className="tx-table-container">
              <table className="tx-table">
                <thead>
                  <tr>
                    <th>ID</th>
                    <th>Ngày báo cáo</th>
                    <th>Tổng GD</th>
                    <th>Khớp</th>
                    <th>Lệch</th>
                    <th>Trạng thái</th>
                    <th>Thời gian chạy</th>
                    <th>Chi tiết</th>
                  </tr>
                </thead>
                <tbody>
                  {reports.map((rep) => (
                    <tr
                      key={rep.id}
                      style={{
                        background:
                          selectedReport?.id === rep.id ? 'rgba(59, 130, 246, 0.08)' : undefined,
                      }}
                    >
                      <td>#{rep.id}</td>
                      <td>
                        <strong>{rep.reportDate}</strong>
                      </td>
                      <td>{rep.totalCount}</td>
                      <td style={{ color: 'var(--accent-emerald)', fontWeight: 600 }}>
                        {rep.matchedCount}
                      </td>
                      <td
                        style={{
                          color: rep.mismatchCount > 0 ? 'var(--accent-rose)' : 'var(--text-muted)',
                          fontWeight: 600,
                        }}
                      >
                        {rep.mismatchCount}
                      </td>
                      <td>
                        <Badge
                          tone={
                            rep.status === 'MATCHED'
                              ? 'success'
                              : rep.status === 'RESOLVED'
                              ? 'info'
                              : 'danger'
                          }
                          dot
                        >
                          {rep.status === 'MATCHED'
                            ? 'Khớp 100%'
                            : rep.status === 'RESOLVED'
                            ? 'Đã điều chỉnh'
                            : 'Có dòng lệch'}
                        </Badge>
                      </td>
                      <td>{formatDateTime(rep.createdAt)}</td>
                      <td>
                        <Button
                          variant="secondary"
                          size="sm"
                          onClick={() => setSelectedReport(rep)}
                        >
                          Xem ({rep.items?.length ?? rep.totalCount} dòng)
                        </Button>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>

            {totalPages > 1 && (
              <Pagination
                page={page}
                size={10}
                totalElements={totalElements}
                totalPages={totalPages}
                onPageChange={(p) => loadData(p)}
                disabled={loading}
              />
            )}
          </div>
        )}
      </SectionCard>

      {/* Selected Report Items Detail & Adjustment Action (API-108) */}
      {selectedReport && (
        <SectionCard
          title={`Chi tiết dòng đối soát — Ngày ${selectedReport.reportDate} (Báo cáo #${selectedReport.id})`}
          description="Các dòng lệch cần Admin kiểm tra và tạo bút toán điều chỉnh trên sổ cái kép (BR-F-083)."
        >
          {!selectedReport.items || selectedReport.items.length === 0 ? (
            <p className="ui-muted">Không có giao dịch phát sinh trong ngày báo cáo này.</p>
          ) : (
            <div className="tx-table-container">
              <table className="tx-table">
                <thead>
                  <tr>
                    <th>Mã tham chiếu (txnRef)</th>
                    <th>Phân loại</th>
                    <th>Số tiền Sổ cái</th>
                    <th>Số tiền VNPay</th>
                    <th>Loại sai lệch</th>
                    <th>Trạng thái</th>
                    <th>Ghi chú / Bút toán ĐC</th>
                    <th>Thao tác</th>
                  </tr>
                </thead>
                <tbody>
                  {selectedReport.items.map((item) => (
                    <tr key={item.id}>
                      <td>
                        <span className="tx-code">{item.txnRef}</span>
                      </td>
                      <td>{item.kind}</td>
                      <td>{item.internalAmount.toLocaleString('vi-VN')} đ</td>
                      <td>{item.vnpayAmount.toLocaleString('vi-VN')} đ</td>
                      <td>
                        {item.discrepancyType === 'NONE' ? (
                          <span className="ui-muted">—</span>
                        ) : (
                          <span title={DISCREPANCY_LABELS[item.discrepancyType]?.summary}>
                            <Badge tone={DISCREPANCY_LABELS[item.discrepancyType]?.tone || 'warning'}>
                              {DISCREPANCY_LABELS[item.discrepancyType]?.label || item.discrepancyType}
                            </Badge>
                          </span>
                        )}
                      </td>
                      <td>
                        <Badge
                          tone={
                            item.status === 'MATCHED'
                              ? 'success'
                              : item.status === 'ADJUSTED'
                              ? 'info'
                              : 'danger'
                          }
                        >
                          {item.status === 'MATCHED'
                            ? 'Khớp'
                            : item.status === 'ADJUSTED'
                            ? 'Đã xử lý'
                            : 'Lệch'}
                        </Badge>
                      </td>
                      <td>
                        <div style={{ fontSize: '0.85rem' }}>{item.note || '—'}</div>
                        {item.adjustmentTxnCode && (
                          <div style={{ fontSize: '0.75rem', color: 'var(--primary)', marginTop: 2 }}>
                            Mã bút toán ĐC: <strong>{item.adjustmentTxnCode}</strong>
                          </div>
                        )}
                      </td>
                      <td>
                        {item.status === 'MISMATCH' ? (
                          <Button
                            variant="primary"
                            size="sm"
                            onClick={() => openAdjustModal(item)}
                            icon={<Wrench size={14} />}
                          >
                            Xử lý lệch
                          </Button>
                        ) : (
                          <span className="ui-muted">—</span>
                        )}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </SectionCard>
      )}

      {/* Modal tạo bút toán điều chỉnh dòng lệch (API-108) */}
      <Modal
        isOpen={!!adjustingItem}
        onClose={() => !adjustSubmitting && setAdjustingItem(null)}
        title="Xử lý dòng sai lệch đối soát (UC-43)"
        description={
          adjustingItem
            ? `Mã tham chiếu ${adjustingItem.txnRef} — ${DISCREPANCY_LABELS[adjustingItem.discrepancyType]?.label || adjustingItem.discrepancyType}`
            : ''
        }
        size="md"
      >
        {adjustingItem && (() => {
          const info = DISCREPANCY_LABELS[adjustingItem.discrepancyType];
          const presets = getPresetsForItem(adjustingItem, targetUserId);
          const activePreset = presets.find((p) => p.id === selectedPresetId) || presets[0];

          return (
            <form onSubmit={handleAdjustSubmit} className="ui-stack">
              {/* Giải thích dễ hiểu chuyện gì đang lệch */}
              <div
                style={{
                  padding: '12px 14px',
                  borderRadius: 'var(--radius-md)',
                  background: 'rgba(255, 255, 255, 0.03)',
                  border: '1px solid var(--border-subtle)',
                  display: 'flex',
                  flexDirection: 'column',
                  gap: '8px',
                }}
              >
                <div style={{ fontSize: '0.88rem', color: 'var(--text-primary)', fontWeight: 600 }}>
                  Vì sao giao dịch này bị lệch?
                </div>
                <div style={{ fontSize: '0.84rem', color: 'var(--text-secondary)', lineHeight: 1.5 }}>
                  {info?.summary}
                </div>
                <div
                  style={{
                    display: 'grid',
                    gridTemplateColumns: 'repeat(3, minmax(0, 1fr))',
                    gap: '8px',
                    paddingTop: '8px',
                    borderTop: '1px solid var(--border-subtle)',
                    fontSize: '0.82rem',
                  }}
                >
                  <div>
                    <div style={{ color: 'var(--text-muted)' }}>Cổng VNPay</div>
                    <strong style={{ color: 'var(--accent-cyan)' }}>
                      {adjustingItem.vnpayAmount.toLocaleString('vi-VN')} đ
                    </strong>
                  </div>
                  <div>
                    <div style={{ color: 'var(--text-muted)' }}>Sổ cái FPTPost</div>
                    <strong style={{ color: 'var(--text-primary)' }}>
                      {adjustingItem.internalAmount.toLocaleString('vi-VN')} đ
                    </strong>
                  </div>
                  <div>
                    <div style={{ color: 'var(--text-muted)' }}>Chênh lệch</div>
                    <strong style={{ color: 'var(--accent-amber)' }}>
                      {Math.abs(adjustingItem.vnpayAmount - adjustingItem.internalAmount).toLocaleString('vi-VN')} đ
                    </strong>
                  </div>
                </div>
              </div>

              {/* Chọn phương án xử lý bằng tiếng Việt thông thường */}
              <div className="ui-stack" style={{ gap: '8px' }}>
                <span style={{ fontSize: '0.86rem', fontWeight: 600, color: 'var(--text-primary)' }}>
                  Chọn cách xử lý:
                </span>
                {presets.map((preset) => {
                  const selected = activePreset?.id === preset.id;
                  return (
                    <label
                      key={preset.id}
                      style={{
                        display: 'flex',
                        gap: '10px',
                        alignItems: 'flex-start',
                        padding: '10px 12px',
                        borderRadius: 'var(--radius-md)',
                        border: selected
                          ? '1px solid var(--primary)'
                          : '1px solid var(--border-subtle)',
                        background: selected
                          ? 'rgba(249, 115, 22, 0.08)'
                          : 'rgba(255, 255, 255, 0.02)',
                        cursor: 'pointer',
                      }}
                    >
                      <input
                        type="radio"
                        name="reconcile-preset"
                        checked={selected}
                        onChange={() => applyPreset(preset)}
                        style={{ marginTop: '4px' }}
                      />
                      <div style={{ display: 'flex', flexDirection: 'column', gap: '2px' }}>
                        <span style={{ fontWeight: 600, fontSize: '0.88rem', color: 'var(--text-primary)' }}>
                          {preset.title}
                        </span>
                        <span style={{ fontSize: '0.8rem', color: 'var(--text-secondary)', lineHeight: 1.4 }}>
                          {preset.description}
                        </span>
                      </div>
                    </label>
                  );
                })}
              </div>

              {activePreset?.needsUserId && (
                <Input
                  id={userInputId}
                  label="ID tài khoản ví cần bù / thu hồi (VD: 3 cho sender@fptpost.vn):"
                  value={targetUserId}
                  onChange={(e) => handleTargetUserIdChange(e.target.value)}
                  inputMode="numeric"
                  placeholder="Nhập ID người dùng (số)"
                  required
                />
              )}

              <Input
                id={amountInputId}
                label="Số tiền xử lý (VNĐ):"
                type="text"
                inputMode="numeric"
                value={adjustAmount}
                onChange={(e) => setAdjustAmount(e.target.value.replace(/\D/g, ''))}
                required
              />

              <Input
                id={noteInputId}
                label="Ghi chú lưu lịch sử kiểm toán:"
                value={adjustNote}
                onChange={(e) => setAdjustNote(e.target.value)}
                placeholder="Nhập lý do xử lý dòng lệch..."
                required
              />

              <details
                style={{
                  border: '1px solid var(--border-subtle)',
                  borderRadius: 'var(--radius-md)',
                  padding: '8px 12px',
                  background: 'rgba(255, 255, 255, 0.02)',
                }}
              >
                <summary style={{ cursor: 'pointer', fontSize: '0.82rem', color: 'var(--text-muted)', fontWeight: 600 }}>
                  Chi tiết tài khoản kế toán kép (Tự động điền sẵn — Nhấn nếu muốn sửa tay)
                </summary>
                <div className="ui-stack" style={{ marginTop: '10px', gap: '10px' }}>
                  <Input
                    id={debitInputId}
                    label="Tài khoản ghi Nợ (Debit — Nguồn giảm / trung gian):"
                    value={debitAccount}
                    onChange={(e) => setDebitAccount(e.target.value.toUpperCase())}
                    required
                  />
                  <Input
                    id={creditInputId}
                    label="Tài khoản ghi Có (Credit — Đích tăng / cân bằng):"
                    value={creditAccount}
                    onChange={(e) => setCreditAccount(e.target.value.toUpperCase())}
                    required
                  />
                </div>
              </details>

              <Alert tone="warning">
                <div style={{ display: 'flex', alignItems: 'center', gap: 'var(--space-2)' }}>
                  <AlertTriangle size={16} />
                  <span>
                    Hệ thống giữ nguyên lịch sử cũ và tự ghi thêm 1 bút toán cân bằng Nợ/Có theo quy tắc <strong>BR-N-039</strong>.
                  </span>
                </div>
              </Alert>

              <div className="ui-form-actions" style={{ marginTop: 'var(--space-2)' }}>
                <Button
                  type="button"
                  variant="secondary"
                  onClick={() => setAdjustingItem(null)}
                  disabled={adjustSubmitting}
                >
                  Đóng
                </Button>
                <Button
                  type="submit"
                  variant="primary"
                  loading={adjustSubmitting}
                  loadingText="Đang ghi bút toán…"
                  disabled={
                    adjustSubmitting ||
                    !debitAccount.trim() ||
                    !creditAccount.trim() ||
                    debitAccount.trim() === creditAccount.trim() ||
                    Number(adjustAmount) <= 0 ||
                    !adjustNote.trim()
                  }
                >
                  Xác nhận xử lý lệch
                </Button>
              </div>
            </form>
          );
        })()}
      </Modal>
    </div>
  );
}
