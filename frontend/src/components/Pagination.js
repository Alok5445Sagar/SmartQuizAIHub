import React from 'react';
import { useLanguage } from '../context/LanguageContext';

export default function Pagination({ page, totalPages, onPageChange }) {
  const { t } = useLanguage();
  if (totalPages <= 1) return null;
  
  const pages = [];
  for (let i = 0; i < totalPages; i++) pages.push(i);
  
  return (
    <div className="pagination">
      <button onClick={() => onPageChange(page - 1)} disabled={page === 0}>← {t('prev')}</button>
      {pages.map(p => (
        <button key={p} className={p === page ? 'active' : ''} onClick={() => onPageChange(p)}>
          {p + 1}
        </button>
      ))}
      <button onClick={() => onPageChange(page + 1)} disabled={page === totalPages - 1}>{t('next')} →</button>
    </div>
  );
}
