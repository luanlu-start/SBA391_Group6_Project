export function describeDevice(userAgent: string) {
  const browser = /Edg(?:e|A|iOS)?\//.test(userAgent) ? 'Edge'
    : /(?:OPR|Opera)\//.test(userAgent) ? 'Opera'
    : /(?:Firefox|FxiOS)\//.test(userAgent) ? 'Firefox'
    : /(?:Chrome|CriOS)\//.test(userAgent) ? 'Chrome'
    : /Safari\//.test(userAgent) ? 'Safari' : '';
  const platform = /iPad/.test(userAgent) ? 'iPad'
    : /iPhone/.test(userAgent) ? 'iPhone'
    : /Android/.test(userAgent) ? 'Android'
    : /Windows/.test(userAgent) ? 'Windows'
    : /Macintosh|Mac OS X/.test(userAgent) ? 'macOS'
    : /Linux/.test(userAgent) ? 'Linux' : '';
  return {
    name: browser && platform ? `${browser} trên ${platform}` : browser || platform || 'Thiết bị không xác định',
    mobile: /Android|iPhone|iPad/.test(userAgent),
  };
}

const dateFormat = new Intl.DateTimeFormat('vi-VN', { dateStyle: 'short', timeStyle: 'short' });
export function formatSessionTime(value: string) {
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? 'Không rõ' : dateFormat.format(date);
}
