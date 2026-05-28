import React, { useState, useEffect, useCallback } from 'react';
import { mcqAPI } from '../services/api';
import { useAuth } from '../context/AuthContext';
import { DifficultyBadge } from '../components/StatusBadge';
import Pagination from '../components/Pagination';
import ViewMCQModal from '../components/ViewMCQModal';
import toast from 'react-hot-toast';

function ReviewModal({ mcq, onClose, onDone }) {
  const [action, setAction] = useState('');
  const [comments, setComments] = useState('');
  const [loading, setLoading] = useState(false);

  const handleSubmit = async () => {
    if (!action) return toast.error('Select Approve or Reject');
    if (action === 'REJECT' && !comments.trim()) return toast.error('Comments are required for rejection');
    setLoading(true);
    try {
      await mcqAPI.review(mcq.questionId, { action, comments });
      toast.success(action === 'APPROVE' ? '✅ Question approved!' : '❌ Question rejected');
      onDone();
    } catch (err) {
      toast.error(err.response?.data?.message || 'Review failed');
    } finally { setLoading(false); }
  };

  return (
    <div className="modal-overlay" onClick={e => e.target === e.currentTarget && onClose()}>
      <div className="modal" style={{ maxWidth: 600 }}>
        <div className="modal-header">
          <h2>Review Question</h2>
          <button className="modal-close" onClick={onClose}>×</button>
        </div>
        <div className="modal-body">
          {/* Question preview */}
          <div style={{ background: 'var(--bg)', borderRadius: 10, padding: 16, marginBottom: 20 }}>
            <div style={{ display: 'flex', gap: 8, marginBottom: 10 }}>
              <span style={{ fontSize: 12, background: '#ede9ff', color: '#5b21b6', padding: '2px 8px', borderRadius: 4, fontWeight: 600 }}>{mcq.stackName}</span>
              <span style={{ fontSize: 12, color: 'var(--text-muted)' }}>{mcq.topicName}</span>
              <DifficultyBadge difficulty={mcq.difficulty} />
            </div>
            <p style={{ fontSize: 14, lineHeight: 1.6, marginBottom: 12 }}>{mcq.questionStem}</p>
            <div className="options-grid">
              {['A','B','C','D'].map(opt => (
                <div key={opt} className={`option-item ${mcq.correctAnswer === opt ? 'correct' : ''}`}>
                  <span className="option-label">{opt}</span>
                  <span style={{ fontSize: 12 }}>{mcq[`option${opt}`]}</span>
                  {mcq.correctAnswer === opt && <span style={{ marginLeft: 'auto' }}>✓</span>}
                </div>
              ))}
            </div>
          </div>

          {/* Action */}
          <div className="form-group">
            <label className="form-label">Decision *</label>
            <div style={{ display: 'flex', gap: 10 }}>
              <button
                className={`btn ${action === 'APPROVE' ? 'btn-success' : 'btn-secondary'}`}
                style={{ flex: 1, justifyContent: 'center' }}
                onClick={() => setAction('APPROVE')}
              >
                ✅ Approve
              </button>
              <button
                className={`btn ${action === 'REJECT' ? 'btn-danger' : 'btn-secondary'}`}
                style={{ flex: 1, justifyContent: 'center' }}
                onClick={() => setAction('REJECT')}
              >
                ❌ Reject
              </button>
            </div>
          </div>

          <div className="form-group">
            <label className="form-label">
              Feedback Comments {action === 'REJECT' && <span style={{ color: 'var(--danger)' }}>*</span>}
            </label>
            <textarea
              className="form-textarea"
              rows={3}
              placeholder={action === 'REJECT' ? 'Rejection reason is mandatory...' : 'Optional feedback for the creator...'}
              value={comments}
              onChange={e => setComments(e.target.value)}
            />
          </div>
        </div>
        <div className="modal-footer">
          <button className="btn btn-secondary" onClick={onClose}>Cancel</button>
          <button className="btn btn-primary" onClick={handleSubmit} disabled={loading || !action}>
            {loading ? <><span className="spinner" /> Submitting...</> : 'Submit Review'}
          </button>
        </div>
      </div>
    </div>
  );
}

export default function MyPendingReviewsPage() {
  const { user } = useAuth();
  const isAdmin = user?.role === 'ADMIN';
  const [data, setData] = useState(null);
  const [page, setPage] = useState(0);
  const [loading, setLoading] = useState(true);
  const [viewMCQ, setViewMCQ] = useState(null);
  const [reviewMCQ, setReviewMCQ] = useState(null);

  const fetchData = useCallback(async () => {
    setLoading(true);
    try {
      const res = await mcqAPI.getMyPendingReviews(page, 10);
      setData(res.data);
    } finally { setLoading(false); }
  }, [page]);

  useEffect(() => { fetchData(); }, [fetchData]);

  const handleReviewDone = () => {
    setReviewMCQ(null);
    fetchData();
  };

  return (
    <div>
      <div className="page-header">
        <div>
          <h1 className="page-title">My Pending Reviews</h1>
          <p className="page-subtitle">MCQs assigned to you for review</p>
        </div>
        {data && (
          <div style={{ background: 'var(--warning-light)', color: '#92400e', padding: '6px 14px', borderRadius: 100, fontSize: 13, fontWeight: 600 }}>
            🔍 {data.totalElements} Pending
          </div>
        )}
      </div>

      <div className="card" style={{ padding: 0 }}>
        {loading ? (
          <div className="loading-container"><div className="spinner spinner-dark" /></div>
        ) : !data?.content?.length ? (
          <div className="empty-state">
            <div className="empty-icon">🎉</div>
            <h3>No pending reviews</h3>
            <p>You're all caught up! No MCQs assigned for review.</p>
          </div>
        ) : (
          <>
            <div className="table-wrapper">
              <table>
                <thead>
                  <tr>
                    <th>#</th>
                    <th>Question</th>
                    <th>Stack</th>
                    <th>Topic</th>
                    <th>Difficulty</th>
                    {isAdmin && <th>Creator</th>}
                    <th>Status</th>
                    <th>Actions</th>
                  </tr>
                </thead>
                <tbody>
                  {data.content.map((mcq, idx) => (
                    <tr key={mcq.questionId}>
                      <td style={{ color: 'var(--text-muted)', fontFamily: 'var(--font-mono)', fontSize: 12 }}>
                        {page * 10 + idx + 1}
                      </td>
                      <td>
                        <div className="truncate" style={{ maxWidth: 260 }} title={mcq.questionStem}>
                          {mcq.questionStem}
                        </div>
                      </td>
                      <td><span style={{ fontSize: 12, background: '#ede9ff', color: '#5b21b6', padding: '2px 8px', borderRadius: 4 }}>{mcq.stackName}</span></td>
                      <td style={{ color: 'var(--text-secondary)', fontSize: 12 }}>{mcq.topicName}</td>
                      <td><DifficultyBadge difficulty={mcq.difficulty} /></td>
                      {isAdmin && <td style={{ fontFamily: 'var(--font-mono)', fontSize: 11, color: 'var(--text-secondary)' }}>{mcq.creatorEnterpriseId}</td>}
                      <td>
                        <span className="badge badge-pending">🔍 Pending</span>
                      </td>
                      <td>
                        <div style={{ display: 'flex', gap: 6 }}>
                          <button className="btn btn-ghost btn-sm" onClick={() => setViewMCQ(mcq)}>
                            👁 View Full Question
                          </button>
                          <button className="btn btn-primary btn-sm" onClick={() => setReviewMCQ(mcq)}>
                            📝 Review
                          </button>
                        </div>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
            <div style={{ padding: '12px 16px', borderTop: '1px solid var(--border)', display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
              <span style={{ fontSize: 12, color: 'var(--text-muted)' }}>
                Showing {data.content.length} of {data.totalElements} reviews
              </span>
              <Pagination page={page} totalPages={data.totalPages} onPageChange={setPage} />
            </div>
          </>
        )}
      </div>

      {viewMCQ && <ViewMCQModal mcq={viewMCQ} onClose={() => setViewMCQ(null)} />}
      {reviewMCQ && <ReviewModal mcq={reviewMCQ} onClose={() => setReviewMCQ(null)} onDone={handleReviewDone} />}
    </div>
  );
}
