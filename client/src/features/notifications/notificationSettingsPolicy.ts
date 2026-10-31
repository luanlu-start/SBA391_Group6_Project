import type { NotificationChannel, NotificationSetting } from '../../types/notification';

export function canChangeNotificationChannel(setting: NotificationSetting, channel: NotificationChannel, smsAvailable: boolean): boolean {
  return !(setting.mandatory && channel !== 'SMS')
    && (channel !== 'SMS' || smsAvailable || setting.channels.SMS);
}
