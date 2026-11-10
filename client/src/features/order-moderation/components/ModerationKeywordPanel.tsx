import { useCallback, useEffect, useState } from 'react';
import type { FormEvent } from 'react';
import { Plus, X } from 'lucide-react';
import { orderModerationApi } from '../../../api/orderModerationApi';
import { getApiError } from '../../../api/ApiError';
import type { ApiError } from '../../../api/ApiError';
import type { ModerationKeyword, ModerationKeywordLevel } from '../../../types/orderModeration';
import SectionCard from '../../../components/ui/SectionCard';
import Input from '../../../components/ui/Input';
import Select from '../../../components/ui/Select';
import Button from '../../../components/ui/Button';
import ApiErrorNotice from '../../../components/ApiErrorNotice';

const levelLabels: Record<ModerationKeywordLevel, string> = { PROHIBITED: 'Hàng cấm', SUSPICIOUS: 'Nghi ngờ' };

export default function ModerationKeywordPanel() {
  const [items, setItems] = useState<ModerationKeyword[]>([]);
  const [term, setTerm] = useState('');
  const [level, setLevel] = useState<ModerationKeywordLevel>('PROHIBITED');
  const [error, setError] = useState<ApiError | null>(null);
  const [saving, setSaving] = useState(false);

  const load = useCallback(() => {
    void orderModerationApi.keywords().then(response => setItems(response.data)).catch(cause => setError(getApiError(cause)));
  }, []);

  useEffect(load, [load]);

  async function add(event: FormEvent) {
    event.preventDefault();
    setSaving(true);
    setError(null);
    try {
      await orderModerationApi.addKeyword(term.trim(), level);
      setTerm('');
      load();
    } catch (cause) {
      setError(getApiError(cause));
    } finally {
      setSaving(false);
    }
  }

  async function remove(keyword: ModerationKeyword) {
    setError(null);
    try {
      await orderModerationApi.removeKeyword(keyword.id);
      setItems(previous => previous.filter(item => item.id !== keyword.id));
    } catch (cause) {
      setError(getApiError(cause));
    }
  }

  return <SectionCard title="Từ khóa kiểm duyệt" description="Đơn có tên, mô tả hoặc ghi chú khớp nguyên từ sẽ vào hàng đợi. Cụm nhiều từ còn khớp khi người gửi bỏ dấu.">
    <form className="order-moderation-keyword-form" onSubmit={add} noValidate>
      <Input label="Từ khóa" maxLength={100} value={term} placeholder="Ví dụ: bình oxy" onChange={event => setTerm(event.target.value)} />
      <Select label="Mức" value={level} onChange={event => setLevel(event.target.value as ModerationKeywordLevel)}>
        <option value="PROHIBITED">Hàng cấm</option>
        <option value="SUSPICIOUS">Nghi ngờ</option>
      </Select>
      <Button type="submit" icon={<Plus size={16} />} loading={saving} disabled={term.trim().length < 2}>Thêm</Button>
    </form>
    {error && <ApiErrorNotice message={error.message} correlationId={error.correlationId} />}
    <ul className="order-moderation-keywords" aria-label="Danh sách từ khóa">
      {items.map(item => <li key={item.id} className={item.level === 'PROHIBITED' ? 'is-prohibited' : undefined}>
        <span>{item.term}</span>
        <small>{levelLabels[item.level]}</small>
        <button type="button" aria-label={`Xóa từ khóa ${item.term}`} onClick={() => void remove(item)}><X size={14} /></button>
      </li>)}
    </ul>
  </SectionCard>;
}
