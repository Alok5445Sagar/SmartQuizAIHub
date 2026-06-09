import React from 'react';
import StatusBadge, { DifficultyBadge, QuestionTypeBadge } from './StatusBadge';


export default function ViewMCQModal({ mcq, onClose }) {
  return (
    <div className="modal-overlay" onClick={e => e.target === e.currentTarget && onClose()}>
      <div className="modal" style={{ maxWidth: 640 }}>
        <div className="modal-header">
          <h2>Question Details</h2>
          <button className="modal-close" onClick={onClose}>×</button>
        </div>
        <div className="modal-body">
          <div style={{ display: 'flex', gap: 8, marginBottom: 16, flexWrap: 'wrap' }}>
            <StatusBadge status={mcq.status} />
            <DifficultyBadge difficulty={mcq.difficulty} />
            <span style={{ fontSize: 12, background: '#ede9ff', color: '#5b21b6', padding: '2px 8px', borderRadius: 4, fontWeight: 600 }}>{mcq.stackName}</span>
          </div>

          <div style={{ background: 'var(--bg)', borderRadius: 10, padding: 16, marginBottom: 16 }}>
            <p style={{ fontSize: 13, fontWeight: 600, color: 'var(--text-secondary)', marginBottom: 8 }}>QUESTION</p>
            <p style={{ fontSize: 15, lineHeight: 1.6 }}>{mcq.questionStem}</p>
          </div>

          <div style={{ marginBottom: 16 }}>
            <div style={{ display: 'flex', alignItems: 'center', gap: 10, marginBottom: 10 }}>
              <p style={{ fontSize: 13, fontWeight: 600, color: 'var(--text-secondary)' }}>OPTIONS</p>
              <QuestionTypeBadge type={mcq.questionType} aiGenerated={mcq.aiGenerated} />
            </div>
              <div className="options-grid">
                {['A', 'B', 'C', 'D', 'E', 'F'].map(opt => {
                  const optValue = mcq[`option${opt}`];
                  if (!optValue) return null;
                  const correctAnswers = mcq.correctAnswer ? mcq.correctAnswer.split(',').map(s => s.trim()) : [];
                  const isCorrect = correctAnswers.includes(opt);
                  return (
                    <div key={opt} className={`option-item ${isCorrect ? 'correct' : ''}`}>
                      <span className="option-label">{opt}</span>
                      <span style={{ fontSize: 13 }}>{optValue}</span>
                      {isCorrect && <span style={{ marginLeft: 'auto', fontSize: 14 }}>✓</span>}
                    </div>
                  );
                })}
            </div>
            {mcq.questionType === 'MSQ' && (
              <p style={{ fontSize: 12, color: 'var(--success)', marginTop: 8 }}>
                ✓ Correct answers: {mcq.correctAnswer}
              </p>
            )}
          </div>

          <div className="grid-2" style={{ fontSize: 13, marginBottom: 12 }}>
            <div>
              <p style={{ color: 'var(--text-muted)', marginBottom: 2 }}>Topic</p>
              <p style={{ fontWeight: 600 }}>{mcq.topicName}</p>
            </div>
            <div>
              <p style={{ color: 'var(--text-muted)', marginBottom: 2 }}>Created by</p>
              <p style={{ fontWeight: 600, fontFamily: 'var(--font-mono)', fontSize: 12 }}>{mcq.creatorEnterpriseId}</p>
            </div>
            {mcq.reviewerEnterpriseId && (
              <div>
                <p style={{ color: 'var(--text-muted)', marginBottom: 2 }}>Reviewer</p>
                <p style={{ fontWeight: 600, fontFamily: 'var(--font-mono)', fontSize: 12 }}>{mcq.reviewerEnterpriseId}</p>
              </div>
            )}
          </div>

          {mcq.reviewerComments && (
            <div className="alert alert-error">
              <div>
                <strong>Reviewer Feedback:</strong>
                <p style={{ marginTop: 4 }}>{mcq.reviewerComments}</p>
              </div>
            </div>
          )}
        </div>
        <div className="modal-footer">
          <button className="btn btn-secondary" onClick={onClose}>Close</button>
        </div>
      </div>
    </div>
  );
}
