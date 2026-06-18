import React, { useState } from 'react';
import { NavLink, useNavigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import ChatAssistantWidget from './ChatAssistantWidget';

const NavItem = ({ to, icon, label, badge }) => (
  <NavLink to={to} style={({ isActive }) => ({
    display: 'flex', alignItems: 'center', gap: 10,
    padding: '9px 16px', borderRadius: 10,
    textDecoration: 'none', fontSize: 14, fontWeight: 500,
    color: isActive ? 'var(--accent)' : 'rgba(255,255,255,0.7)',
    background: isActive ? 'rgba(108,99,255,0.15)' : 'transparent',
    transition: 'all 0.15s',
    marginBottom: 2,
  })}>
    <span style={{ fontSize: 18, width: 22, textAlign: 'center' }}>{icon}</span>
    <span style={{ flex: 1 }}>{label}</span>
    {badge && <span style={{ fontSize: 11, background: 'var(--accent)', color: '#fff', borderRadius: 100, padding: '1px 7px' }}>{badge}</span>}
  </NavLink>
);

export default function Layout({ children }) {
  const { user, logout } = useAuth();
  const navigate = useNavigate();
  const [sidebarOpen, setSidebarOpen] = useState(true);

  const handleLogout = () => { logout(); navigate('/login'); };

  const isAdmin = user?.role === 'ADMIN';

  return (
    <div style={{ display: 'flex', height: '100vh', overflow: 'hidden' }}>
      {/* Sidebar */}
      <div style={{
        width: 'var(--sidebar-width)', flexShrink: 0,
        background: 'linear-gradient(180deg, #1a1f3a 0%, #2d3561 100%)',
        display: 'flex', flexDirection: 'column',
        borderRight: '1px solid rgba(255,255,255,0.06)',
        overflow: 'hidden',
      }}>
        {/* Logo */}
        <div style={{ padding: '20px 20px 16px', borderBottom: '1px solid rgba(255,255,255,0.08)' }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
            <span style={{ fontSize: 28 }}>🧠</span>
            <div>
              <div style={{ color: '#fff', fontWeight: 800, fontSize: 15, letterSpacing: '-0.02em' }}>Smart Quiz Hub</div>
              <div style={{ color: 'rgba(255,255,255,0.45)', fontSize: 11 }}>AI-Powered Platform</div>
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
          <div style={{ fontSize: 10, fontWeight: 700, color: 'rgba(255,255,255,0.3)', letterSpacing: '0.1em', textTransform: 'uppercase', padding: '4px 8px 8px' }}>Questions</div>
          <NavItem to="/my-questions" icon="📋" label="My Questions" />
          <NavItem to="/add-question" icon="➕" label="Add Question" />
          <NavItem to="/my-reviews" icon="🔍" label="My Pending Reviews" />

          {isAdmin && (
            <>
              <div style={{ fontSize: 10, fontWeight: 700, color: 'rgba(255,255,255,0.3)', letterSpacing: '0.1em', textTransform: 'uppercase', padding: '16px 8px 8px' }}>Admin</div>
              <NavItem to="/admin/question-bank" icon="🏦" label="Question Bank" />
            </>
          )}
        </nav>

        {/* Logout */}
        <div style={{ padding: '12px 16px', borderTop: '1px solid rgba(255,255,255,0.08)' }}>
          <button onClick={handleLogout} style={{
            width: '100%', padding: '9px 14px', border: '1px solid rgba(255,255,255,0.1)',
            borderRadius: 10, background: 'transparent', color: 'rgba(255,255,255,0.6)',
            cursor: 'pointer', fontSize: 13, fontWeight: 500, display: 'flex', alignItems: 'center', gap: 8,
            transition: 'all 0.15s',
            fontFamily: 'var(--font)',
          }}
          onMouseEnter={e => { e.currentTarget.style.background = 'rgba(239,68,68,0.15)'; e.currentTarget.style.color = '#fca5a5'; e.currentTarget.style.borderColor = 'rgba(239,68,68,0.3)'; }}
          onMouseLeave={e => { e.currentTarget.style.background = 'transparent'; e.currentTarget.style.color = 'rgba(255,255,255,0.6)'; e.currentTarget.style.borderColor = 'rgba(255,255,255,0.1)'; }}>
            🚪 Sign Out
          </button>
        </div>
      </div>

      {/* Main content */}
      <div style={{ flex: 1, overflow: 'auto', display: 'flex', flexDirection: 'column' }}>
        <main style={{ flex: 1, padding: '28px 32px' }}>
          {children}
        </main>
      </div>

      <ChatAssistantWidget />
    </div>
  );
}
