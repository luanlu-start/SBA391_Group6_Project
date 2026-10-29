import { useEffect, useRef, useState } from 'react';
import { Eye, RefreshCw, X } from 'lucide-react';
import { kycReviewApi } from '../../../api/kycReviewApi';
import { getApiError } from '../../../api/ApiError';
import type { ApiError } from '../../../api/ApiError';
import type { KycReviewFile } from '../../../types/kycReview';
import { kycReviewFileLabels } from '../kycReviewPresentation';
import ApiErrorNotice from '../../../components/ApiErrorNotice';
import LoadingSpinner from '../../../components/common/LoadingSpinner';
import Button from '../../../components/ui/Button';

interface Props {
  requestId: string;
  applicantName: string;
  files: KycReviewFile[];
  disabled: boolean;
  onLoadingChange: (loading: boolean) => void;
}

export default function KycReviewImages({ requestId, applicantName, files, disabled, onLoadingChange }: Props) {
  const [preview, setPreview] = useState<{ file: KycReviewFile; url: string } | null>(null);
  const [selectedFile, setSelectedFile] = useState<KycReviewFile | null>(null);
  const [loading, setLoading] = useState(false);
  const [failure, setFailure] = useState<ApiError | null>(null);
  const objectUrl = useRef<string | null>(null);
  const request = useRef<AbortController | null>(null);
  const sequence = useRef(0);
  const mounted = useRef(true);

  useEffect(() => {
    mounted.current = true;
    return () => {
      mounted.current = false; sequence.current += 1; request.current?.abort();
      if (objectUrl.current) URL.revokeObjectURL(objectUrl.current);
    };
  }, []);

  useEffect(() => { onLoadingChange(loading); }, [loading, onLoadingChange]);

  function clearPreview() {
    if (objectUrl.current) { URL.revokeObjectURL(objectUrl.current); objectUrl.current = null; }
    setPreview(null);
  }

  function closeImage() {
    sequence.current += 1; request.current?.abort(); clearPreview();
    setSelectedFile(null); setFailure(null); setLoading(false);
  }

  async function viewImage(file: KycReviewFile) {
    if (disabled || loading) return;
    const current = ++sequence.current;
    request.current?.abort();
    const controller = new AbortController(); request.current = controller;
    clearPreview(); setSelectedFile(file); setFailure(null); setLoading(true);
    try {
      const blob = await kycReviewApi.image(requestId, file.id, controller.signal);
      if (!mounted.current || current !== sequence.current || controller.signal.aborted) return;
      const url = URL.createObjectURL(blob); objectUrl.current = url;
      setPreview({ file, url });
    } catch (cause) {
      if (mounted.current && current === sequence.current && !controller.signal.aborted) setFailure(getApiError(cause));
    } finally {
      if (mounted.current && current === sequence.current && !controller.signal.aborted) setLoading(false);
    }
  }

  return <section className="kyc-review-images ui-stack" aria-labelledby="kyc-review-images-title">
    <div><h3 id="kyc-review-images-title">Ảnh đối chiếu</h3><p className="kyc-review-muted">Chọn ảnh để xem tài liệu đã nộp.</p></div>
    {files.length === 0 ? <p>Hồ sơ chưa có ảnh đối chiếu.</p> : <ul className="kyc-review-file-list">
      {files.map(file => <li key={file.id}>
        <div><strong>{kycReviewFileLabels[file.type] ?? 'Tài liệu xác minh'}</strong>
          <small>{new Intl.NumberFormat('vi-VN', { maximumFractionDigits: 1 }).format(file.sizeBytes / 1024)} KB</small></div>
        <Button variant="secondary" size="sm" disabled={disabled || loading} icon={<Eye size={16} />}
          aria-label={`Xem ${kycReviewFileLabels[file.type] ?? 'tài liệu xác minh'}`} onClick={() => void viewImage(file)}>Xem ảnh</Button>
      </li>)}
    </ul>}
    {selectedFile && <div className="kyc-review-image-viewer ui-stack" aria-busy={loading || undefined}>
      <div className="kyc-review-image-heading"><h4>{kycReviewFileLabels[selectedFile.type] ?? 'Tài liệu xác minh'}</h4>
        <Button variant="ghost" size="sm" disabled={disabled} icon={<X size={16} />} onClick={closeImage}>Đóng ảnh</Button></div>
      {loading && <LoadingSpinner text="Đang tải ảnh đối chiếu…" />}
      {failure && <div className="ui-stack"><ApiErrorNotice message={failure.message} correlationId={failure.correlationId} />
        <p className="kyc-review-muted">Nếu liên kết đã hết hạn, tải lại để yêu cầu liên kết mới.</p>
        <div><Button variant="secondary" size="sm" disabled={disabled} icon={<RefreshCw size={15} />}
          onClick={() => void viewImage(selectedFile)}>Tải lại ảnh</Button></div></div>}
      {preview && <img src={preview.url} alt={`${kycReviewFileLabels[preview.file.type] ?? 'Tài liệu xác minh'} của ${applicantName}`} />}
    </div>}
  </section>;
}
