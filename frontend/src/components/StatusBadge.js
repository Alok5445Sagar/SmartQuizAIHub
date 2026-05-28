import React from 'react';

const STATUS_CONFIG = {
  DRAFT: { label: 'Draft', cls: 'badge-draft', icon: '✏️' },
  READY_FOR_REVIEW: { label: 'Ready for Review', cls: 'badge-ready', icon: '📤' },
  UNDER_REVIEW: { label: 'Under Review', cls: 'badge-under-review', icon: '🔍' },
  APPROVED: { label: 'Approved', cls: 'badge-approved', icon: '✅' },
  REJECTED: { label: 'Rejected', cls: 'badge-rejected', icon: '❌' },
};

export default function StatusBadge({ status }) {
  const cfg = STATUS_CONFIG[status] || { label: status, cls: 'badge-draft', icon: '❓' };
  return (
    <span className={`badge ${cfg.cls}`}>
      {cfg.icon} {cfg.label}
    </span>
  );
}

export function DifficultyBadge({ difficulty }) {
  const map = {
    Easy: { color: '#065f46', bg: '#d1fae5' },
    Medium: { color: '#92400e', bg: '#fef3c7' },
    Hard: { color: '#7f1d1d', bg: '#fee2e2' },
  };
  const style = map[difficulty] || { color: '#374151', bg: '#f3f4f6' };
  return (
    <span className="badge" style={{ background: style.bg, color: style.color }}>
      {difficulty}
    </span>
  );
}


export function QuestionTypeBadge({ type }) {
  const isMSQ = type === 'MSQ';
  return (
    <span className="badge" style={{
      background: isMSQ ? '#dbeafe' : '#ede9ff',
      color: isMSQ ? '#1d4ed8' : '#5b21b6',
    }}>
      {isMSQ ? '☑️ MSQ' : '🔘 MCQ'}
    </span>
  );
}
