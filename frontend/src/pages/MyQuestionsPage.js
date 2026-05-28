import React, { useState, useEffect, useCallback } from 'react';
import { useNavigate, useLocation } from 'react-router-dom';
import { mcqAPI, referenceAPI } from '../services/api';
import StatusBadge, { DifficultyBadge } from '../components/StatusBadge';
import Pagination from '../components/Pagination';
import ViewMCQModal from '../components/ViewMCQModal';
import { QuestionTypeBadge } from '../components/StatusBadge';

export default function MyQuestionsPage() {
  const [data, setData] = useState(null);
  const [page, setPage] = useState(0);
  const [loading, setLoading] = useState(true);
  const [viewMCQ, setViewMCQ] = useState(null);
  const navigate = useNavigate();
  const location = useLocation();

  // Filters
  const [stacks, setStacks] = useState([]);
  const [topics, setTopics] = useState([]);
  const [filterStack, setFilterStack] = useState('');
  const [filterTopic, setFilterTopic] = useState('');
  const [filterDifficulty, setFilterDifficulty] = useState('');
  const [filterStatus, setFilterStatus] = useState('');

  // Restore page from navigation state (coming back from edit)
  useEffect(() => {
    if (location.state?.returnToPage !== undefined) {
      setPage(location.state.returnToPage);
    }
  }, []);

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

    // Reset to page 0 whenever a filter changes
  useEffect(() => {
    setPage(0);
  }, [filterStack, filterTopic, filterDifficulty, filterStatus]);

  const fetchData = useCallback(async () => {
    setLoading(true);
    try {
      const res = await mcqAPI.getMyQuestions(page, 10, {
        stackId: filterStack || undefined,
        topicId: filterTopic || undefined,
        difficulty: filterDifficulty || undefined,
        status: filterStatus || undefined,
      });
      // If current page is empty after edit (e.g. last item on last page),
      // go back one page
      if (res.data.content.length === 0 && page > 0) {
        setPage(p => p - 1);
        return;
      }
      setData(res.data);
    } finally {
      setLoading(false);
    }
  }, [page, filterStack, filterTopic, filterDifficulty, filterStatus]);

  useEffect(() => { fetchData(); }, [fetchData]);

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
  };

  return (
    <div>
      <div className="page-header">
        <div>
          <h1 className="page-title">My Questions</h1>
          <p className="page-subtitle">All questions created by you</p>
        </div>
        <button className="btn btn-primary" onClick={() => navigate('/add-question')}>
          ➕ Add Question
        </button>
      </div>

      {/* Filters */}
      <div style={{ display: 'flex', gap: 10, marginBottom: 16, flexWrap: 'wrap', alignItems: 'center' }}>
        <select
          className="form-select"
          style={{ width: 160 }}
          value={filterStack}
          onChange={e => { setFilterStack(e.target.value); setFilterTopic(''); }}
        >
          <option value="">All Stacks</option>
          {stacks.map(s => <option key={s.stackId} value={s.stackId}>{s.stackName}</option>)}
        </select>

        <select
          className="form-select"
          style={{ width: 200 }}
          value={filterTopic}
          onChange={e => setFilterTopic(e.target.value)}
          disabled={!filterStack}
        >
          <option value="">All Topics</option>
          {topics.map(t => <option key={t.topicId} value={t.topicId}>{t.topicName}</option>)}
        </select>

        <select
          className="form-select"
          style={{ width: 140 }}
          value={filterDifficulty}
          onChange={e => setFilterDifficulty(e.target.value)}
        >
          <option value="">All Difficulties</option>
          <option value="Easy">Easy</option>
          <option value="Medium">Medium</option>
          <option value="Hard">Hard</option>
        </select>

        <select
          className="form-select"
          style={{ width: 180 }}
          value={filterStatus}
          onChange={e => setFilterStatus(e.target.value)}
        >
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

        {hasFilters && (
          <span style={{ fontSize: 12, color: 'var(--text-muted)' }}>
            {filtered.length} match{filtered.length !== 1 ? 'es' : ''} on this page
          </span>
        )}
      </div>

      <div className="card" style={{ padding: 0 }}>
        {loading ? (
          <div className="loading-container"><div className="spinner spinner-dark" /></div>
        ) : !data?.content?.length ? (
          <div className="empty-state">
            <div className="empty-icon">📝</div>
            <h3>No questions yet</h3>
            <p>Create your first question by clicking "Add Question"</p>
          </div>
        ) : filtered.length === 0 ? (
          <div className="empty-state">
            <div className="empty-icon">🔍</div>
            <h3>No matches on this page</h3>
            <p>Try changing the filters or browsing other pages</p>
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
                    <th>Stack</th>
                    <th>Topic</th>
                    <th>Difficulty</th>
                    <th>Status</th>
                    <th>Actions</th>
                  </tr>
                </thead>
                <tbody>
                  {filtered.map((mcq, idx) => (
                    <tr key={mcq.questionId}>
                      <td style={{ color: 'var(--text-muted)', fontFamily: 'var(--font-mono)', fontSize: 12 }}>
                        {page * 10 + idx + 1}
                      </td>
                      <td><QuestionTypeBadge type={mcq.questionType} /></td>
                      <td>
                        <div className="truncate" title={mcq.questionStem}>
                          {mcq.questionStem}
                        </div>
                      </td>
                      <td>
                        <span style={{ fontSize: 12, background: '#ede9ff', color: '#5b21b6', padding: '2px 8px', borderRadius: 4 }}>
                          {mcq.stackName}
                        </span>
                      </td>
                      <td style={{ color: 'var(--text-secondary)', fontSize: 12 }}>{mcq.topicName}</td>
                      <td><DifficultyBadge difficulty={mcq.difficulty} /></td>
                      <td><StatusBadge status={mcq.status} /></td>
                      <td>
                        <div style={{ display: 'flex', gap: 6 }}>
                          <button className="btn btn-ghost btn-sm" onClick={() => setViewMCQ(mcq)}>
                            👁 View
                          </button>
                          {canEdit(mcq.status) && (
                            <button
                              className="btn btn-secondary btn-sm"
                              onClick={() => navigate(`/edit-question/${mcq.questionId}`, {
                                state: { returnToPage: page }
                              })}
                            >
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
                Showing {data.content.length} of {data.totalElements} questions
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