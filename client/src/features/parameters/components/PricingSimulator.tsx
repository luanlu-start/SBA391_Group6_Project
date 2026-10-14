import { useEffect, useRef, useState } from 'react';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { Calculator } from 'lucide-react';
import { parameterApi } from '../../../api/parameterApi';
import { getApiError } from '../../../api/ApiError';
import type { ApiError } from '../../../api/ApiError';
import type { PricingSimulation, SimulationQuote } from '../../../types/parameter';
import { simulationSchema } from '../schemas/simulationSchema';
import type { SimulationFormValues } from '../schemas/simulationSchema';
import { formatMoney, formatRatio } from '../parameterPresentation';
import ApiErrorNotice from '../../../components/ApiErrorNotice';
import SectionCard from '../../../components/ui/SectionCard';
import FormSection from '../../../components/ui/FormSection';
import Input from '../../../components/ui/Input';
import Select from '../../../components/ui/Select';
import Checkbox from '../../../components/ui/Checkbox';
import Button from '../../../components/ui/Button';
import Badge from '../../../components/ui/Badge';
import Alert from '../../../components/ui/Alert';
import DataTable from '../../../components/ui/DataTable';
import type { TableColumn } from '../../../components/ui/DataTable';
import LoadingSpinner from '../../../components/common/LoadingSpinner';

interface Props {
  overrides: Record<string, string>;
  sourceRevision: string;
  disabled?: boolean;
}

const defaultValues: SimulationFormValues = {
  distanceKm: '5', weightKg: '1', longestSideCm: '20', urgency: 'NORMAL', declaredValue: '500000',
  insured: false, courierTier: 'NEW', agreedFareRatio: '1', promotionDiscount: '0',
};

function toQuote(values: SimulationFormValues): SimulationQuote {
  return { ...values, distanceKm: Number(values.distanceKm), weightKg: Number(values.weightKg),
    longestSideCm: Number(values.longestSideCm), declaredValue: Number(values.declaredValue),
    agreedFareRatio: Number(values.agreedFareRatio), promotionDiscount: Number(values.promotionDiscount) };
}

interface BreakdownRow { label: string; current: string; proposed: string }

export default function PricingSimulator({ overrides, sourceRevision, disabled = false }: Props) {
  const [quote, setQuote] = useState(() => toQuote(defaultValues));
  const [result, setResult] = useState<{ current: PricingSimulation; proposed: PricingSimulation } | null>(null);
  const [loading, setLoading] = useState(false);
  const [stale, setStale] = useState(false);
  const [error, setError] = useState<ApiError | null>(null);
  const latestRequest = useRef(0);
  const { register, handleSubmit, setError: setFieldError, formState: { errors } } = useForm<SimulationFormValues>({
    resolver: zodResolver(simulationSchema), defaultValues,
  });
  const overrideSignature = JSON.stringify(Object.fromEntries(Object.entries(overrides).sort(([a], [b]) => a.localeCompare(b))));
  const hasOverrides = overrideSignature !== '{}';

  useEffect(() => {
    const request = ++latestRequest.current;
    setResult(null);
    setError(null);
    if (disabled || stale) { setLoading(false); return; }
    setLoading(true);
    const proposedOverrides = JSON.parse(overrideSignature) as Record<string, string>;
    const currentRequest = parameterApi.simulate(quote);
    const proposedRequest = hasOverrides ? parameterApi.simulate(quote, proposedOverrides) : currentRequest;
    void Promise.all([currentRequest, proposedRequest]).then(([current, proposed]) => {
      if (request === latestRequest.current) setResult({ current: current.data, proposed: proposed.data });
    }).catch((cause: unknown) => {
      if (request !== latestRequest.current) return;
      const failure = getApiError(cause);
      setError(failure);
      for (const field of Object.keys(simulationSchema.shape) as (keyof SimulationFormValues)[]) {
        const message = failure.fieldErrors[`quote.${field}`] ?? failure.fieldErrors[field];
        if (message) setFieldError(field, { type: 'server', message });
      }
    }).finally(() => {
      if (request === latestRequest.current) setLoading(false);
    });
    return () => { latestRequest.current += 1; };
  }, [quote, overrideSignature, sourceRevision, hasOverrides, setFieldError, disabled, stale]);

  const unavailable = error?.status === 503 || error?.code === 'PARAMETER_SIMULATION_UNAVAILABLE';
  const versionsChanged = result && Object.entries(result.current.parameterVersions).some(
    ([key, version]) => result.proposed.parameterVersions[key] !== version,
  );
  const rows: BreakdownRow[] = [];
  if (result) {
    const moneyRows: [string, keyof PricingSimulation][] = [
      ['Phí giao cơ bản', 'baseFare'], ['Phí quãng đường', 'distanceFee'], ['Phụ phí khối lượng', 'weightFee'],
      ['Phụ phí kích thước', 'sizeFee'], ['Giá giao gợi ý', 'suggestedFare'], ['Biên giá thấp nhất', 'bidMinimum'],
      ['Biên giá cao nhất', 'bidMaximum'], ['Giá giao thỏa thuận', 'agreedFare'], ['Hoa hồng', 'commissionAmount'],
      ['Phí bảo hiểm', 'insuranceFee'], ['Trích Quỹ bảo đảm', 'guaranteeFundAmount'], ['Giảm giá', 'promotionDiscount'],
      ['Người gửi thanh toán', 'senderTotal'], ['Người giao nhận', 'courierNet'], ['Nền tảng nhận', 'platformNet'],
      ['Thuế khấu trừ', 'withholdingAmount'], ['Chênh lệch đối soát', 'balanceDifference'],
    ];
    rows.push({ label: 'Hệ số độ khẩn', current: `${result.current.urgencyMultiplier}×`, proposed: `${result.proposed.urgencyMultiplier}×` });
    rows.push({ label: 'Tỷ lệ hoa hồng', current: formatRatio(result.current.commissionRate), proposed: formatRatio(result.proposed.commissionRate) });
    rows.push(...moneyRows.map(([label, key]) => ({ label, current: formatMoney(result.current[key] as number), proposed: formatMoney(result.proposed[key] as number) })));
  }
  const columns: TableColumn<BreakdownRow>[] = [
    { key: 'label', header: 'Thành phần', render: row => row.label },
    { key: 'current', header: 'Đang áp dụng', align: 'right', render: row => row.current },
    ...(hasOverrides ? [{ key: 'proposed', header: 'Theo bản nháp', align: 'right' as const,
      render: (row: BreakdownRow) => <span className={row.current !== row.proposed ? 'parameter-changed' : undefined}>{row.proposed}</span> }] : []),
  ];

  return <SectionCard title="Mô phỏng biểu phí"
    description={hasOverrides ? 'So sánh bộ tham số đang áp dụng với toàn bộ bản nháp. Mô phỏng không lưu cấu hình.' : 'Nhập thông tin đơn để kiểm tra giá, hoa hồng và bảo hiểm.'}
    actions={result && <Badge tone={result.current.mode === 'REFERENCE' ? 'warning' : 'success'}>
      {result.current.mode === 'REFERENCE' ? 'Ước tính tham khảo' : 'Biểu phí hiện hành'}</Badge>}>
    <div className="ui-stack">
      <form className="ui-form" noValidate onChange={() => setStale(true)} onSubmit={handleSubmit(values => {
        setStale(false); setQuote(toQuote(values));
      })}>
        <FormSection title="Thông tin đơn mô phỏng" disabled={disabled}>
          <Input label="Quãng đường (km)" inputMode="decimal" required {...register('distanceKm')} error={errors.distanceKm?.message} />
          <Input label="Khối lượng (kg)" inputMode="decimal" required {...register('weightKg')} error={errors.weightKg?.message} />
          <Input label="Cạnh dài nhất (cm)" inputMode="decimal" required {...register('longestSideCm')} error={errors.longestSideCm?.message} />
          <Select label="Độ khẩn" {...register('urgency')} error={errors.urgency?.message}>
            <option value="NORMAL">Bình thường</option><option value="SAMEDAY">Trong ngày</option><option value="URGENT">Khẩn cấp</option>
          </Select>
          <Input label="Giá trị khai báo (VND)" inputMode="numeric" required {...register('declaredValue')} error={errors.declaredValue?.message} />
          <Select label="Hạng người giao" {...register('courierTier')} error={errors.courierTier?.message}>
            <option value="NEW">Tân binh</option><option value="BRONZE">Đồng</option><option value="SILVER">Bạc</option>
            <option value="GOLD">Vàng</option><option value="DIAMOND">Kim cương</option>
          </Select>
          <Input label="Giá thỏa thuận / giá gợi ý" inputMode="decimal" required hint="1 tương ứng 100% giá gợi ý."
            {...register('agreedFareRatio')} error={errors.agreedFareRatio?.message} />
          <Input label="Giảm giá (VND)" inputMode="numeric" required {...register('promotionDiscount')} error={errors.promotionDiscount?.message} />
          <Checkbox label="Mua bảo hiểm cho hàng hóa" {...register('insured')} error={errors.insured?.message} />
        </FormSection>
        <div><Button type="submit" icon={<Calculator size={17} />} loading={loading} disabled={disabled}
          loadingText="Đang tính…">Tính lại biểu phí</Button></div>
      </form>
      {stale && <Alert tone="info">Thông tin đơn đã thay đổi. Bấm “Tính lại biểu phí” để xem kết quả mới.</Alert>}
      {!stale && loading && <LoadingSpinner text="Đang mô phỏng biểu phí…" />}
      {!stale && unavailable && <Alert tone="warning" title="Mô phỏng hiện chưa khả dụng">
        Chưa có dịch vụ tính giá trong môi trường này. Bạn vẫn có thể quản lý và áp dụng tham số.
      </Alert>}
      {!stale && error && !unavailable && <ApiErrorNotice message={error.message} correlationId={error.correlationId} />}
      {!stale && versionsChanged && <Alert tone="warning">Tham số đã thay đổi trong lúc mô phỏng. Tính lại để so sánh cùng một phiên bản.</Alert>}
      {!stale && result && !versionsChanged && <>
        <DataTable caption="Cấu thành biểu phí mô phỏng" columns={columns} rows={rows} rowKey={row => row.label} />
        {result.current.mode === 'REFERENCE' && <p className="parameter-description">Ước tính để kiểm tra thay đổi cấu hình; giá đơn thực tế cần được xác nhận khi tạo đơn.</p>}
      </>}
    </div>
  </SectionCard>;
}
