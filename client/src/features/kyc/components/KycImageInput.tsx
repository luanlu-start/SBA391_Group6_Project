import { useEffect, useState } from 'react';
import Input from '../../../components/ui/Input';

export default function KycImageInput({ label, hint, value, onChange, error, disabled }: {
  label: string; hint: string; value?: File; onChange: (file?: File) => void; error?: string; disabled: boolean;
}) {
  const [preview, setPreview] = useState('');
  useEffect(() => {
    if (!value) { setPreview(''); return; }
    const url = URL.createObjectURL(value); setPreview(url);
    return () => URL.revokeObjectURL(url);
  }, [value]);
  return <div className="kyc-upload"><Input label={label} required hint={hint} type="file" accept="image/jpeg,image/png,image/webp"
    disabled={disabled} error={error} onChange={event => onChange(event.target.files?.[0])} />
    {preview && <img className="kyc-image-preview" src={preview} alt={`Ảnh đã chọn: ${label}`} />}</div>;
}
