import { useEffect, useState } from 'react';

export function useCountdown(until: number) {
  const [now, setNow] = useState(Date.now());
  useEffect(() => {
    setNow(Date.now());
    if (until <= Date.now()) return;
    const timer = window.setInterval(() => { setNow(Date.now()); if (Date.now() >= until) window.clearInterval(timer); }, 1000);
    return () => window.clearInterval(timer);
  }, [until]);
  return Math.max(0, Math.ceil((until - now) / 1000));
}
