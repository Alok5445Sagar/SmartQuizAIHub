import React, { useEffect, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import { useLanguage } from '../context/LanguageContext';

export default function LoginPage() {
  const [enterpriseId, setEnterpriseId] = useState('');
  const [password, setPassword] = useState('');
  const [showPassword, setShowPassword] = useState(false);
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);
  const [isMobile, setIsMobile] = useState(() => (typeof window !== 'undefined' ? window.innerWidth <= 768 : false));
  const [showDemoAccounts, setShowDemoAccounts] = useState(() => (typeof window !== 'undefined' ? window.innerWidth > 768 : true));
  const { login } = useAuth();
  const { t, language, setLanguage, availableLanguages } = useLanguage();
  const navigate = useNavigate();

  useEffect(() => {
    const onResize = () => {
      const mobile = window.innerWidth <= 768;
      setIsMobile(mobile);
      if (!mobile) {
        setShowDemoAccounts(true);
      }
    };

    window.addEventListener('resize', onResize);
    return () => window.removeEventListener('resize', onResize);
  }, []);

  const handleSubmit = async (e) => {
    e.preventDefault();
    setError('');
    setLoading(true);
    try {
      const user = await login(enterpriseId, password);
      navigate(user.role === 'ADMIN' ? '/admin/question-bank' : '/my-questions');
    } catch (err) {
      setError((err.response?.data?.detail || err.response?.data?.message) || t('invalidCredentialsTryAgain'));
    } finally {
      setLoading(false);
    }
  };

  const fillDemo = (id, pw) => { setEnterpriseId(id); setPassword(pw); };


  return (
    <div className="login-page" style={styles.page}>
      <div className="login-left" style={styles.left}>
        <div style={styles.leftContent}>
          <div style={styles.logo}>
            <span style={styles.logoIcon}>🧠</span>
            <span style={styles.logoText}>{t('appBrand')}</span>
          </div>
          <h1 style={styles.headline}>{t('centralizedQuizManagementPlatform')}</h1>
          <p style={styles.subheading}>
            {t('loginHeroSubheading')}
          </p>
          <div style={styles.features}>
            {[t('questionCreationBulkUpload'), t('lifecycleStatusTracking'), t('reviewApprovalWorkflow'), t('questionBankManagement')].map(f => (
              <div key={f} style={styles.feature}>
                <span style={styles.featureCheck}>✓</span>
                <span>{f}</span>
              </div>
            ))}
          </div>
        </div>
      </div>

      <div className="login-right" style={styles.right}>
        <div style={styles.formCard}>
          <div style={styles.roleStrip}>
            <span style={styles.rolePillPrimary}>Admin</span>
            <span style={styles.rolePillSecondary}>SME</span>
          </div>
          <h2 style={styles.formTitle}>{t('welcomeBack')}</h2>
          <p style={styles.formSubtitle}>{t('signInEnterprise')}</p>
          <div style={{ marginTop: -8, marginBottom: 14 }}>
            <label style={{ display: 'block', fontSize: 12, color: 'var(--text-secondary)', marginBottom: 6 }}>{t('language')}</label>
            <select
              className="form-select"
              value={language}
              onChange={(e) => setLanguage(e.target.value)}
              style={{ width: '100%' }}
            >
              {availableLanguages.map((lang) => (
                <option key={lang.code} value={lang.code}>{lang.label}</option>
              ))}
            </select>
          </div>
          <p style={{ fontSize: 12, color: 'var(--text-secondary)', marginTop: -12, marginBottom: 18 }}>
            {t('participantPrompt')} <Link to="/participant">{t('participantPortalTitle')}</Link>
          </p>

          {error && (
            <div className="alert alert-error" style={{ marginBottom: 16 }}>
              ⚠️ {error}
            </div>
          )}

          <form onSubmit={handleSubmit}>
            <div className="form-group">
              <label className="form-label">{t('enterpriseId')}</label>
              <input
                className="form-input"
                placeholder={t('enterpriseIdPlaceholder')}
                value={enterpriseId}
                onChange={e => setEnterpriseId(e.target.value)}
                autoComplete="username"
                required
              />
            </div>
            <div className="form-group">
              <label className="form-label">{t('password')}</label>
              <div style={styles.passwordWrap}>
                <input
                  className="form-input"
                  type={showPassword ? 'text' : 'password'}
                  placeholder={t('enterPassword')}
                  value={password}
                  onChange={e => setPassword(e.target.value)}
                  autoComplete="current-password"
                  style={styles.passwordInput}
                  required
                />
                <button
                  type="button"
                  onClick={() => setShowPassword((prev) => !prev)}
                  style={styles.passwordToggle}
                  aria-label={showPassword ? 'Hide password' : 'Show password'}
                >
                  {showPassword ? 'Hide' : 'Show'}
                </button>
              </div>
            </div>
            <button className="btn btn-primary btn-lg w-full" type="submit" disabled={loading} style={{ justifyContent: 'center' }}>
              {loading ? <><span className="spinner"></span> {t('signingIn')}</> : t('signIn')}
            </button>
          </form>

          <div style={styles.trustRow}>
            <span style={styles.trustBadge}>Enterprise Secure Access</span>
            <span style={styles.trustHint}>Role-based routes for admin and SME workflows</span>
          </div>

          {/* Demo shortcuts are dev-only (frontend/.env.development); inlined so production builds drop them. */}
          {process.env.REACT_APP_SHOW_DEMO_ACCOUNTS === 'true' && (
          <div style={styles.demoSection} data-testid="demo-accounts">
            <div style={styles.demoHeader}>
              <p style={styles.demoTitle}>{t('demoAccounts')}</p>
              {isMobile && (
                <button
                  type="button"
                  className="btn btn-secondary btn-sm"
                  style={styles.demoToggleBtn}
                  onClick={() => setShowDemoAccounts((prev) => !prev)}
                >
                  {showDemoAccounts ? 'Hide' : 'Show'}
                </button>
              )}
            </div>
            {showDemoAccounts && (
            <div className="login-demo-grid" style={styles.demoGrid}>
              <button type="button" className="btn btn-secondary btn-sm" style={styles.demoButtonAdmin} onClick={() => fillDemo('admin', 'admin123')}>
                👑 Admin
              </button>
              <button type="button" className="btn btn-secondary btn-sm" style={styles.demoButtonSme} onClick={() => fillDemo('gaurav.a.bhola', 'password123')}>
                👤 SME (Gaurav)
              </button>
              <button type="button" className="btn btn-secondary btn-sm" style={styles.demoButtonSme} onClick={() => fillDemo('birendra.kumar.singh', 'password123')}>
                👤 SME (Birendra)
              </button>
              <button type="button" className="btn btn-secondary btn-sm" style={styles.demoButtonSme} onClick={() => fillDemo('divya.madhanasekar', 'password123')}>
                👤 SME (Divya)
              </button>
              <button type="button" className="btn btn-secondary btn-sm" style={styles.demoButtonSme} onClick={() => fillDemo('swati.avinash.nikam', 'password123')}>
                👤 SME (Swati)
              </button>
              <button type="button" className="btn btn-secondary btn-sm" style={styles.demoButtonSme} onClick={() => fillDemo('indugu.hari.prasad', 'password123')}>
                👤 SME (Indugu)
              </button>
            </div>
            )}
            <p style={{ fontSize: 11, color: 'var(--text-muted)', marginTop: 8 }}>{t('clickToFillThenSignIn')}</p>
          </div>
          )}
        </div>
      </div>
    </div>
  );
}

const styles = {
  page: { display: 'flex', minHeight: '100vh', fontFamily: 'var(--font)' },
  left: {
    flex: 1,
    background: 'linear-gradient(135deg, #1a1f3a 0%, #2d3561 50%, #3d4a8a 100%)',
    display: 'flex', alignItems: 'center', justifyContent: 'center', padding: 48,
    position: 'relative', overflow: 'hidden',
  },
  leftContent: { position: 'relative', zIndex: 1, maxWidth: 480 },
  logo: { display: 'flex', alignItems: 'center', gap: 10, marginBottom: 40 },
  logoIcon: { fontSize: 32 },
  logoText: { fontSize: 18, fontWeight: 700, color: '#fff', letterSpacing: '-0.02em' },
  headline: { fontSize: 42, fontWeight: 800, color: '#fff', lineHeight: 1.2, marginBottom: 20, letterSpacing: '-0.03em' },
  subheading: { fontSize: 16, color: 'rgba(255,255,255,0.7)', lineHeight: 1.7, marginBottom: 36 },
  features: { display: 'flex', flexDirection: 'column', gap: 12 },
  feature: { display: 'flex', alignItems: 'center', gap: 12, color: 'rgba(255,255,255,0.85)', fontSize: 14 },
  featureCheck: { width: 22, height: 22, background: 'rgba(108,99,255,0.4)', borderRadius: '50%', display: 'flex', alignItems: 'center', justifyContent: 'center', color: '#a78bfa', fontSize: 12, flexShrink: 0, textAlign: 'center', lineHeight: '22px' },
  right: { width: 480, display: 'flex', alignItems: 'center', justifyContent: 'center', padding: 40, background: '#f8f9fc' },
  formCard: { width: '100%', background: '#fff', borderRadius: 20, padding: 40, boxShadow: '0 20px 60px rgba(0,0,0,0.08)' },
  roleStrip: { display: 'flex', gap: 8, marginBottom: 14 },
  rolePillPrimary: { display: 'inline-flex', alignItems: 'center', padding: '4px 10px', borderRadius: 999, background: '#dbeafe', color: '#1d4ed8', fontSize: 11, fontWeight: 700 },
  rolePillSecondary: { display: 'inline-flex', alignItems: 'center', padding: '4px 10px', borderRadius: 999, background: '#e0f2fe', color: '#0369a1', fontSize: 11, fontWeight: 700 },
  formTitle: { fontSize: 26, fontWeight: 800, marginBottom: 6, letterSpacing: '-0.02em' },
  formSubtitle: { color: 'var(--text-secondary)', marginBottom: 28, fontSize: 14 },
  passwordWrap: { position: 'relative' },
  passwordInput: { paddingRight: 64 },
  passwordToggle: {
    position: 'absolute',
    right: 10,
    top: '50%',
    transform: 'translateY(-50%)',
    border: '1px solid #dbe3f1',
    background: '#f8faff',
    color: '#334155',
    borderRadius: 8,
    fontSize: 11,
    fontWeight: 700,
    padding: '3px 8px',
    cursor: 'pointer',
  },
  trustRow: { marginTop: 12, display: 'flex', gap: 10, flexWrap: 'wrap', alignItems: 'center' },
  trustBadge: { fontSize: 11, fontWeight: 700, color: '#065f46', background: '#ecfdf5', border: '1px solid #bbf7d0', borderRadius: 999, padding: '4px 10px' },
  trustHint: { fontSize: 12, color: 'var(--text-secondary)' },
  demoSection: { marginTop: 28, paddingTop: 20, borderTop: '1px solid var(--border)' },
  demoHeader: { display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: 10, marginBottom: 10 },
  demoTitle: { fontSize: 12, fontWeight: 600, color: 'var(--text-muted)', textTransform: 'uppercase', letterSpacing: '0.06em', marginBottom: 10 },
  demoToggleBtn: { minHeight: 34, padding: '6px 12px' },
  demoGrid: { display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 8 },
  demoButtonAdmin: { borderColor: '#c7d2fe', background: '#eef2ff', color: '#3730a3' },
  demoButtonSme: { borderColor: '#bfdbfe', background: '#eff6ff', color: '#1e40af' },
};
