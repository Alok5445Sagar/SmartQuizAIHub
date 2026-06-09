import React, { useState, useEffect } from 'react';
import { useNavigate, useParams, useLocation } from 'react-router-dom';
import { mcqAPI, aiAPI } from '../services/api';
import MCQForm from '../components/MCQForm';
import StatusBadge from '../components/StatusBadge';
import toast from 'react-hot-toast';

function SimilarityResultModal({ result, onClose, onProceed }) {
  return (
    <div className="modal-overlay" onClick={e => e.target === e.currentTarget && onClose()}>
      <div className="modal" style={{ maxWidth: 640 }}>
        <div className="modal-header" style={{ background: '#fef3c7', borderBottom: '1px solid #f59e0b' }}>
          <div>
            <h2 style={{ color: '#92400e' }}>⚠️ Duplicate Check Failed</h2>
            <p style={{ fontSize: 13, color: '#92400e', marginTop: 2 }}>
              A similarity match was detected with existing questions in the same stack and topic.
            </p>
          </div>
          <button className="modal-close" onClick={onClose}>×</button>
        </div>
        <div className="modal-body">
          <p style={{ fontSize: 13, marginBottom: 16, color: 'var(--text-secondary)' }}>
            Similar question IDs: {result.similarQuestions.map(q => q.questionId).join(' and ')}.
            {result.similarQuestions.map(q => ` The MCQ is ${q.similarityPercent}% similar to question ID ${q.questionId}`).join(' and ')}.
          </p>

          {result.similarQuestions.map(q => (
            <div key={q.questionId} style={{
              background: '#fef9c3', border: '1px solid #f59e0b',
              borderRadius: 10, padding: 16, marginBottom: 12
            }}>
              <p style={{ fontWeight: 700, color: '#92400e', marginBottom: 8 }}>
                Question ID {q.questionId} — {q.similarityPercent}% similar
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
            The question can only be sent for review if similarity is below 30%. Please edit your question and re-check.
          </div>
        </div>
        <div className="modal-footer">
          <button className="btn btn-secondary" onClick={onClose}>Edit Question</button>
        </div>
      </div>
    </div>
  );
}

export default function EditQuestionPage() {
  const { id } = useParams();
  const navigate = useNavigate();
  const location = useLocation();
  const returnToPage = location.state?.returnToPage ?? 0;

  const [mcq, setMcq] = useState(null);
  const [loading, setLoading] = useState(false);
  const [fetching, setFetching] = useState(true);
  const [similarityResult, setSimilarityResult] = useState(null);
  const [checkingDuplicate, setCheckingDuplicate] = useState(false);
  const [lastCheckedData, setLastCheckedData] = useState(null);

  useEffect(() => {
    mcqAPI.getById(id)
      .then(r => setMcq(r.data))
      .catch(() => {
        toast.error('Failed to load question');
        navigate('/my-questions', { state: { returnToPage } });
      })
      .finally(() => setFetching(false));
  }, [id]);

  const goBack = () => navigate('/my-questions', { state: { returnToPage } });

  const runSimilarityCheck = async (data) => {
    setCheckingDuplicate(true);
    try {
      const res = await aiAPI.checkSimilarity({
        questionStem: data.questionStem,
        optionA: data.optionA,
        optionB: data.optionB,
        optionC: data.optionC,
        optionD: data.optionD,
        optionE: data.optionE || null,
        optionF: data.optionF || null,
        stackId: parseInt(data.stackId),
        topicId: parseInt(data.topicId),
        excludeQuestionId: parseInt(id),
      });
      return res.data;
    } catch (err) {
      toast.error('Similarity check failed. Please try again.');
      return null;
    } finally {
      setCheckingDuplicate(false);
    }
  };

  const handleDuplicateCheck = async (data) => {
    setLastCheckedData(data);
    const result = await runSimilarityCheck(data);
    if (!result) return;

    if (result.hasSimilar) {
      setSimilarityResult(result);
    } else {
      toast.success('✅ No similar questions found! This question is unique.');
      setSimilarityResult(null);
    }
  };

  const handleSave = async (data) => {
    setLoading('save');
    try {
      await mcqAPI.update(id, data);
      toast.success('Question updated!');
      goBack();
    } catch (err) {
      const msg = err.response?.data?.message || 'Failed to update';
      toast.error(msg.includes('uplicate') ? '❌ ' + msg : msg,
        { duration: msg.includes('uplicate') ? 5000 : 3000 });
    } finally { setLoading(false); }
  };

  const handleSaveAndSend = async (data) => {
    // Always run similarity check before sending for review
    setLastCheckedData(data);
    const result = await runSimilarityCheck(data);
    if (!result) return;

    if (result.hasSimilar) {
      setSimilarityResult(result);
      return; // Block — show modal
    }

    // Similarity passed — proceed to save and send
    setLoading('send');
    try {
      await mcqAPI.update(id, { ...data, sendForReview: true });
      toast.success('Question submitted for review!');
      goBack();
    } catch (err) {
      const msg = err.response?.data?.message || 'Failed to submit';
      toast.error(msg.includes('uplicate') ? '❌ ' + msg : msg,
        { duration: msg.includes('uplicate') ? 5000 : 3000 });
    } finally { setLoading(false); }
  };

  if (fetching) return (
    <div className="loading-container" style={{ minHeight: '60vh' }}>
      <div className="spinner spinner-dark" />
    </div>
  );

  if (!mcq) return null;

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
    <div>
      <div className="page-header">
        <div>
          <div style={{ display: 'flex', alignItems: 'center', gap: 10, marginBottom: 4 }}>
            <h1 className="page-title">Edit Question</h1>
            <StatusBadge status={mcq.status} />
          </div>
          <p className="page-subtitle">Question #{mcq.questionId} · {mcq.stackName}</p>
        </div>
        <button className="btn btn-ghost" onClick={goBack}>← Back</button>
      </div>

      <div className="card">
        <MCQForm
          initial={initial}
          onSave={handleSave}
          onSaveAndSend={handleSaveAndSend}
          onDuplicateCheck={handleDuplicateCheck}
          loading={loading}
          checkingDuplicate={checkingDuplicate}
          isEdit
          rejectedComments={mcq.status === 'REJECTED' ? mcq.reviewerComments : null}
          showDuplicateCheck={true}
        />
      </div>

      {similarityResult && (
        <SimilarityResultModal
          result={similarityResult}
          onClose={() => setSimilarityResult(null)}
          onProceed={() => setSimilarityResult(null)}
        />
      )}
    </div>
  );
}

// Extended MCQForm with Duplicate Check button
// function MCQFormWithDuplicateCheck({ initial, onSave, onSaveAndSend, onDuplicateCheck,
//                                       loading, checkingDuplicate, isEdit, rejectedComments }) {
//   const MCQForm = require('../components/MCQForm').default;
//   const [currentData, setCurrentData] = useState(null);

//   return (
//     <MCQForm
//       initial={initial}
//       onSave={onSave}
//       onSaveAndSend={onSaveAndSend}
//       onDuplicateCheck={onDuplicateCheck}
//       loading={loading}
//       checkingDuplicate={checkingDuplicate}
//       isEdit={isEdit}
//       rejectedComments={rejectedComments}
//       showDuplicateCheck={true}
//     />
//   );
// }