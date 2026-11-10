import { ArrowUpRight, MapPin, Navigation, Package } from 'lucide-react';
import copy from '../../../i18n/home.vi.json';

export default function JourneyIllustration() {
  return <figure className="home-journey" aria-label={copy.hero.visualLabel}>
    <div className="home-journey-top"><span><Navigation size={14} aria-hidden="true" />FPost</span>
      <span>{copy.hero.illustration}</span></div>
    <svg className="home-journey-map" viewBox="0 0 560 410" aria-hidden="true">
      <defs>
        <pattern id="journey-grid" width="42" height="42" patternUnits="userSpaceOnUse" patternTransform="rotate(-25)">
          <path d="M 42 0 L 0 0 0 42" fill="none" stroke="currentColor" strokeWidth=".8" />
        </pattern>
        <linearGradient id="journey-road" x1="0" y1="0" x2="1" y2="1">
          <stop stopColor="#333d4c" /><stop offset="1" stopColor="#242d39" />
        </linearGradient>
      </defs>
      <rect width="560" height="410" fill="url(#journey-grid)" opacity=".35" />
      <g fill="none" stroke="url(#journey-road)" strokeWidth="19" strokeLinejoin="round">
        <path d="M-25 270 170 185 330 272 580 157" />
        <path d="M67 430 172 343 100 291 347 113 501 12" />
        <path d="M-30 122 180 38 392 149 592 85" />
        <path d="m411 428-90-61 218-152" />
      </g>
      <g stroke="#4d5868" strokeWidth="1" fill="#232c38">
        <path d="m124 107 57-25 45 25-55 27Z" /><path d="m124 107 47 27v31l-47-27Z" /><path d="m171 134 55-27v31l-55 27Z" fill="#1a212b" />
        <path d="m367 78 49-22 48 26-49 23Z" /><path d="m367 78 48 27v41l-48-26Z" /><path d="m415 105 49-23v41l-49 23Z" fill="#1a212b" />
        <path d="m392 296 48-23 61 34-49 23Z" /><path d="m392 296 60 34v22l-60-33Z" /><path d="m452 330 49-23v22l-49 23Z" fill="#1a212b" />
      </g>
      <path d="M113 270c36-95 122-50 170-68s62-96 151-78" fill="none" stroke="var(--primary)" strokeWidth="4" strokeDasharray="7 9" strokeLinecap="round" />
      <circle cx="113" cy="270" r="21" fill="var(--primary)" opacity=".12" />
      <circle cx="113" cy="270" r="8" fill="var(--primary)" /><circle cx="113" cy="270" r="3" fill="#fff" />
      <circle cx="434" cy="124" r="21" fill="var(--accent-emerald)" opacity=".12" />
      <circle cx="434" cy="124" r="8" fill="var(--accent-emerald)" /><circle cx="434" cy="124" r="3" fill="#fff" />
      <g stroke="#ffb17d" strokeWidth="1.5" strokeLinejoin="round">
        <path d="m216 233 64-35 76 42-63 36Z" fill="#ff9a54" />
        <path d="m216 233 77 43v80l-77-43Z" fill="#e6671b" />
        <path d="m293 276 63-36v79l-63 37Z" fill="#b54b12" />
        <path d="m245 217 76 42 17-9-76-42Z" fill="#ffe1bd" stroke="none" />
        <path d="m321 259 17-9v29l-17 10Z" fill="#eac7a1" stroke="none" />
      </g>
      <path d="m239 277 27 16v20l-27-15Z" fill="#ffe1bd" stroke="none" />
      <path d="m244 284 17 10m-17-3 10 6" stroke="#86401e" strokeWidth="2" />
    </svg>
    <div className="home-route-label home-route-pickup"><span><Package size={16} aria-hidden="true" /></span>{copy.hero.pickup}</div>
    <div className="home-route-label home-route-destination"><span><MapPin size={16} aria-hidden="true" /></span>{copy.hero.destination}</div>
    <figcaption className="home-journey-caption"><span className="home-journey-icon"><Navigation size={20} aria-hidden="true" /></span>
      <div><strong>{copy.hero.visualCaption}</strong><p>{copy.hero.route}</p></div><ArrowUpRight size={20} aria-hidden="true" />
    </figcaption>
  </figure>;
}
