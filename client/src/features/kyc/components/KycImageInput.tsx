import { useEffect, useId, useRef, useState } from 'react';
import { CreditCard, ImagePlus, ScanFace, Trash2 } from 'lucide-react';
import Button from '../../../components/ui/Button';

export default function KycImageInput({ label, hint, kind, value, onChange, error, disabled }: {
  label: string; hint: string; kind: 'front' | 'back' | 'portrait'; value?: File;
  onChange: (file?: File) => void; error?: string; disabled: boolean;
}) {
  const input = useRef<HTMLInputElement>(null);
  const id = useId();
  const [preview, setPreview] = useState('');
  useEffect(() => {
    if (!value) { setPreview(''); return; }
    const url = URL.createObjectURL(value);
    setPreview(url);
    return () => URL.revokeObjectURL(url);
  }, [value]);
  const Icon = kind === 'portrait' ? ScanFace : CreditCard;
  const description = `${id}-hint${error ? ` ${id}-error` : ''}`;

  return <div className={`kyc-upload${error ? ' kyc-upload-error' : ''}`}>
    <h4>{label}<span aria-hidden="true"> *</span></h4>
    <input ref={input} hidden type="file" aria-label={label} accept="image/jpeg,image/png,image/webp" disabled={disabled}
      onChange={event => { const file = event.target.files?.[0]; if (file) onChange(file); event.target.value = ''; }} />
    <button type="button" className="kyc-upload-target" disabled={disabled} onClick={() => input.current?.click()}
      aria-label={`${value ? 'Đổi' : 'Chọn'} ảnh ${label.toLowerCase()}`} aria-describedby={description}>
      {preview ? <img className="kyc-image-preview" src={preview} alt={`Ảnh đã chọn: ${label}`} /> : <>
        <Icon size={42} strokeWidth={1.3} aria-hidden="true" />
        <span><ImagePlus size={15} aria-hidden="true" />Chọn ảnh từ thiết bị</span>
      </>}
    </button>
    <p id={`${id}-hint`} className="kyc-upload-hint">{hint}</p>
    {value && <div className="kyc-selected-file"><span title={value.name}>{value.name}</span>
      <Button variant="ghost" size="sm" aria-label={`Xóa ảnh ${label.toLowerCase()}`} disabled={disabled}
        onClick={() => onChange(undefined)} icon={<Trash2 size={15} />} />
    </div>}
    {error && <p id={`${id}-error`} className="ui-field-error" role="alert">{error}</p>}
  </div>;
}
