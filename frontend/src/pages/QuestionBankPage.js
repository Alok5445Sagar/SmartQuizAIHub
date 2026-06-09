import React, { useState, useEffect, useCallback } from 'react';
import { adminAPI, referenceAPI } from '../services/api';
import MCQForm from '../components/MCQForm';
import StatusBadge, { DifficultyBadge } from '../components/StatusBadge';
import Pagination from '../components/Pagination';
import ViewMCQModal from '../components/ViewMCQModal';
import toast from 'react-hot-toast';
import { QuestionTypeBadge } from '../components/StatusBadge';

function AssignReviewerModal({ mcq, onClose, onDone }) {
  const [reviewers, setReviewers] = useState([]);
  const [selectedReviewer, setSelectedReviewer] = useState('');
  const [loading, setLoading] = useState(false);
  const [fetching, setFetching] = useState(true);

  useEffect(() => {
    adminAPI.getReviewers(mcq.stackId, mcq.creatorEnterpriseId)
      .then(r => setReviewers(r.data))
      .catch(() => toast.error('Failed to load reviewers'))
      .finally(() => setFetching(false));
  }, [mcq.stackId, mcq.creatorEnterpriseId]);

  const handleAssign = async () => {
    if (!selectedReviewer) return toast.error('Please select a reviewer');
    setLoading(true);
    try {
      await adminAPI.assignReviewer(mcq.questionId, selectedReviewer);
      toast.success('Reviewer assigned successfully!');
      onDone();
    } catch (err) {
      toast.error(err.response?.data?.message || 'Failed to assign reviewer');
    } finally { setLoading(false); }
  };

  return (
    <div className="modal-overlay" onClick={e => e.target === e.currentTarget && onClose()}>
      <div className="modal" style={{ maxWidth: 500 }}>
        <div className="modal-header">
          <h2>Assign Reviewer</h2>
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
              Created by: <strong style={{ fontFamily: 'var(--font-mono)' }}>{mcq.creatorEnterpriseId}</strong>
            </p>
          </div>

          <div className="form-group">
            <label className="form-label">Select Reviewer *</label>
            <p style={{ fontSize: 12, color: 'var(--text-muted)', marginBottom: 10 }}>
              Showing SMEs with <strong>{mcq.stackName}</strong> expertise (excluding the creator)
            </p>
            {fetching ? (
              <div style={{ textAlign: 'center', padding: 20 }}><div className="spinner spinner-dark" style={{ margin: '0 auto' }} /></div>
            ) : reviewers.length === 0 ? (
              <div className="alert alert-info">No eligible reviewers found for this stack.</div>
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
          <button className="btn btn-secondary" onClick={onClose}>Cancel</button>
          <button className="btn btn-primary" onClick={handleAssign} disabled={loading || !selectedReviewer || fetching}>
            {loading ? <><span className="spinner" /> Assigning...</> : '👤 Assign Reviewer'}
          </button>
        </div>
      </div>
    </div>
  );
}

function AdminEditModal({ mcq, onClose, onDone }) {
  const [loading, setLoading] = useState(false);

  const handleSave = async (data) => {
    setLoading('save');
    try {
      await adminAPI.editQuestion(mcq.questionId, { ...data, sendForReview: false });
      toast.success('Question updated!');
      onDone();
    } catch (err) {
      const msg = err.response?.data?.message || 'Failed to update';
      toast.error(msg.includes('uplicate') ? '❌ ' + msg : msg,
        { duration: msg.includes('uplicate') ? 5000 : 3000 });
    } finally { setLoading(false); }
  };

  const handleSaveAndSend = async (data) => {
    setLoading('send');
    try {
      await adminAPI.editQuestion(mcq.questionId, { ...data, sendForReview: true });
      toast.success('Question updated and sent for review!');
      onDone();
    } catch (err) {
      const msg = err.response?.data?.message || 'Failed to update';
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
            <h2>Edit Question</h2>
            <p style={{ fontSize: 12, color: 'var(--text-muted)', marginTop: 2 }}>
              #{mcq.questionId} · {mcq.stackName} · Created by <strong style={{ fontFamily: 'var(--font-mono)' }}>{mcq.creatorEnterpriseId}</strong>
            </p>
          </div>
          <button className="modal-close" onClick={onClose}>×</button>
        </div>
        <div className="modal-body">
          <MCQForm
            initial={initial}
            onSave={handleSave}
            onSaveAndSend={handleSaveAndSend}
            loading={loading}
            isEdit
          />
        </div>
      </div>
    </div>
  );
}

export default function QuestionBankPage() {
  const [data, setData] = useState(null);
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

  const fetchData = useCallback(async () => {
    setLoading(true);
    try {
      const res = await adminAPI.getQuestionBank(page, 10, {
        stackId: filterStack || undefined,
        topicId: filterTopic || undefined,
        difficulty: filterDifficulty || undefined,
        status: filterStatus || undefined,
        search: search || undefined,
      });
      // If current page is empty after assign (last item on page), go back one page
      if (res.data.content.length === 0 && page > 0) {
        setPage(p => p - 1);
        return;
      }
      setData(res.data);
    } finally { setLoading(false); }
  }, [page, filterStack, filterTopic, filterDifficulty, filterStatus, search]);

  useEffect(() => { fetchData(); }, [fetchData]);

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

  const canAssign = (status) => status === 'READY_FOR_REVIEW';
  const canAdminEdit = (status) => status !== 'DRAFT';

  const content = data?.content || [];

  // Stats from full data (use totalElements for total; others from current page for display)
  const stats = data ? {
    total: data.totalElements,
    approved: content.filter(m => m.status === 'APPROVED').length,
    pending: content.filter(m => m.status === 'READY_FOR_REVIEW').length,
    underReview: content.filter(m => m.status === 'UNDER_REVIEW').length,
  } : null;

  return (
    <div>
      <div className="page-header">
        <div>
          <h1 className="page-title">Question Bank Management</h1>
          <p className="page-subtitle">View all questions and assign reviewers</p>
        </div>
      </div>

      {/* Stats */}
      {stats && (
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(4, 1fr)', gap: 16, marginBottom: 24 }}>
          {[
            { label: 'Total Questions', value: data.totalElements, icon: '📚', color: '#5b21b6', bg: '#ede9ff' },
            { label: 'Ready for Review', value: content.filter(m => m.status === 'READY_FOR_REVIEW').length, icon: '📤', color: '#1d4ed8', bg: '#dbeafe' },
            { label: 'Under Review',     value: content.filter(m => m.status === 'UNDER_REVIEW').length,    icon: '🔍', color: '#92400e', bg: '#fef3c7' },
            { label: 'Approved',         value: content.filter(m => m.status === 'APPROVED').length,        icon: '✅', color: '#065f46', bg: '#d1fae5' },
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
      <div style={{ display: 'flex', gap: 10, marginBottom: 16, flexWrap: 'wrap', alignItems: 'center' }}>
        <input
          className="form-input"
          style={{ width: 240 }}
          placeholder="🔎 Search question, stack, creator..."
          value={searchInput}
          onChange={e => setSearchInput(e.target.value)}
        />
        <select className="form-select" style={{ width: 160 }}
          value={filterStack}
          onChange={e => { setFilterStack(e.target.value); setFilterTopic(''); }}>
          <option value="">All Stacks</option>
          {stacks.map(s => <option key={s.stackId} value={s.stackId}>{s.stackName}</option>)}
        </select>
        <select className="form-select" style={{ width: 200 }}
          value={filterTopic}
          onChange={e => setFilterTopic(e.target.value)}
          disabled={!filterStack}>
          <option value="">All Topics</option>
          {topics.map(t => <option key={t.topicId} value={t.topicId}>{t.topicName}</option>)}
        </select>
        <select className="form-select" style={{ width: 140 }}
          value={filterDifficulty}
          onChange={e => setFilterDifficulty(e.target.value)}>
          <option value="">All Difficulties</option>
          <option value="Easy">Easy</option>
          <option value="Medium">Medium</option>
          <option value="Hard">Hard</option>
        </select>
        <select className="form-select" style={{ width: 180 }}
          value={filterStatus}
          onChange={e => setFilterStatus(e.target.value)}>
          <option value="">All Statuses</option>
          <option value="DRAFT">Draft</option>
          <option value="READY_FOR_REVIEW">Ready for Review</option>
          <option value="UNDER_REVIEW">Under Review</option>
          <option value="APPROVED">Approved</option>
          <option value="REJECTED">Rejected</option>
        </select>
        {hasFilters && (
          <button className="btn btn-ghost btn-sm" onClick={clearFilters}>✕ Clear</button>
        )}
      </div>

      <div className="card" style={{ padding: 0 }}>
        {loading ? (
          <div className="loading-container"><div className="spinner spinner-dark" /></div>
        ) : !content.length ? (
          <div className="empty-state">
            <div className="empty-icon">{hasFilters ? '🔍' : '📭'}</div>
            <h3>{hasFilters ? 'No matching questions' : 'No questions yet'}</h3>
            <p>{hasFilters ? 'Try adjusting your filters' : 'Questions will appear here once SMEs create them'}</p>
          </div>
        ) : (
          <>
            <div className="table-wrapper">
              <table>
                <thead>
                  <tr>
                    <th>#</th>
                    <th>Type</th>
                    <th>Question</th>
                    <th>Stack / Topic</th>
                    <th>Difficulty</th>
                    <th>Creator</th>
                    <th>Reviewer</th>
                    <th>Status</th>
                    <th>Actions</th>
                  </tr>
                </thead>
                <tbody>
                  {content.map((mcq, idx) => (
                    <tr key={mcq.questionId}>
                      <td style={{ color: 'var(--text-muted)', fontFamily: 'var(--font-mono)', fontSize: 12 }}>
                        {page * 10 + idx + 1}
                      </td>
                      <td><QuestionTypeBadge type={mcq.questionType} aiGenerated={mcq.aiGenerated} /></td>
                      <td style={{ maxWidth: 260 }}>
                        <div style={{ display: '-webkit-box', WebkitLineClamp: 2, WebkitBoxOrient: 'vertical', overflow: 'hidden', fontSize: 13, lineHeight: 1.5 }} title={mcq.questionStem}>
                          {mcq.questionStem}
                        </div>
                      </td>
                      <td>
                        <div style={{ display: 'flex', flexDirection: 'column', gap: 3 }}>
                          <span style={{ fontSize: 12, background: '#ede9ff', color: '#5b21b6', padding: '2px 8px', borderRadius: 4, fontWeight: 600, display: 'inline-block' }}>{mcq.stackName}</span>
                          <span style={{ fontSize: 11, color: 'var(--text-muted)' }}>{mcq.topicName}</span>
                        </div>
                      </td>
                      <td><DifficultyBadge difficulty={mcq.difficulty} /></td>
                      <td style={{ fontFamily: 'var(--font-mono)', fontSize: 11, color: 'var(--text-secondary)' }}>
                        {mcq.creatorEnterpriseId}
                      </td>
                      <td style={{ fontFamily: 'var(--font-mono)', fontSize: 11, color: 'var(--text-secondary)' }}>
                        {mcq.reviewerEnterpriseId || <span style={{ color: 'var(--text-muted)' }}>—</span>}
                      </td>
                      <td><StatusBadge status={mcq.status} /></td>
                      <td>
                        <div style={{ display: 'flex', gap: 6 }}>
                          <button className="btn btn-ghost btn-sm" onClick={() => setViewMCQ(mcq)}>👁</button>
                          {canAssign(mcq.status) && (
                            <button className="btn btn-primary btn-sm" onClick={() => setAssignMCQ(mcq)}>
                              👤 Assign
                            </button>
                          )}
                          {canAdminEdit(mcq.status) && (
                            <button className="btn btn-secondary btn-sm" onClick={() => setEditMCQ(mcq)}>
                              ✏️ Edit
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
                Showing {content.length} of {data.totalElements} questions
                {hasFilters && ' (filtered)'}
              </span>
              <Pagination page={page} totalPages={data.totalPages} onPageChange={setPage} />
            </div>
          </>
        )}
      </div>

      {viewMCQ && <ViewMCQModal mcq={viewMCQ} onClose={() => setViewMCQ(null)} />}
      {assignMCQ && <AssignReviewerModal mcq={assignMCQ} onClose={() => setAssignMCQ(null)} onDone={handleAssignDone} />}
      {editMCQ && <AdminEditModal mcq={editMCQ} onClose={() => setEditMCQ(null)} onDone={() => { setEditMCQ(null); fetchData(); }} />}
    </div>
  );
}