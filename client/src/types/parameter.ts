export interface SystemParameter {
  prmId: string;
  key: string;
  group: string;
  name: string;
  valueType: 'INTEGER' | 'DECIMAL' | 'TEXT';
  value: string;
  unit: string | null;
  description: string | null;
  version: number;
  effectiveFrom: string;
}

export interface ParameterChange {
  value: string;
  expectedVersion: number;
}

export interface ParameterDraft extends ParameterChange {
  original: SystemParameter;
}

export interface ParameterHistory {
  id: string;
  oldValue: string;
  newValue: string;
  changedBy: string;
  changedAt: string;
  version: number;
}

export interface SimulationQuote {
  distanceKm: number;
  weightKg: number;
  longestSideCm: number;
  urgency: 'NORMAL' | 'SAMEDAY' | 'URGENT';
  declaredValue: number;
  insured: boolean;
  courierTier: 'NEW' | 'BRONZE' | 'SILVER' | 'GOLD' | 'DIAMOND';
  agreedFareRatio: number;
  promotionDiscount: number;
}

export interface PricingSimulation {
  suggestedFare: number;
  baseFare: number;
  distanceFee: number;
  weightFee: number;
  sizeFee: number;
  urgencyMultiplier: number;
  commissionRate: number;
  commissionAmount: number;
  insuranceFee: number;
  guaranteeFundAmount: number;
  courierNet: number;
  platformNet: number;
  senderTotal: number;
  agreedFare: number;
  bidMinimum: number;
  bidMaximum: number;
  promotionDiscount: number;
  withholdingAmount: number;
  balanceDifference: number;
  parameterVersions: Record<string, number>;
  mode: 'REFERENCE' | 'REAL';
}
