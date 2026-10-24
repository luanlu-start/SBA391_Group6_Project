import { useState } from 'react';
import { Link } from 'react-router-dom';
import { ArrowDown, ArrowRight, Check, CircleCheck, Handshake, MapPin, Package,
  Route, ShieldCheck, Truck, Wallet } from 'lucide-react';
import { useAuthentication } from '../auth/useAuthentication';
import Select from '../components/ui/Select';
import JourneyIllustration from '../features/home/components/JourneyIllustration';
import copy from '../i18n/home.vi.json';
import '../styles/home.css';

const benefitIcons = [Handshake, ShieldCheck, Route];
const stepIcons = [Package, Handshake, CircleCheck];
const numberFormat = new Intl.NumberFormat('vi-VN');

export default function HomePage() {
  const { user } = useAuthentication();
  const [exampleId, setExampleId] = useState(copy.pricing.examples[0].id);
  const example = copy.pricing.examples.find(item => item.id === exampleId) ?? copy.pricing.examples[0];
  const accountPath = user ? '/account' : '/register';

  return <div className="fpost-home" id="home-top">
    <section className="home-hero" aria-labelledby="home-title">
      <div className="home-hero-copy">
        <p className="home-eyebrow"><span />{copy.hero.eyebrow}</p>
        <h1 id="home-title">{copy.hero.title}<span>{copy.hero.accent}</span></h1>
        <p className="home-hero-description">{copy.hero.description}</p>
        <div className="home-hero-actions">
          <Link to={accountPath} className="ui-button ui-button-primary">{user ? copy.hero.account : copy.hero.primary}<ArrowRight size={18} aria-hidden="true" /></Link>
          <a href="#how-it-works" className="home-text-action">{copy.hero.secondary}<ArrowDown size={16} aria-hidden="true" /></a>
        </div>
        <p className="home-hero-note"><Check size={15} aria-hidden="true" />{copy.hero.note}</p>
      </div>
      <JourneyIllustration />
    </section>

    <section className="home-benefits" aria-label={copy.how.description}>
      {copy.benefits.map((benefit, index) => {
        const Icon = benefitIcons[index];
        return <article key={benefit.title}><span className="home-feature-icon"><Icon size={23} aria-hidden="true" /></span>
          <div><h2>{benefit.title}</h2><p>{benefit.description}</p></div></article>;
      })}
    </section>

    <section className="home-section" id="how-it-works" aria-labelledby="home-how-title">
      <header className="home-section-header"><p className="home-eyebrow">{copy.how.eyebrow}</p>
        <h2 id="home-how-title">{copy.how.title}</h2><p>{copy.how.description}</p></header>
      <div className="home-steps">{copy.how.steps.map((step, index) => {
        const Icon = stepIcons[index];
        return <article key={step.title}><div className="home-step-top"><span className="home-step-number">0{index + 1}</span><Icon size={24} aria-hidden="true" /></div>
          <h3>{step.title}</h3><p>{step.description}</p></article>;
      })}</div>
    </section>

    <section className="home-pricing home-section" id="shipping-prices" aria-labelledby="home-pricing-title">
      <div className="home-pricing-copy"><p className="home-eyebrow">{copy.pricing.eyebrow}</p>
        <h2 id="home-pricing-title">{copy.pricing.title}</h2><p>{copy.pricing.description}</p>
        <ul>{copy.pricing.factors.map(factor => <li key={factor}><Check size={17} aria-hidden="true" />{factor}</li>)}</ul>
      </div>
      <div className="home-price-card">
        {/* Reference fixtures: source workbook, 07_Biểu_phí_Mô_phỏng, rows 61–63. No live pricing calculation. */}
        <Select label={copy.pricing.exampleLabel} value={exampleId} onChange={event => setExampleId(event.target.value)}>
          {copy.pricing.examples.map(item => <option key={item.id} value={item.id}>{item.label}</option>)}
        </Select>
        <dl className="home-price-details">
          <div><dt>{copy.pricing.distance}</dt><dd>{numberFormat.format(example.distanceKm)} km</dd></div>
          <div><dt>{copy.pricing.weight}</dt><dd>{numberFormat.format(example.weightKg)} kg</dd></div>
          <div><dt>{copy.pricing.size}</dt><dd>{numberFormat.format(example.longestSideCm)} cm</dd></div>
          <div><dt>{copy.pricing.urgency}</dt><dd>{copy.pricing.standard}</dd></div>
        </dl>
        <div className="home-price-total" aria-live="polite" aria-atomic="true"><span>{copy.pricing.priceLabel}</span>
          <strong>{numberFormat.format(example.feeVnd)}<small>{copy.pricing.unit}</small></strong></div>
        <p className="home-price-note">{copy.pricing.note}</p>
      </div>
    </section>

    <section className="home-courier" id="become-courier" aria-labelledby="home-courier-title">
      <div><p className="home-eyebrow">{copy.courier.eyebrow}</p><h2 id="home-courier-title">{copy.courier.title}</h2>
        <p className="home-courier-description">{copy.courier.description}</p>
        <Link to={accountPath} className="ui-button ui-button-primary">{user ? copy.courier.accountAction : copy.courier.action}<ArrowRight size={18} aria-hidden="true" /></Link>
      </div>
      <div className="home-courier-side"><span className="home-courier-symbol"><Truck size={55} strokeWidth={1.4} aria-hidden="true" /></span>
        <ul>{copy.courier.points.map((point, index) => {
          const Icon = [MapPin, Handshake, Wallet][index];
          return <li key={point}><Icon size={20} aria-hidden="true" />{point}</li>;
        })}</ul>
      </div>
    </section>
  </div>;
}
