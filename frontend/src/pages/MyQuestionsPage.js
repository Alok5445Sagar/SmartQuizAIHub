import React, { useState, useEffect, useCallback } from 'react';
import { useNavigate, useLocation } from 'react-router-dom';
import { mcqAPI, referenceAPI } from '../services/api';
import StatusBadge, { DifficultyBadge } from '../components/StatusBadge';
import Pagination from '../components/Pagination';
import ViewMCQModal from '../components/ViewMCQModal';
import { QuestionTypeBadge } from '../components/StatusBadge';
import { useLanguage } from '../context/LanguageContext';
import { useAuth } from '../context/AuthContext';

const AUTO_REFRESH_MS = 1000;

export default function MyQuestionsPage() {
  const { t, language } = useLanguage();
  const { user } = useAuth();
  const location = useLocation();
  const [data, setData] = useState(null);
  const [page, setPage] = useState(() => location.state?.returnToPage ?? 0);
  const [loading, setLoading] = useState(true);
  const [viewMCQ, setViewMCQ] = useState(null);
  const navigate = useNavigate();

  // Filters
  const [stacks, setStacks] = useState([]);
  const [topics, setTopics] = useState([]);
  const [filterStack, setFilterStack] = useState(() => location.state?.returnFilters?.filterStack ?? '');
  const [filterTopic, setFilterTopic] = useState(() => location.state?.returnFilters?.filterTopic ?? '');
  const [filterDifficulty, setFilterDifficulty] = useState(() => location.state?.returnFilters?.filterDifficulty ?? '');
  const [filterStatus, setFilterStatus] = useState(() => location.state?.returnFilters?.filterStatus ?? '');
  const shouldAutoRefresh = user?.role === 'ADMIN' || user?.role === 'SME';

  // Count questions per status from current data
  const statusCounts = (data?.content || []).reduce((acc, mcq) => {
    acc[mcq.status] = (acc[mcq.status] || 0) + 1;
    return acc;
  }, {});

  const STATUS_TABS = [
    { label: t('all'), value: '' },
    { label: t('draft'), value: 'DRAFT' },
    { label: t('readyForReview'), value: 'READY_FOR_REVIEW' },
    { label: t('underReview'), value: 'UNDER_REVIEW' },
    { label: t('approved'), value: 'APPROVED' },
    { label: t('rejected'), value: 'REJECTED' },
  ];

  // Load stacks for filter dropdown
  useEffect(() => {
    referenceAPI.getStacks().then(r => setStacks(r.data));
  }, []);

  // Load topics when stack filter changes
  useEffect(() => {
    if (filterStack) {
      referenceAPI.getTopics(filterStack).then(r => setTopics(r.data));
    } else {
      setTopics([]);
      setFilterTopic('');
    }
  }, [filterStack]);

  const fetchData = useCallback(async ({ background = false } = {}) => {
    if (!background) {
      setLoading(true);
    }
    try {
      const res = await mcqAPI.getMyQuestions(page, 10, {
        stackId: filterStack || undefined,
        topicId: filterTopic || undefined,
        difficulty: filterDifficulty || undefined,
        status: filterStatus || undefined,
      });
      setData(res.data);
    } finally {
      if (!background) {
        setLoading(false);
      }
    }
  }, [page, filterStack, filterTopic, filterDifficulty, filterStatus, language]);

  useEffect(() => { fetchData(); }, [fetchData]);

  useEffect(() => {
    if (!shouldAutoRefresh) return;

    const refreshNow = () => {
      if (document.visibilityState === 'hidden') return;
      fetchData({ background: true });
    };

    const intervalId = setInterval(() => {
      refreshNow();
    }, AUTO_REFRESH_MS);

    const handleVisibility = () => {
      if (document.visibilityState === 'visible') {
        refreshNow();
      }
    };

    window.addEventListener('focus', refreshNow);
    document.addEventListener('visibilitychange', handleVisibility);

    return () => {
      clearInterval(intervalId);
      window.removeEventListener('focus', refreshNow);
      document.removeEventListener('visibilitychange', handleVisibility);
    };
  }, [shouldAutoRefresh, fetchData]);

  const canEdit = (status) => status === 'DRAFT' || status === 'REJECTED';

  // Client-side filtering
  // Server-side filtering — use content directly
  const filtered = data?.content || [];

  const hasFilters = filterStack || filterTopic || filterDifficulty || filterStatus;
  const clearFilters = () => {
    setFilterStack('');
    setFilterTopic('');
    setFilterDifficulty('');
    setFilterStatus('');
    setPage(0);
  };

  return (
    <div>
      <div className="page-header">
        <div>
          <h1 className="page-title">{t('myQuestions')}</h1>
          <p className="page-subtitle">{t('myQuestionsSubtitle')}</p>
        </div>
        <button className="btn btn-primary" onClick={() => navigate('/add-question')}>
          ➕ {t('addQuestion')}
        </button>
      </div>

      {/* Status tabs */}
          <div style={{ display: 'flex', gap: 6, marginBottom: 16, flexWrap: 'wrap' }}>
            {STATUS_TABS.map(tab => (
              <button
                key={tab.value}
                onClick={() => { setFilterStatus(tab.value); setPage(0); }}
                style={{
                  padding: '5px 14px',
                  borderRadius: 100,
                  border: `1.5px solid ${filterStatus === tab.value ? 'var(--accent)' : 'var(--border)'}`,
                  background: filterStatus === tab.value ? 'var(--accent)' : 'var(--bg-card)',
                  color: filterStatus === tab.value ? '#fff' : 'var(--text-secondary)',
                  fontWeight: 600,
                  fontSize: 13,
                  cursor: 'pointer',
                  fontFamily: 'var(--font)',
                  transition: 'all 0.15s',
                }}
              >
                {tab.label}
                {tab.value && data?.totalElements !== undefined && (
                  <span style={{
                    marginLeft: 6, fontSize: 11,
                    background: filterStatus === tab.value ? 'rgba(255,255,255,0.25)' : 'var(--border)',
                    color: filterStatus === tab.value ? '#fff' : 'var(--text-muted)',
                    borderRadius: 100, padding: '1px 6px'
                  }}>
                    {statusCounts[tab.value] || 0}
                  </span>
                )}
                {!tab.value && (
                  <span style={{
                    marginLeft: 6, fontSize: 11,
                    background: filterStatus === tab.value ? 'rgba(255,255,255,0.25)' : 'var(--border)',
                    color: filterStatus === tab.value ? '#fff' : 'var(--text-muted)',
                    borderRadius: 100, padding: '1px 6px'
                  }}>
                    {data?.totalElements || 0}
                  </span>
                )}
              </button>
            ))}
          </div>

      {/* Filters */}
      <div className="questions-filter-row" style={{ display: 'flex', gap: 10, marginBottom: 16, flexWrap: 'wrap', alignItems: 'center' }}>
        <select
          className="form-select"
          style={{ width: 160 }}
          value={filterStack}
          onChange={e => { setFilterStack(e.target.value); setFilterTopic(''); setPage(0); }}
        >
          <option value="">{t('allStacks')}</option>
          {stacks.map(s => <option key={s.stackId} value={s.stackId}>{s.stackName}</option>)}
        </select>

        <select
          className="form-select"
          style={{ width: 200 }}
          value={filterTopic}
          onChange={e => { setFilterTopic(e.target.value); setPage(0); }}
          disabled={!filterStack}
        >
          <option value="">{t('allTopics')}</option>
          {topics.map(t => <option key={t.topicId} value={t.topicId}>{t.topicName}</option>)}
        </select>

        <select
          className="form-select"
          style={{ width: 140 }}
          value={filterDifficulty}
          onChange={e => { setFilterDifficulty(e.target.value); setPage(0); }}
        >
          <option value="">{t('allDifficulties')}</option>
          <option value="Easy">{t('easy')}</option>
          <option value="Medium">{t('medium')}</option>
          <option value="Hard">{t('hard')}</option>
        </select>

        <select
          className="form-select"
          style={{ width: 180 }}
          value={filterStatus}
          onChange={e => { setFilterStatus(e.target.value); setPage(0); }}
        >
          <option value="">{t('allStatuses')}</option>
          <option value="DRAFT">{t('draft')}</option>
          <option value="READY_FOR_REVIEW">{t('readyForReview')}</option>
          <option value="UNDER_REVIEW">{t('underReview')}</option>
          <option value="APPROVED">{t('approved')}</option>
          <option value="REJECTED">{t('rejected')}</option>
        </select>

        {hasFilters && (
          <button className="btn btn-ghost btn-sm" onClick={clearFilters}>✕ {t('clear')}</button>
        )}

        {hasFilters && (
          <span style={{ fontSize: 12, color: 'var(--text-muted)' }}>
            {filtered.length} {t('matchesOnPage')}
          </span>
        )}
      </div>

      <div className="card" style={{ padding: 0 }}>
        {loading ? (
          <div className="loading-container"><div className="spinner spinner-dark" /></div>
        ) : !data?.content?.length ? (
          <div className="empty-state">
            <div className="empty-icon">📝</div>
            <h3>{t('noQuestionsYet')}</h3>
            <p>{t('createFirstQuestionByClicking')} "{t('addQuestion')}"</p>
          </div>
        ) : filtered.length === 0 ? (
          <div className="empty-state">
            <div className="empty-icon">🔍</div>
            <h3>{t('noMatchesOnPage')}</h3>
            <p>{t('tryChangingFilters')}</p>
          </div>
        ) : (
          <>
            <div className="table-wrapper mobile-cards">
              <table>
                <thead>
                  <tr>
                    <th>#</th>
                    <th>{t('type')}</th>
                    <th>{t('question')}</th>
                    <th>{t('stack')}</th>
                    <th>{t('topic')}</th>
                    <th>{t('difficulty')}</th>
                    <th>{t('status')}</th>
                    <th>{t('actions')}</th>
                  </tr>
                </thead>
                <tbody>
                  {filtered.map((mcq, idx) => (
                    <tr key={mcq.questionId}>
                      <td data-label="#" style={{ color: 'var(--text-muted)', fontFamily: 'var(--font-mono)', fontSize: 12 }}>
                        {page * 10 + idx + 1}
                      </td>
                      <td data-label={t('type')}><QuestionTypeBadge type={mcq.questionType} aiGenerated={mcq.aiGenerated} bulkUploaded={mcq.bulkUploaded} /></td>
                      <td data-label={t('question')}>
                        <div className="truncate" title={mcq.questionStem}>
                          {mcq.questionStem}
                        </div>
                      </td>
                      <td data-label={t('stack')}>
                        <span style={{ fontSize: 12, background: '#ede9ff', color: '#5b21b6', padding: '2px 8px', borderRadius: 4 }}>
                          {mcq.stackName}
                        </span>
                      </td>
                      <td data-label={t('topic')} style={{ color: 'var(--text-secondary)', fontSize: 12 }}>{mcq.topicName}</td>
                      <td data-label={t('difficulty')}><DifficultyBadge difficulty={mcq.difficulty} /></td>
                      <td data-label={t('status')}><StatusBadge status={mcq.status} /></td>
                      <td data-label={t('actions')}>
                        <div style={{ display: 'flex', gap: 6 }}>
                          <button className="btn btn-ghost btn-sm" onClick={() => setViewMCQ(mcq)}>
                            👁 {t('view')}
                          </button>
                          {canEdit(mcq.status) && (
                            <button
                              className="btn btn-secondary btn-sm"
                              onClick={() => navigate(`/edit-question/${mcq.questionId}`, {
                                state: {
                                  returnToPage: page,
                                  returnFilters: {
                                    filterStack,
                                    filterTopic,
                                    filterDifficulty,
                                    filterStatus,
                                  },
                                }
                              })}
                            >
                              ✏️ {t('edit')}
                            </button>
                          )}
                        </div>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
            <div style={{ padding: '12px 16px', borderTop: '1px solid var(--border)', display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
              <span style={{ fontSize: 12, color: 'var(--text-muted)' }}>
                {t('showing')} {data.content.length} {t('of')} {data.totalElements} questions
              </span>
              <Pagination page={page} totalPages={data.totalPages} onPageChange={setPage} />
            </div>
          </>
        )}
      </div>

      {viewMCQ && <ViewMCQModal mcq={viewMCQ} onClose={() => setViewMCQ(null)} />}
    </div>
  );
}