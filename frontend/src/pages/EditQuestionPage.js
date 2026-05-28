import React, { useState, useEffect } from 'react';
import { useNavigate, useParams, useLocation } from 'react-router-dom';
import { mcqAPI } from '../services/api';
import MCQForm from '../components/MCQForm';
import StatusBadge from '../components/StatusBadge';
import toast from 'react-hot-toast';

export default function EditQuestionPage() {
  const { id } = useParams();
  const navigate = useNavigate();
  const location = useLocation();
  const returnToPage = location.state?.returnToPage ?? 0;

  const [mcq, setMcq] = useState(null);
  const [loading, setLoading] = useState(false);
  const [fetching, setFetching] = useState(true);

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

  const handleSave = async (data) => {
    setLoading('save');
    try {
      await mcqAPI.update(id, data);
      toast.success('Question updated!');
      goBack();
    } catch (err) {
      toast.error(err.response?.data?.message || 'Failed to update');
    } finally { setLoading(false); }
  };

  const handleSaveAndSend = async (data) => {
    setLoading('send');
    try {
      await mcqAPI.update(id, data);
      toast.success('Question submitted for review!');
      goBack();
    } catch (err) {
      toast.error(err.response?.data?.message || 'Failed to submit');
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
    stackId: String(mcq.stackId),
    topicId: String(mcq.topicId),
    difficulty: mcq.difficulty,
    optionA: mcq.optionA,
    optionB: mcq.optionB,
    optionC: mcq.optionC,
    optionD: mcq.optionD,
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
          loading={loading}
          isEdit
          rejectedComments={mcq.status === 'REJECTED' ? mcq.reviewerComments : null}
        />
      </div>
    </div>
  );
}