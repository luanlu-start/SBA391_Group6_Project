import { Link } from 'react-router-dom';
import { Mail, MapPin, Package, Phone } from 'lucide-react';
import copy from '../../i18n/home.vi.json';
import '../../styles/footer.css';

export default function Footer() {
  return <footer className="site-footer">
    <div className="site-footer-inner">
      <div className="site-footer-grid">
        <div className="site-footer-intro">
          <Link to="/" className="site-footer-brand" aria-label={`FPost — ${copy.navigation.home}`}>
            <Package size={31} strokeWidth={1.6} aria-hidden="true" /><span>F<span>Post</span></span>
          </Link>
          <p>{copy.footer.description}</p>
        </div>
        <nav className="site-footer-column" aria-labelledby="footer-quick-links">
          <h2 id="footer-quick-links">{copy.footer.quickLinks}</h2>
          <ul>
            <li><Link to="/">{copy.navigation.home}</Link></li>
            <li><Link to="/#how-it-works">{copy.navigation.how}</Link></li>
            <li><Link to="/#shipping-prices">{copy.navigation.pricing}</Link></li>
            <li><Link to="/#become-courier">{copy.navigation.courier}</Link></li>
          </ul>
        </nav>
        <nav className="site-footer-column" aria-labelledby="footer-policies">
          <h2 id="footer-policies">{copy.footer.policies}</h2>
          <ul>
            <li><Link to="/legal#terms">{copy.footer.terms}</Link></li>
            <li><Link to="/legal#privacy">{copy.footer.privacy}</Link></li>
          </ul>
        </nav>
        <section className="site-footer-column site-footer-contact" aria-labelledby="footer-contact">
          <h2 id="footer-contact">{copy.footer.contactTitle}</h2>
          <address>
            <a href={copy.footer.contact.mapUrl} target="_blank" rel="noopener noreferrer"><MapPin size={18} aria-hidden="true" /><span>{copy.footer.contact.address}</span></a>
            <a href={`tel:${copy.footer.contact.phone}`}><Phone size={18} aria-hidden="true" /><span>{copy.footer.contact.phoneDisplay}</span></a>
            <a href={`mailto:${copy.footer.contact.email}`}><Mail size={18} aria-hidden="true" /><span>{copy.footer.contact.email}</span></a>
          </address>
        </section>
      </div>
      <div className="site-footer-bottom"><p>© {new Date().getFullYear()} FPost. {copy.footer.rights}</p><p>{copy.footer.tagline}</p></div>
    </div>
  </footer>;
}
