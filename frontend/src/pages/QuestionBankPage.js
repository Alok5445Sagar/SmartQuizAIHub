import React, { useState, useEffect, useCallback, useRef } from 'react';
import { adminAPI, aiAPI, referenceAPI } from '../services/api';
import MCQForm from '../components/MCQForm';
import StatusBadge, { DifficultyBadge } from '../components/StatusBadge';
import Pagination from '../components/Pagination';
import ViewMCQModal from '../components/ViewMCQModal';
import toast from 'react-hot-toast';
import { QuestionTypeBadge } from '../components/StatusBadge';
import { useAuth } from '../context/AuthContext';
import { useLanguage } from '../context/LanguageContext';

const AUTO_REFRESH_MS = 1000;

function AssignReviewerModal({ mcq, onClose, onDone }) {
  const { t } = useLanguage();
  const [reviewers, setReviewers] = useState([]);
  const [selectedReviewer, setSelectedReviewer] = useState('');
  const [loading, setLoading] = useState(false);
  const [fetching, setFetching] = useState(true);

  useEffect(() => {
    adminAPI.getReviewers(mcq.stackId, mcq.creatorEnterpriseId)
      .then(r => setReviewers(r.data))
      .catch(() => toast.error(t('failedToLoadReviewers')))
      .finally(() => setFetching(false));
  }, [mcq.stackId, mcq.creatorEnterpriseId]);

  const handleAssign = async () => {
    if (!selectedReviewer) return toast.error(t('pleaseSelectReviewer'));
    setLoading(true);
    try {
      await adminAPI.assignReviewer(mcq.questionId, selectedReviewer);
      toast.success(t('reviewerAssignedSuccessfully'));
      onDone();
    } catch (err) {
      toast.error((err.response?.data?.detail || err.response?.data?.message) || t('failedToAssignReviewer'));
    } finally { setLoading(false); }
  };

  return (
    <div className="modal-overlay" onClick={e => e.target === e.currentTarget && onClose()}>
      <div className="modal" style={{ maxWidth: 500 }}>
        <div className="modal-header">
          <h2>{t('assignReviewer')}</h2>
          <button className="modal-close" onClick={onClose}>×</button>
        </div>
        <div className="modal-body">
          <div style={{ background: 'var(--bg)', borderRadius: 10, padding: 14, marginBottom: 20 }}>
            <div style={{ display: 'flex', gap: 8, marginBottom: 8, flexWrap: 'wrap' }}>
              <span style={{ fontSize: 12, background: '#ede9ff', color: '#5b21b6', padding: '2px 8px', borderRadius: 4, fontWeight: 600 }}>{mcq.stackName}</span>
              <DifficultyBadge difficulty={mcq.difficulty} />
            </div>
            <p style={{ fontSize: 13, color: 'var(--text-secondary)', lineHeight: 1.5, display: '-webkit-box', WebkitLineClamp: 2, WebkitBoxOrient: 'vertical', overflow: 'hidden' }}>
              {mcq.questionStem}
            </p>
            <p style={{ fontSize: 11, color: 'var(--text-muted)', marginTop: 6 }}>
              {t('createdBy')}: <strong style={{ fontFamily: 'var(--font-mono)' }}>{mcq.creatorEnterpriseId}</strong>
            </p>
          </div>

          <div className="form-group">
            <label className="form-label">{t('selectReviewer')} *</label>
            <p style={{ fontSize: 12, color: 'var(--text-muted)', marginBottom: 10 }}>
              {t('showingSmesWithExpertise')} <strong>{mcq.stackName}</strong> {t('excludingCreator')}
            </p>
            {fetching ? (
              <div style={{ textAlign: 'center', padding: 20 }}><div className="spinner spinner-dark" style={{ margin: '0 auto' }} /></div>
            ) : reviewers.length === 0 ? (
              <div className="alert alert-info">{t('noEligibleReviewersForStack')}</div>
            ) : (
              <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
                {reviewers.map(r => (
                  <label key={r.enterpriseId} style={{
                    display: 'flex', alignItems: 'center', gap: 12,
                    padding: '10px 14px',
                    border: `1.5px solid ${selectedReviewer === r.enterpriseId ? 'var(--accent)' : 'var(--border)'}`,
                    borderRadius: 10, cursor: 'pointer', transition: 'all 0.15s',
                    background: selectedReviewer === r.enterpriseId ? 'var(--accent-light)' : 'var(--bg-card)',
                  }}>
                    <input type="radio" name="reviewer" value={r.enterpriseId}
                      checked={selectedReviewer === r.enterpriseId}
                      onChange={() => setSelectedReviewer(r.enterpriseId)}
                      style={{ accentColor: 'var(--accent)' }} />
                    <div style={{ flex: 1 }}>
                      <div style={{ fontWeight: 600, fontSize: 14 }}>{r.fullName}</div>
                      <div style={{ fontSize: 11, fontFamily: 'var(--font-mono)', color: 'var(--text-muted)' }}>{r.enterpriseId}</div>
                    </div>
                    <span style={{
                      fontSize: 11, fontWeight: 600, padding: '2px 8px', borderRadius: 100,
                      background: r.role === 'ADMIN' ? '#fef3c7' : '#ede9ff',
                      color: r.role === 'ADMIN' ? '#92400e' : '#5b21b6',
                    }}>{r.role}</span>
                  </label>
                ))}
              </div>
            )}
          </div>
        </div>
        <div className="modal-footer">
          <button className="btn btn-secondary" onClick={onClose}>{t('cancel')}</button>
          <button className="btn btn-primary" onClick={handleAssign} disabled={loading || !selectedReviewer || fetching}>
            {loading ? <><span className="spinner" /> {t('assigning')}</> : `👤 ${t('assignReviewer')}`}
          </button>
        </div>
      </div>
    </div>
  );
}

function SimilarityResultModal({ result, onClose }) {
  const { t } = useLanguage();
  return (
    <div className="modal-overlay" onClick={e => e.target === e.currentTarget && onClose()}>
      <div className="modal" style={{ maxWidth: 640 }}>
        <div className="modal-header" style={{ background: '#fef3c7', borderBottom: '1px solid #f59e0b' }}>
          <div>
            <h2 style={{ color: '#92400e' }}>⚠️ {t('duplicateCheckFailedTitle')}</h2>
            <p style={{ fontSize: 13, color: '#92400e', marginTop: 2 }}>
              {t('similarityMatchDetectedMessage')}
            </p>
          </div>
          <button className="modal-close" onClick={onClose}>×</button>
        </div>
        <div className="modal-body">
          <p style={{ fontSize: 13, marginBottom: 16, color: 'var(--text-secondary)' }}>
            {t('similarQuestionIdsLabel')}: {result.similarQuestions.map(q => q.questionId).join(` ${t('and')} `)}.
            {result.similarQuestions.map(q => ` ${t('mcqSimilarityPrefix')} ${q.similarityPercent}% ${t('toQuestionId')} ${q.questionId}`).join(` ${t('and')} `)}.
          </p>

          {result.similarQuestions.map(q => (
            <div key={q.questionId} style={{
              background: '#fef9c3', border: '1px solid #f59e0b',
              borderRadius: 10, padding: 16, marginBottom: 12
            }}>
              <p style={{ fontWeight: 700, color: '#92400e', marginBottom: 8 }}>
                {t('questionIdLabel')} {q.questionId} — {q.similarityPercent}% {t('similar')}
              </p>
              <p style={{ fontWeight: 600, marginBottom: 8, fontSize: 14 }}>
                Stem: {q.questionStem}
              </p>
              {['A', 'B', 'C', 'D', 'E', 'F'].map(opt => {
                const val = q[`option${opt}`];
                if (!val) return null;
                return (
                  <p key={opt} style={{ fontSize: 13, marginBottom: 2, color: 'var(--text-secondary)' }}>
                    {opt}. {val}
                  </p>
                );
              })}
            </div>
          ))}

          <div className="alert alert-error" style={{ marginTop: 16 }}>
            {t('reviewRequiresSimilarityBelow30')}
          </div>
        </div>
        <div className="modal-footer">
          <button className="btn btn-secondary" onClick={onClose}>{t('editQuestionTitle')}</button>
        </div>
      </div>
    </div>
  );
}

function AdminEditModal({ mcq, onClose, onDone, onOpenAssign }) {
  const { t } = useLanguage();
  const [loading, setLoading] = useState(false);
  const [checkingDuplicate, setCheckingDuplicate] = useState(false);
  const [similarityResult, setSimilarityResult] = useState(null);
  const similarityCacheRef = useRef({ signature: null, result: null });

  const buildSimilarityPayload = (data) => ({
    questionStem: (data.questionStem || '').trim(),
    optionA: data.optionA || '',
    optionB: data.optionB || '',
    optionC: data.optionC || '',
    optionD: data.optionD || '',
    optionE: data.optionE || null,
    optionF: data.optionF || null,
    stackId: parseInt(data.stackId, 10),
    topicId: parseInt(data.topicId, 10),
    excludeQuestionId: mcq.questionId,
  });

  const buildSimilaritySignature = (payload) => JSON.stringify(payload);

  const runSimilarityCheck = async (payload) => {
    setCheckingDuplicate(true);
    try {
      const res = await aiAPI.checkSimilarity(payload);
      return res.data;
    } catch (err) {
      toast.error(t('similarityCheckFailedTryAgain'));
      return null;
    } finally {
      setCheckingDuplicate(false);
    }
  };

  const getSimilarityCheckResult = async (data) => {
    const payload = buildSimilarityPayload(data);
    const signature = buildSimilaritySignature(payload);

    if (similarityCacheRef.current.signature === signature && similarityCacheRef.current.result) {
      return similarityCacheRef.current.result;
    }

    const result = await runSimilarityCheck(payload);
    if (result) {
      similarityCacheRef.current = { signature, result };
    }
    return result;
  };

  const handleDuplicateCheck = async (data) => {
    const result = await getSimilarityCheckResult(data);
    if (!result) return;

    if (result.hasSimilar) {
      setSimilarityResult(result);
    } else {
      setSimilarityResult(null);
      toast.success(`✅ ${t('noSimilarQuestionsFoundUnique')}`);
    }
  };

  const handleSave = async (data) => {
    setLoading('save');
    try {
      await adminAPI.editQuestion(mcq.questionId, { ...data, sendForReview: false });
      toast.success(t('questionUpdated'));
      onDone();
    } catch (err) {
        const msg = (err.response?.data?.detail || err.response?.data?.message) || t('failedToUpdate');
      toast.error(msg.includes('uplicate') ? '❌ ' + msg : msg,
        { duration: msg.includes('uplicate') ? 5000 : 3000 });
    } finally { setLoading(false); }
  };

  const handleSaveAndSend = async (data) => {
    if (!mcq.aiGenerated) {
      const result = await getSimilarityCheckResult(data);
      if (!result) return;

      if (result.hasSimilar) {
        setSimilarityResult(result);
        return;
      }
    }

    setLoading('send');
    try {
      const canDirectAssign = mcq.status === 'DRAFT';

      if (canDirectAssign) {
        await adminAPI.editQuestion(mcq.questionId, { ...data, sendForReview: false });
        toast.success(t('questionUpdatedPleaseAssignReviewer'));
        onClose();
        onOpenAssign(mcq);
        return;
      }

      await adminAPI.editQuestion(mcq.questionId, { ...data, sendForReview: true });
      toast.success(t('questionUpdatedSentForReview'));
      onDone();
    } catch (err) {
        const msg = (err.response?.data?.detail || err.response?.data?.message) || t('failedToUpdate');
      toast.error(msg.includes('uplicate') ? '❌ ' + msg : msg,
        { duration: msg.includes('uplicate') ? 5000 : 3000 });
    } finally { setLoading(false); }
  };

  const initial = {
    questionStem: mcq.questionStem,
    questionType: mcq.questionType || 'MCQ',
    stackId: String(mcq.stackId),
    topicId: String(mcq.topicId),
    difficulty: mcq.difficulty,
    optionA: mcq.optionA,
    optionB: mcq.optionB,
    optionC: mcq.optionC,
    optionD: mcq.optionD,
    optionE: mcq.optionE || '',
    optionF: mcq.optionF || '',
    correctAnswer: mcq.correctAnswer,
  };

  return (
    <div className="modal-overlay" onClick={e => e.target === e.currentTarget && onClose()}>
      <div className="modal" style={{ maxWidth: 700 }}>
        <div className="modal-header">
          <div>
            <h2>{t('editQuestionTitle')}</h2>
            <p style={{ fontSize: 12, color: 'var(--text-muted)', marginTop: 2 }}>
              #{mcq.questionId} · {mcq.stackName} · {t('createdBy')} <strong style={{ fontFamily: 'var(--font-mono)' }}>{mcq.creatorEnterpriseId}</strong>
            </p>
          </div>
          <button className="modal-close" onClick={onClose}>×</button>
        </div>
        <div className="modal-body">
          <MCQForm
            initial={initial}
            onSave={handleSave}
            onSaveAndSend={handleSaveAndSend}
            onDuplicateCheck={handleDuplicateCheck}
            loading={loading}
            checkingDuplicate={checkingDuplicate}
            isEdit
            showDuplicateCheck={!mcq.aiGenerated}
            saveAndSendLabel={mcq.status === 'DRAFT' ? `👤 ${t('saveAndAssignReviewer')}` : `🚀 ${t('saveAndSendForReview')}`}
          />
        </div>
        {similarityResult && (
          <SimilarityResultModal
            result={similarityResult}
            onClose={() => setSimilarityResult(null)}
          />
        )}
      </div>
    </div>
  );
}

export default function QuestionBankPage() {
  const { user } = useAuth();
  const { t, language } = useLanguage();
  const [data, setData] = useState(null);
  const [statsData, setStatsData] = useState(null);
  const [page, setPage] = useState(0);
  const [loading, setLoading] = useState(true);
  const [viewMCQ, setViewMCQ] = useState(null);
  const [assignMCQ, setAssignMCQ] = useState(null);
  const [editMCQ, setEditMCQ] = useState(null);

  // Filters
  const [stacks, setStacks] = useState([]);
  const [topics, setTopics] = useState([]);
  const [filterStack, setFilterStack] = useState('');
  const [filterTopic, setFilterTopic] = useState('');
  const [filterDifficulty, setFilterDifficulty] = useState('');
  const [filterStatus, setFilterStatus] = useState('');
  const [search, setSearch] = useState('');
  const [searchInput, setSearchInput] = useState('');
  const shouldAutoRefresh = user?.role === 'ADMIN' || user?.role === 'SME';

  // Load stacks
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

  // Reset to page 0 when any filter changes
  useEffect(() => {
    setPage(0);
  }, [filterStack, filterTopic, filterDifficulty, filterStatus, search]);

  const fetchData = useCallback(async ({ background = false } = {}) => {
    if (!background) {
      setLoading(true);
    }
    try {
      const listFilters = {
        stackId: filterStack || undefined,
        topicId: filterTopic || undefined,
        difficulty: filterDifficulty || undefined,
        status: filterStatus || undefined,
        search: search || undefined,
      };

      // Status is intentionally excluded from stats to keep cards global across statuses.
      const statsFilters = {
        stackId: filterStack || undefined,
        topicId: filterTopic || undefined,
        difficulty: filterDifficulty || undefined,
        search: search || undefined,
      };

      const [res, statsRes] = await Promise.all([
        adminAPI.getQuestionBank(page, 10, listFilters),
        adminAPI.getQuestionBankStats(statsFilters),
      ]);

      // If current page is empty after assign (last item on page), go back one page
      if (res.data.content.length === 0 && page > 0) {
        setPage(p => p - 1);
        return;
      }
      setData(res.data);
      setStatsData(statsRes.data);
    } finally {
      if (!background) {
        setLoading(false);
      }
    }
  }, [page, filterStack, filterTopic, filterDifficulty, filterStatus, search, language]);

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

  const handleAssignDone = () => {
    setAssignMCQ(null);
    fetchData();
  };

  const hasFilters = filterStack || filterTopic || filterDifficulty || filterStatus || search;
  const clearFilters = () => {
    setFilterStack('');
    setFilterTopic('');
    setFilterDifficulty('');
    setFilterStatus('');
    setSearch('');
    setSearchInput('');
  };

  // Search on Enter or after 500ms debounce
  useEffect(() => {
    const t = setTimeout(() => setSearch(searchInput), 500);
    return () => clearTimeout(t);
  }, [searchInput]);

  const currentEnterpriseId = user?.enterpriseId;
  const canAssign = (mcq) =>
    mcq.status === 'READY_FOR_REVIEW'
    || (mcq.status === 'DRAFT' && mcq.creatorEnterpriseId === currentEnterpriseId);
  const canAdminEdit = (mcq) =>
    mcq.status !== 'DRAFT' || mcq.creatorEnterpriseId === currentEnterpriseId;

  const content = data?.content || [];

  return (
    <div>
      <div className="page-header">
        <div>
          <h1 className="page-title">{t('questionBankManagement')}</h1>
          <p className="page-subtitle">{t('viewAllQuestionsAssignReviewers')}</p>
        </div>
      </div>

      {/* Stats */}
      {statsData && (
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(180px, 1fr))', gap: 16, marginBottom: 24 }}>
          {[
            { label: t('totalQuestions'), value: statsData.total || 0, icon: '📚', color: '#5b21b6', bg: '#ede9ff' },
            { label: t('readyForReview'), value: statsData.readyForReview || 0, icon: '📤', color: '#1d4ed8', bg: '#dbeafe' },
            { label: t('underReview'),    value: statsData.underReview || 0,    icon: '🔍', color: '#92400e', bg: '#fef3c7' },
            { label: t('approved'),       value: statsData.approved || 0,       icon: '✅', color: '#065f46', bg: '#d1fae5' },
          ].map(s => (
            <div key={s.label} className="card" style={{ padding: '16px 20px', display: 'flex', alignItems: 'center', gap: 14 }}>
              <div style={{ width: 42, height: 42, background: s.bg, borderRadius: 10, display: 'flex', alignItems: 'center', justifyContent: 'center', fontSize: 20, flexShrink: 0 }}>
                {s.icon}
              </div>
              <div>
                <div style={{ fontSize: 24, fontWeight: 800, color: s.color, lineHeight: 1 }}>{s.value}</div>
                <div style={{ fontSize: 12, color: 'var(--text-muted)', marginTop: 2 }}>{s.label}</div>
              </div>
            </div>
          ))}
        </div>
      )}

      {/* Filters */}
      <div className="questions-filter-row" style={{ display: 'flex', gap: 10, marginBottom: 16, flexWrap: 'wrap', alignItems: 'center' }}>
        <input
          className="form-input"
          style={{ width: 240 }}
          placeholder={t('searchQuestionStackCreatorPlaceholder')}
          value={searchInput}
          onChange={e => setSearchInput(e.target.value)}
        />
        <select className="form-select" style={{ width: 160 }}
          value={filterStack}
          onChange={e => { setFilterStack(e.target.value); setFilterTopic(''); }}>
          <option value="">{t('allStacks')}</option>
          {stacks.map(s => <option key={s.stackId} value={s.stackId}>{s.stackName}</option>)}
        </select>
        <select className="form-select" style={{ width: 200 }}
          value={filterTopic}
          onChange={e => setFilterTopic(e.target.value)}
          disabled={!filterStack}>
          <option value="">{t('allTopics')}</option>
          {topics.map(t => <option key={t.topicId} value={t.topicId}>{t.topicName}</option>)}
        </select>
        <select className="form-select" style={{ width: 140 }}
          value={filterDifficulty}
          onChange={e => setFilterDifficulty(e.target.value)}>
          <option value="">{t('allDifficulties')}</option>
          <option value="Easy">{t('easy')}</option>
          <option value="Medium">{t('medium')}</option>
          <option value="Hard">{t('hard')}</option>
        </select>
        <select className="form-select" style={{ width: 180 }}
          value={filterStatus}
          onChange={e => setFilterStatus(e.target.value)}>
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
      </div>

      <div className="card" style={{ padding: 0 }}>
        {loading && !content.length ? (
          <div className="loading-container"><div className="spinner spinner-dark" /></div>
        ) : !content.length ? (
          <div className="empty-state">
            <div className="empty-icon">{hasFilters ? '🔍' : '📭'}</div>
            <h3>{hasFilters ? t('noMatchingQuestions') : t('noQuestionsYet')}</h3>
            <p>{hasFilters ? t('tryAdjustingFilters') : t('questionsAppearOnceCreated')}</p>
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
                    <th>{t('stackTopic')}</th>
                    <th>{t('difficulty')}</th>
                    <th>{t('creator')}</th>
                    <th>{t('reviewer')}</th>
                    <th>{t('status')}</th>
                    <th>{t('actions')}</th>
                  </tr>
                </thead>
                <tbody>
                  {content.map((mcq, idx) => (
                    <tr key={mcq.questionId}>
                      <td data-label="#" style={{ color: 'var(--text-muted)', fontFamily: 'var(--font-mono)', fontSize: 12 }}>
                        {page * 10 + idx + 1}
                      </td>
                      <td data-label={t('type')}><QuestionTypeBadge type={mcq.questionType} aiGenerated={mcq.aiGenerated} bulkUploaded={mcq.bulkUploaded} /></td>
                      <td data-label={t('question')} style={{ maxWidth: 260 }}>
                        <div style={{ display: '-webkit-box', WebkitLineClamp: 2, WebkitBoxOrient: 'vertical', overflow: 'hidden', fontSize: 13, lineHeight: 1.5 }} title={mcq.questionStem}>
                          {mcq.questionStem}
                        </div>
                      </td>
                      <td data-label={t('stackTopic')}>
                        <div style={{ display: 'flex', flexDirection: 'column', gap: 3 }}>
                          <span style={{ fontSize: 12, background: '#ede9ff', color: '#5b21b6', padding: '2px 8px', borderRadius: 4, fontWeight: 600, display: 'inline-block' }}>{mcq.stackName}</span>
                          <span style={{ fontSize: 11, color: 'var(--text-muted)' }}>{mcq.topicName}</span>
                        </div>
                      </td>
                      <td data-label={t('difficulty')}><DifficultyBadge difficulty={mcq.difficulty} /></td>
                      <td data-label={t('creator')} style={{ fontFamily: 'var(--font-mono)', fontSize: 11, color: 'var(--text-secondary)' }}>
                        {mcq.creatorEnterpriseId}
                      </td>
                      <td data-label={t('reviewer')} style={{ fontFamily: 'var(--font-mono)', fontSize: 11, color: 'var(--text-secondary)' }}>
                        {mcq.reviewerEnterpriseId || <span style={{ color: 'var(--text-muted)' }}>—</span>}
                      </td>
                      <td data-label={t('status')}><StatusBadge status={mcq.status} /></td>
                      <td data-label={t('actions')}>
                        <div style={{ display: 'flex', gap: 6 }}>
                          <button className="btn btn-ghost btn-sm" onClick={() => setViewMCQ(mcq)}>👁</button>
                          {canAssign(mcq) && (
                            <button className="btn btn-primary btn-sm" onClick={() => setAssignMCQ(mcq)}>
                                👤 {t('assign')}
                            </button>
                          )}
                          {canAdminEdit(mcq) && (
                            <button className="btn btn-secondary btn-sm" onClick={() => setEditMCQ(mcq)}>
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
                {t('showing')} {content.length} {t('of')} {data.totalElements} {t('questions')}
                {hasFilters && ` (${t('filtered')})`}
              </span>
              <Pagination page={page} totalPages={data.totalPages} onPageChange={setPage} />
            </div>
          </>
        )}
      </div>

      {viewMCQ && <ViewMCQModal mcq={viewMCQ} onClose={() => setViewMCQ(null)} />}
      {assignMCQ && <AssignReviewerModal mcq={assignMCQ} onClose={() => setAssignMCQ(null)} onDone={handleAssignDone} />}
      {editMCQ && <AdminEditModal mcq={editMCQ} onClose={() => setEditMCQ(null)} onOpenAssign={setAssignMCQ} onDone={() => { setEditMCQ(null); fetchData(); }} />}
    </div>
  );
}