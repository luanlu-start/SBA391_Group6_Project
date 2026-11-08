import type { ComplaintOutcome } from '../../types/complaint';

export interface RulingDraft {
  outcome: ComplaintOutcome;
  senderPercent: string;
  compensationAmount: string;
}

export const emptyRuling: RulingDraft = { outcome: 'SENDER_FAVOR', senderPercent: '50', compensationAmount: '0' };

export function rulingPayload(draft: RulingDraft) {
  return {
    outcome: draft.outcome,
    senderPercent: draft.outcome === 'SPLIT' ? Number(draft.senderPercent) : undefined,
    compensationAmount: draft.outcome === 'COURIER_FAVOR' ? 0 : Number(draft.compensationAmount || 0),
  };
}
