import { useEffect, useId, useRef, useState } from 'react';
import { X } from 'lucide-react';
import { promotionApi } from '../../../api/promotionApi';
import type { PromotionTargetOption, PromotionTargetOptions } from '../../../types/promotion';
import Input from '../../../components/ui/Input';
import Button from '../../../components/ui/Button';

interface Props {
  value: string[];
  onChange: (value: string[]) => void;
  initialOptions: PromotionTargetOption[];
  error?: string;
  disabled: boolean;
}

export default function PromotionTargetPicker({ value, onChange, initialOptions, error, disabled }: Props) {
  const id = useId();
  const known = useRef(new Map(initialOptions.map(option => [option.value, option])));
  const [search, setSearch] = useState('');
  const [result, setResult] = useState<PromotionTargetOptions | null>(null);
  const [loading, setLoading] = useState(true);
  const [failed, setFailed] = useState(false);
  const [retry, setRetry] = useState(0);
  const sequence = useRef(0);

  useEffect(() => {
    const request = ++sequence.current;
    const controller = new AbortController();
    setLoading(true); setFailed(false); setResult(null);
    const timer = window.setTimeout(() => {
      void (async () => {
        try {
          const response = await promotionApi.targetOptions(search.trim(), controller.signal);
          if (request === sequence.current) setResult(response.data);
        } catch {
          if (request === sequence.current && !controller.signal.aborted) setFailed(true);
        } finally {
          if (request === sequence.current) setLoading(false);
        }
      })();
    }, search.trim() ? 250 : 0);
    return () => { sequence.current += 1; window.clearTimeout(timer); controller.abort(); };
  }, [search, retry]);

  function toggle(option: PromotionTargetOption) {
    if (disabled) return;
    known.current.set(option.value, option);
    if (value.includes(option.value)) onChange(value.filter(id => id !== option.value));
    else if (value.length < 100) onChange([...value, option.value]);
  }

  return <div className="promotion-target-picker ui-stack">
    <div className="promotion-target-heading"><p className="ui-field-label">Người được áp dụng ({value.length}/100)</p>
      {!!value.length && <Button variant="ghost" size="sm" disabled={disabled} onClick={() => onChange([])}>Bỏ chọn tất cả</Button>}
    </div>
    {!!value.length && <ul className="promotion-target-chips" aria-label="Người dùng đã chọn">
      {value.map(userId => {
        const option = known.current.get(userId) ?? { value: userId, label: 'Người dùng đã chọn', description: userId };
        return <li key={userId}><span><strong>{option.label}</strong>{option.description && <small>{option.description}</small>}</span>
          <Button variant="ghost" size="sm" icon={<X size={14} />} disabled={disabled}
            aria-label={`Bỏ người dùng ${option.label}${option.description ? ` (${option.description})` : ''}`}
            onClick={() => onChange(value.filter(id => id !== userId))} />
        </li>;
      })}
    </ul>}
    <Input id={id} label="Tìm người áp dụng" placeholder="Tìm theo tên hoặc email…" maxLength={100}
      value={search} disabled={disabled} onChange={event => setSearch(event.target.value)}
      hint="Tìm rồi đánh dấu người dùng. Nhập từ khóa không làm thay đổi danh sách đã chọn." error={error} />
    <div className="promotion-target-results" aria-busy={loading || undefined}>
      {loading ? <p className="promotion-muted" role="status">Đang tải người dùng…</p>
        : failed ? <div className="ui-stack"><p role="alert">Chưa thể tải danh sách người dùng. Các lựa chọn hiện tại được giữ.</p>
          <div><Button variant="secondary" size="sm" disabled={disabled} onClick={() => setRetry(previous => previous + 1)}>Thử lại</Button></div>
        </div> : <>
          <ul className="promotion-target-options" aria-label="Người dùng có thể chọn">
            {(result?.options ?? []).map(option => <li key={option.value}>
              <label><input type="checkbox" checked={value.includes(option.value)}
                disabled={disabled || (value.length >= 100 && !value.includes(option.value))} onChange={() => toggle(option)} />
                <span><strong>{option.label}</strong>{option.description && <small>{option.description}</small>}</span>
              </label>
            </li>)}
          </ul>
          {!result?.options.length && <p className="promotion-muted" role="status">Không có người dùng phù hợp.</p>}
          {result?.hasMore && <p className="promotion-muted" role="status">Còn người dùng khác. Nhập thêm từ khóa để thu hẹp danh sách.</p>}
        </>}
    </div>
    {value.length >= 100 && <p className="promotion-muted" role="status">Đã chọn đủ 100 người dùng. Bỏ một lựa chọn trước khi thêm người khác.</p>}
  </div>;
}
