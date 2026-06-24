import React, { useEffect, useState } from 'react';
import { NavLink, useNavigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import { useLanguage } from '../context/LanguageContext';
import ChatAssistantWidget from './ChatAssistantWidget';

const NavItem = ({ to, icon, label, badge, onClick }) => (
  <NavLink to={to} style={({ isActive }) => ({
    display: 'flex', alignItems: 'center', gap: 10,
    padding: '9px 16px', borderRadius: 10,
    textDecoration: 'none', fontSize: 14, fontWeight: 500,
    color: isActive ? 'var(--accent)' : 'rgba(255,255,255,0.7)',
    background: isActive ? 'rgba(108,99,255,0.15)' : 'transparent',
    transition: 'all 0.15s',
    marginBottom: 2,
  })} onClick={onClick}>
    <span style={{ fontSize: 18, width: 22, textAlign: 'center' }}>{icon}</span>
    <span style={{ flex: 1 }}>{label}</span>
    {badge && <span style={{ fontSize: 11, background: 'var(--accent)', color: '#fff', borderRadius: 100, padding: '1px 7px' }}>{badge}</span>}
  </NavLink>
);

export default function Layout({ children }) {
  const { user, logout } = useAuth();
  const { language, setLanguage, t, availableLanguages } = useLanguage();
  const navigate = useNavigate();
  const [isMobile, setIsMobile] = useState(() => (typeof window !== 'undefined' ? window.innerWidth <= 900 : false));
  const [sidebarOpen, setSidebarOpen] = useState(() => (typeof window !== 'undefined' ? window.innerWidth > 900 : false));

  useEffect(() => {
    const onResize = () => {
      const mobile = window.innerWidth <= 900;
      setIsMobile(mobile);
      setSidebarOpen(!mobile);
    };
    window.addEventListener('resize', onResize);
    return () => window.removeEventListener('resize', onResize);
  }, []);

  const handleLogout = () => { logout(); navigate('/login'); };

  const isAdmin = user?.role === 'ADMIN';

  const closeSidebarIfMobile = () => {
    if (isMobile) setSidebarOpen(false);
  };

  return (
    <div style={{ display: 'flex', height: '100vh', overflow: 'hidden', position: 'relative' }}>
      {isMobile && sidebarOpen && (
        <div
          onClick={() => setSidebarOpen(false)}
          style={{ position: 'fixed', inset: 0, background: 'rgba(15, 23, 42, 0.45)', zIndex: 1290 }}
        />
      )}

      {/* Sidebar */}
      <div style={{
        width: 'var(--sidebar-width)', flexShrink: 0,
        background: 'linear-gradient(180deg, #1a1f3a 0%, #2d3561 100%)',
        display: 'flex', flexDirection: 'column',
        borderRight: '1px solid rgba(255,255,255,0.06)',
        overflow: 'hidden',
        position: isMobile ? 'fixed' : 'relative',
        height: '100vh',
        top: 0,
        left: 0,
        zIndex: 1300,
        transform: isMobile ? (sidebarOpen ? 'translateX(0)' : 'translateX(-100%)') : 'none',
        transition: 'transform 0.2s ease',
      }}>
        {/* Logo */}
        <div style={{ padding: '20px 20px 16px', borderBottom: '1px solid rgba(255,255,255,0.08)' }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
            <span style={{ fontSize: 28 }}>🧠</span>
            <div>
              <div style={{ color: '#fff', fontWeight: 800, fontSize: 15, letterSpacing: '-0.02em' }}>{t('appBrand')}</div>
              <div style={{ color: 'rgba(255,255,255,0.45)', fontSize: 11 }}>{t('aiPoweredPlatform')}</div>
            </div>
          </div>
        </div>

        {/* User info */}
        <div style={{ padding: '14px 16px', borderBottom: '1px solid rgba(255,255,255,0.08)' }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
            <div style={{ width: 34, height: 34, background: 'rgba(108,99,255,0.4)', borderRadius: '50%', display: 'flex', alignItems: 'center', justifyContent: 'center', fontSize: 16 }}>
              {isAdmin ? '👑' : '👤'}
            </div>
            <div style={{ overflow: 'hidden' }}>
              <div style={{ color: '#fff', fontSize: 13, fontWeight: 600, whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis' }}>{user?.fullName}</div>
              <div style={{ color: 'rgba(255,255,255,0.45)', fontSize: 11 }}>{user?.role}</div>
            </div>
          </div>
        </div>

        {/* Nav */}
        <nav style={{ flex: 1, padding: '14px 12px', overflowY: 'auto' }}>
          <div style={{ fontSize: 10, fontWeight: 700, color: 'rgba(255,255,255,0.3)', letterSpacing: '0.1em', textTransform: 'uppercase', padding: '4px 8px 8px' }}>{t('questionsSection')}</div>
          <NavItem to="/my-questions" icon="📋" label={t('myQuestions')} onClick={closeSidebarIfMobile} />
          <NavItem to="/add-question" icon="➕" label={t('addQuestion')} onClick={closeSidebarIfMobile} />
          <NavItem to="/my-reviews" icon="🔍" label={t('myPendingReviews')} onClick={closeSidebarIfMobile} />

          <div style={{ fontSize: 10, fontWeight: 700, color: 'rgba(255,255,255,0.3)', letterSpacing: '0.1em', textTransform: 'uppercase', padding: '16px 8px 8px' }}>{t('collaborationSection')}</div>
          <NavItem to="/team-chat" icon="💬" label={t('teamChat')} onClick={closeSidebarIfMobile} />

          {isAdmin && (
            <>
              <div style={{ fontSize: 10, fontWeight: 700, color: 'rgba(255,255,255,0.3)', letterSpacing: '0.1em', textTransform: 'uppercase', padding: '16px 8px 8px' }}>{t('adminSection')}</div>
              <NavItem to="/admin/question-bank" icon="🏦" label={t('questionBank')} onClick={closeSidebarIfMobile} />
              <NavItem to="/admin/live-quizzes" icon="🎯" label={t('liveQuizzes')} onClick={closeSidebarIfMobile} />
              <NavItem to="/admin/participant-analytics" icon="📊" label={t('participantAnalytics')} onClick={closeSidebarIfMobile} />
            </>
          )}
        </nav>

        {/* Logout */}
        <div style={{ padding: '12px 16px', borderTop: '1px solid rgba(255,255,255,0.08)' }}>
          <div style={{ marginBottom: 10 }}>
            <label style={{ display: 'block', fontSize: 11, color: 'rgba(255,255,255,0.55)', marginBottom: 6 }}>{t('language')}</label>
            <select
              value={language}
              onChange={(e) => setLanguage(e.target.value)}
              style={{
                width: '100%',
                padding: '8px 10px',
                borderRadius: 10,
                border: '1px solid rgba(255,255,255,0.18)',
                background: 'rgba(255,255,255,0.08)',
                color: '#fff',
              }}
            >
              {availableLanguages.map((lang) => (
                <option key={lang.code} value={lang.code} style={{ color: '#111827' }}>{lang.label}</option>
              ))}
            </select>
          </div>
          <button onClick={handleLogout} style={{
            width: '100%', padding: '9px 14px', border: '1px solid rgba(255,255,255,0.1)',
            borderRadius: 10, background: 'transparent', color: 'rgba(255,255,255,0.6)',
            cursor: 'pointer', fontSize: 13, fontWeight: 500, display: 'flex', alignItems: 'center', gap: 8,
            transition: 'all 0.15s',
            fontFamily: 'var(--font)',
          }}
          onMouseEnter={e => { e.currentTarget.style.background = 'rgba(239,68,68,0.15)'; e.currentTarget.style.color = '#fca5a5'; e.currentTarget.style.borderColor = 'rgba(239,68,68,0.3)'; }}
          onMouseLeave={e => { e.currentTarget.style.background = 'transparent'; e.currentTarget.style.color = 'rgba(255,255,255,0.6)'; e.currentTarget.style.borderColor = 'rgba(255,255,255,0.1)'; }}>
            🚪 {t('signOut')}
          </button>
        </div>
      </div>

      {/* Main content */}
      <div style={{ flex: 1, overflow: 'auto', display: 'flex', flexDirection: 'column' }}>
        {isMobile && (
          <header
            style={{
              height: 56,
              borderBottom: '1px solid var(--border)',
              background: '#fff',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'space-between',
              padding: '0 12px',
              position: 'sticky',
              top: 0,
              zIndex: 1200,
            }}
          >
            <button className="btn btn-secondary btn-sm" onClick={() => setSidebarOpen(true)}>
              ☰
            </button>
            <div style={{ fontWeight: 700, color: 'var(--primary)' }}>{t('appBrand')}</div>
            <button className="btn btn-ghost btn-sm" onClick={handleLogout}>{t('signOut')}</button>
          </header>
        )}
        <main style={{ flex: 1, padding: isMobile ? '14px 12px 20px' : '28px 32px' }}>
          {children}
        </main>
      </div>

      <ChatAssistantWidget />
    </div>
  );
}
