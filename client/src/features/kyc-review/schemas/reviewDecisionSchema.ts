import { z } from 'zod';
import type { KycRejectionReason, KycReviewAction } from '../../../types/kycReview';

export function reviewDecisionSchema(action: KycReviewAction, reasons: KycRejectionReason[]) {
  return z.object({
    reasonCode: z.string(),
    note: z.string().trim().max(400, 'Ghi chú tối đa 400 ký tự.'),
    flagReason: z.string().trim().max(500, 'Lý do tối đa 500 ký tự.'),
    confirmed: z.boolean(),
  }).superRefine((values, context) => {
    if (action === 'approve' && !values.confirmed) {
      context.addIssue({ code: 'custom', path: ['confirmed'], message: 'Xác nhận đã đối chiếu thông tin và tài liệu trước khi duyệt.' });
    }
    if (action === 'reject') {
      const reason = reasons.find(value => value.code === values.reasonCode);
      if (!reason) context.addIssue({ code: 'custom', path: ['reasonCode'], message: 'Chọn lý do từ chối trong danh sách.' });
      if (reason?.noteRequired && !values.note) {
        context.addIssue({ code: 'custom', path: ['note'], message: 'Nhập ghi chú cho lý do đã chọn.' });
      }
    }
    if (action === 'flag' && !values.flagReason) {
      context.addIssue({ code: 'custom', path: ['flagReason'], message: 'Nhập lý do cần quản trị viên xem xét.' });
    }
  });
}

export type ReviewDecisionValues = z.infer<ReturnType<typeof reviewDecisionSchema>>;
