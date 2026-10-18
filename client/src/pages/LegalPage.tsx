import { useEffect, useState } from 'react';
import { Link, useLocation } from 'react-router-dom';
import { authService } from '../services/authService';
import { getApiError } from '../api/ApiError';
import type { ApiError } from '../api/ApiError';
import type { RegistrationPolicies } from '../types/identity';
import ApiErrorNotice from '../components/ApiErrorNotice';
import Button from '../components/ui/Button';
import PageHeader from '../components/ui/PageHeader';
import copy from '../i18n/home.vi.json';

export default function LegalPage() {
  const { hash } = useLocation();
  const [policies, setPolicies] = useState<RegistrationPolicies | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<ApiError>();
  const [retry, setRetry] = useState(0);

  useEffect(() => {
    let active = true;
    setLoading(true);
    setError(undefined);
    authService.getRegistrationPolicies().then(response => {
      if (active) setPolicies(response.data);
    }).catch(cause => {
      if (active) setError(getApiError(cause));
    }).finally(() => { if (active) setLoading(false); });
    return () => { active = false; };
  }, [retry]);

  useEffect(() => {
    if (!policies || !hash) return;
    const frame = requestAnimationFrame(() => document.getElementById(hash.slice(1))?.scrollIntoView());
    return () => cancelAnimationFrame(frame);
  }, [policies, hash]);

  return <div className="legal-page">
    <PageHeader title={copy.footer.legalTitle} description={copy.footer.legalDescription} />
    <nav className="legal-navigation" aria-label={copy.footer.policyNavigation}>
      <Link to="/legal#terms">{copy.footer.terms}</Link><Link to="/legal#privacy">{copy.footer.privacy}</Link>
    </nav>
    {loading ? <div className="legal-loading" role="status">{copy.footer.loadingPolicies}</div>
      : error ? <div className="ui-stack"><ApiErrorNotice error={error} />
        <div><Button variant="secondary" onClick={() => setRetry(value => value + 1)}>{copy.footer.retry}</Button></div></div>
        : policies && (['terms', 'privacy'] as const).map(key => <section key={key} id={key} className="legal-policy" aria-labelledby={`legal-${key}-title`}>
          <h2 id={`legal-${key}-title`}>{policies[key].title}</h2>
          <p className="legal-policy-version">{copy.footer.version}: {policies[key].version}</p>
          <div className="legal-policy-content">{policies[key].paragraphs.map((paragraph, index) => <p key={index}>{paragraph}</p>)}</div>
        </section>)}
  </div>;
}
