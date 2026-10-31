export type NotificationCategory = 'KYC' | 'SECURITY' | 'PAYMENT' | 'DISPUTE' | 'ORDER' | 'PROMOTION' | 'RECOMMENDATION';
export type NotificationChannel = 'IN_APP' | 'EMAIL' | 'SMS';

export interface Notification {
  id: string;
  category: NotificationCategory;
  title: string;
  body: string;
  relativeLink: string | null;
  at: string;
  readAt: string | null;
}

export interface NotificationSetting {
  category: NotificationCategory;
  mandatory: boolean;
  channels: Record<NotificationChannel, boolean>;
}

export interface NotificationSettings {
  settings: NotificationSetting[];
  smsAvailable: boolean;
  webPushAvailable: boolean;
}

export const notificationCategoryLabels: Record<NotificationCategory, string> = {
  KYC: 'Xác minh danh tính',
  SECURITY: 'Bảo mật tài khoản',
  PAYMENT: 'Ví và thanh toán',
  DISPUTE: 'Khiếu nại và bồi thường',
  ORDER: 'Trạng thái đơn hàng',
  PROMOTION: 'Khuyến mãi',
  RECOMMENDATION: 'Gợi ý phù hợp',
};

export function safeNotificationLink(link: string | null): string | null {
  if (typeof link !== 'string' || !link || !/^\/(?!\/)/.test(link) || [...link].some(character => character === '\\' || character.charCodeAt(0) <= 32)) return null;
  try {
    const parsed = new URL(link, window.location.origin);
    return parsed.origin === window.location.origin ? parsed.pathname + parsed.search + parsed.hash : null;
  } catch { return null; }
}
