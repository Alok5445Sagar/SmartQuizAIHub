import React, { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';

export default function LoginPage() {
  const [enterpriseId, setEnterpriseId] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);
  const { login } = useAuth();
  const navigate = useNavigate();

  const handleSubmit = async (e) => {
    e.preventDefault();
    setError('');
    setLoading(true);
    try {
      const user = await login(enterpriseId, password);
      navigate(user.role === 'ADMIN' ? '/admin/question-bank' : '/my-questions');
    } catch (err) {
      setError(err.response?.data?.message || 'Invalid credentials. Please try again.');
    } finally {
      setLoading(false);
    }
  };

  const fillDemo = (id, pw) => { setEnterpriseId(id); setPassword(pw); };

  return (
    <div style={styles.page}>
      <div style={styles.left}>
        <div style={styles.leftContent}>
          <div style={styles.logo}>
            <span style={styles.logoIcon}>🧠</span>
            <span style={styles.logoText}>Smart Quiz AI Hub</span>
          </div>
          <h1 style={styles.headline}>Centralized Quiz<br/>Management Platform</h1>
          <p style={styles.subheading}>
            Create, review, and manage questions with an AI-powered workflow 
            built for Subject Matter Experts and Admins.
          </p>
          <div style={styles.features}>
            {['Question Creation & Bulk Upload', 'Lifecycle Status Tracking', 'Review & Approval Workflow', 'Question Bank Management'].map(f => (
              <div key={f} style={styles.feature}>
                <span style={styles.featureCheck}>✓</span>
                <span>{f}</span>
              </div>
            ))}
          </div>
        </div>
      </div>

      <div style={styles.right}>
        <div style={styles.formCard}>
          <h2 style={styles.formTitle}>Welcome back</h2>
          <p style={styles.formSubtitle}>Sign in with your enterprise ID</p>

          {error && (
            <div className="alert alert-error" style={{ marginBottom: 16 }}>
              ⚠️ {error}
            </div>
          )}

          <form onSubmit={handleSubmit}>
            <div className="form-group">
              <label className="form-label">Enterprise ID</label>
              <input
                className="form-input"
                placeholder="e.g. gaurav.a.bhola"
                value={enterpriseId}
                onChange={e => setEnterpriseId(e.target.value)}
                required
              />
            </div>
            <div className="form-group">
              <label className="form-label">Password</label>
              <input
                className="form-input"
                type="password"
                placeholder="Enter your password"
                value={password}
                onChange={e => setPassword(e.target.value)}
                required
              />
            </div>
            <button className="btn btn-primary btn-lg w-full" type="submit" disabled={loading} style={{ justifyContent: 'center' }}>
              {loading ? <><span className="spinner"></span> Signing in...</> : 'Sign In'}
            </button>
          </form>

          <div style={styles.demoSection}>
            <p style={styles.demoTitle}>Demo Accounts</p>
            <div style={styles.demoGrid}>
              <button className="btn btn-secondary btn-sm" onClick={() => fillDemo('admin', 'admin123')}>
                👑 Admin
              </button>
              <button className="btn btn-secondary btn-sm" onClick={() => fillDemo('gaurav.a.bhola', 'password123')}>
                👤 SME (Gaurav)
              </button>
              <button className="btn btn-secondary btn-sm" onClick={() => fillDemo('birendra.kumar.singh', 'password123')}>
                👤 SME (Birendra)
              </button>
              <button className="btn btn-secondary btn-sm" onClick={() => fillDemo('divya.madhanasekar', 'password123')}>
                👤 SME (Divya)
              </button>
            </div>
            <p style={{ fontSize: 11, color: 'var(--text-muted)', marginTop: 8 }}>Click to fill credentials, then Sign In</p>
          </div>
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
  formTitle: { fontSize: 26, fontWeight: 800, marginBottom: 6, letterSpacing: '-0.02em' },
  formSubtitle: { color: 'var(--text-secondary)', marginBottom: 28, fontSize: 14 },
  demoSection: { marginTop: 28, paddingTop: 20, borderTop: '1px solid var(--border)' },
  demoTitle: { fontSize: 12, fontWeight: 600, color: 'var(--text-muted)', textTransform: 'uppercase', letterSpacing: '0.06em', marginBottom: 10 },
  demoGrid: { display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 8 },
};
