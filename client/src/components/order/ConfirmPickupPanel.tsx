import { useState, useRef, useEffect } from 'react';
import { Camera, CheckCircle2, KeyRound, QrCode, UploadCloud, X } from 'lucide-react';
import { orderApi } from '../../api/orderApi';
import Button from '../ui/Button';
import Alert from '../ui/Alert';

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
  const [previewUrl, setPreviewUrl] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');
  const fileInputRef = useRef<HTMLInputElement>(null);

  useEffect(() => {
    return () => {
      if (previewUrl) {
        URL.revokeObjectURL(previewUrl);
      }
    };
  }, [previewUrl]);

  function handlePhotoChange(file: File | null) {
    if (previewUrl) {
      URL.revokeObjectURL(previewUrl);
    }
    setPhoto(file);
    if (file) {
      setPreviewUrl(URL.createObjectURL(file));
      setError('');
    } else {
      setPreviewUrl(null);
    }
  }

  function clearPhoto() {
    handlePhotoChange(null);
    if (fileInputRef.current) {
      fileInputRef.current.value = '';
    }
  }

  async function confirm() {
    if (!qrToken.trim()) {
      setError('Vui lòng nhập mã xác nhận từ người gửi.');
      return;
    }
    if (!photo) {
      setError('Vui lòng tải lên ảnh bằng chứng lấy hàng.');
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
        'Xác nhận thất bại. Kiểm tra lại mã xác nhận, trạng thái đơn và kết nối mạng.'
      );
    } finally {
      setLoading(false);
    }
  }

  return (
    <div className="pickup-box">
      <div className="pickup-box-header">
        <div className="pickup-box-title">
          <QrCode size={18} className="pickup-header-icon" />
          <span>Xác nhận lấy hàng từ người gửi (Sender)</span>
        </div>
        <p className="pickup-box-subtitle">
          Nhập mã xác nhận do người gửi cung cấp và tải ảnh chụp kiện hàng làm bằng chứng
        </p>
      </div>

      <div className="pickup-form-grid">
        {/* Field 1: Mã xác nhận */}
        <div className="pickup-field">
          <label htmlFor={`pickup-qr-${orderId}`} className="pickup-field-label">
            <KeyRound size={14} />
            <span>Mã xác nhận lấy hàng</span>
            <span className="pickup-required">*</span>
          </label>
          <div className="pickup-input-wrapper">
            <input
              id={`pickup-qr-${orderId}`}
              className="ui-control pickup-text-input"
              value={qrToken}
              onChange={event => {
                setQrToken(event.target.value);
                if (error) setError('');
              }}
              placeholder="Nhập mã xác nhận từ Sender"
              disabled={loading}
              autoComplete="off"
            />
          </div>
        </div>

        {/* Field 2: Ảnh bằng chứng */}
        <div className="pickup-field">
          <label htmlFor={`pickup-photo-${orderId}`} className="pickup-field-label">
            <Camera size={14} />
            <span>Ảnh bằng chứng lấy hàng</span>
            <span className="pickup-required">*</span>
          </label>

          <input
            ref={fileInputRef}
            id={`pickup-photo-${orderId}`}
            type="file"
            accept="image/jpeg,image/png,image/webp,image/gif"
            capture="environment"
            style={{ display: 'none' }}
            disabled={loading}
            onChange={event =>
              handlePhotoChange(event.target.files?.[0] ?? null)
            }
          />

          {!photo ? (
            <button
              type="button"
              className="pickup-upload-btn"
              disabled={loading}
              onClick={() => fileInputRef.current?.click()}
            >
              <UploadCloud size={20} className="pickup-upload-icon" />
              <div className="pickup-upload-text">
                <span className="pickup-upload-primary">Chọn ảnh hoặc chụp ảnh kiện hàng</span>
                <span className="pickup-upload-hint">JPG, PNG, WebP (Tối đa 5MB)</span>
              </div>
            </button>
          ) : (
            <div className="pickup-preview-bar">
              {previewUrl && (
                <img
                  src={previewUrl}
                  alt="Ảnh bằng chứng"
                  className="pickup-preview-thumb"
                />
              )}
              <div className="pickup-preview-info">
                <span className="pickup-preview-name" title={photo.name}>
                  {photo.name}
                </span>
                <span className="pickup-preview-size">
                  {(photo.size / 1024).toFixed(1)} KB
                </span>
              </div>
              <button
                type="button"
                className="pickup-preview-remove"
                disabled={loading}
                onClick={clearPhoto}
                title="Chọn lại ảnh"
              >
                <X size={15} />
              </button>
            </div>
          )}
        </div>
      </div>

      {error && (
        <Alert tone="danger">{error}</Alert>
      )}

      <div className="pickup-action-row">
        <Button
          type="button"
          variant="primary"
          size="sm"
          icon={<CheckCircle2 size={16} />}
          disabled={loading || !photo || !qrToken.trim()}
          loading={loading}
          onClick={() => void confirm()}
        >
          {loading ? 'Đang xác nhận...' : 'Xác nhận đã lấy hàng'}
        </Button>
      </div>
    </div>
  );
}
