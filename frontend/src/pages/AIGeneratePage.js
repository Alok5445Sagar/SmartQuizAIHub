import React, { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { aiAPI, referenceAPI } from '../services/api';
import toast from 'react-hot-toast';

export default function AIGeneratePage({ onBack }) {
  const [stacks, setStacks] = useState([]);
  const [topics, setTopics] = useState([]);
  const [form, setForm] = useState({
    stackId: '',
    topicId: '',
    difficulty: 'Medium',
    numberOfQuestions: 3,
  });
  const [loading, setLoading] = useState(false);
  const [result, setResult] = useState(null);
  const navigate = useNavigate();

  useEffect(() => {
    referenceAPI.getStacks().then(r => setStacks(r.data));
  }, []);

  useEffect(() => {
    if (form.stackId) {
      referenceAPI.getTopics(form.stackId).then(r => {
        setTopics(r.data);
        setForm(f => ({ ...f, topicId: '' }));
      });
    } else {
      setTopics([]);
    }
  }, [form.stackId]);

  const set = (field, value) => setForm(f => ({ ...f, [field]: value }));

  const handleGenerate = async () => {
    if (!form.stackId) return toast.error('Please select a Technology Stack');
    if (!form.topicId) return toast.error('Please select a Topic');
    if (!form.numberOfQuestions || form.numberOfQuestions < 1 || form.numberOfQuestions > 20) {
      return toast.error('Number of questions must be between 1 and 20');
    }
    setLoading(true);
    setResult(null);
    try {
      const res = await aiAPI.generateQuestions({
        stackId: parseInt(form.stackId),
        topicId: parseInt(form.topicId),
        difficulty: form.difficulty,
        numberOfQuestions: parseInt(form.numberOfQuestions),
      });
      setResult(res.data);
      toast.success(res.data.message);
    } catch (err) {
      toast.error(err.response?.data?.message || 'AI generation failed. Please try again.');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div>
      <div className="page-header">
        <div>
          <h1 className="page-title">Generate Questions with AI</h1>
          <p className="page-subtitle">
            Generate draft questions, review them, and submit only low-similarity ones.
          </p>
        </div>
        <button className="btn btn-ghost" onClick={onBack}>← Back</button>
      </div>

      <div className="card" style={{ maxWidth: 800 }}>
        {/* Form */}
        <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr 1fr 160px', gap: 16, marginBottom: 24 }}>
          <div className="form-group" style={{ margin: 0 }}>
            <label className="form-label">TECHNOLOGY STACK</label>
            <select className="form-select" value={form.stackId} onChange={e => set('stackId', e.target.value)}>
              <option value="">Select Stack</option>
              {stacks.map(s => <option key={s.stackId} value={s.stackId}>{s.stackName}</option>)}
            </select>
          </div>
          <div className="form-group" style={{ margin: 0 }}>
            <label className="form-label">TOPIC</label>
            <select className="form-select" value={form.topicId} onChange={e => set('topicId', e.target.value)} disabled={!form.stackId}>
              <option value="">Select Topic</option>
              {topics.map(t => <option key={t.topicId} value={t.topicId}>{t.topicName}</option>)}
            </select>
          </div>
          <div className="form-group" style={{ margin: 0 }}>
            <label className="form-label">DIFFICULTY</label>
            <select className="form-select" value={form.difficulty} onChange={e => set('difficulty', e.target.value)}>
              <option value="Easy">Easy</option>
              <option value="Medium">Medium</option>
              <option value="Hard">Hard</option>
            </select>
          </div>
          <div className="form-group" style={{ margin: 0 }}>
            <label className="form-label">NUMBER OF QUESTIONS</label>
            <input
              className="form-input"
              type="number"
              min="1"
              max="20"
              value={form.numberOfQuestions}
              onChange={e => set('numberOfQuestions', e.target.value)}
            />
          </div>
        </div>

        <div style={{ display: 'flex', justifyContent: 'flex-end' }}>
          <button className="btn btn-primary btn-lg" onClick={handleGenerate} disabled={loading}>
            {loading
              ? <><span className="spinner" /> AI is generating questions...</>
              : '🤖 Generate Questions'}
          </button>
        </div>

        {/* Loading state */}
        {loading && (
          <div style={{
            marginTop: 24, padding: 20, background: 'var(--accent-light)',
            borderRadius: 12, textAlign: 'center'
          }}>
            <div style={{ fontSize: 32, marginBottom: 8 }}>🤖</div>
            <p style={{ fontWeight: 600, color: 'var(--accent)' }}>AI is generating your questions...</p>
            <p style={{ fontSize: 13, color: 'var(--text-secondary)', marginTop: 4 }}>
              Each question is automatically screened for similarity. Questions with ≥30% similarity
              are replaced with new ones.
            </p>
          </div>
        )}

        {/* Results */}
        {result && !loading && (
          <div style={{ marginTop: 24 }}>
            <div className="alert alert-success">
              ✅ {result.message}
              {result.generated < result.requested && (
                <span style={{ marginLeft: 8, fontSize: 12 }}>
                  ({result.requested - result.generated} question(s) were filtered due to high similarity with existing questions)
                </span>
              )}
            </div>

            {result.questions?.length > 0 && (
              <div style={{ marginTop: 16 }}>
                <p style={{ fontWeight: 700, fontSize: 14, marginBottom: 12, color: 'var(--text-secondary)' }}>
                  GENERATED QUESTIONS (saved as Draft)
                </p>
                {result.questions.map((q, idx) => (
                  <div key={q.questionId} style={{
                    border: '1px solid var(--border)', borderRadius: 10,
                    padding: 16, marginBottom: 12, background: 'var(--bg)'
                  }}>
                    <div style={{ display: 'flex', gap: 8, marginBottom: 8 }}>
                      <span style={{ fontSize: 12, background: '#ede9ff', color: '#5b21b6', padding: '2px 8px', borderRadius: 4, fontWeight: 600 }}>
                        #{idx + 1}
                      </span>
                      <span style={{ fontSize: 12, background: 'var(--success-light)', color: '#065f46', padding: '2px 8px', borderRadius: 4, fontWeight: 600 }}>
                        ✅ Passed similarity check
                      </span>
                    </div>
                    <p style={{ fontSize: 14, fontWeight: 600, marginBottom: 10 }}>{q.questionStem}</p>
                    <div className="options-grid">
                      {['A', 'B', 'C', 'D'].map(opt => (
                        <div key={opt} className={`option-item ${q.correctAnswer === opt ? 'correct' : ''}`}>
                          <span className="option-label">{opt}</span>
                          <span style={{ fontSize: 12 }}>{q[`option${opt}`]}</span>
                          {q.correctAnswer === opt && <span style={{ marginLeft: 'auto' }}>✓</span>}
                        </div>
                      ))}
                    </div>
                  </div>
                ))}

                <div style={{ display: 'flex', gap: 10, marginTop: 16, justifyContent: 'flex-end' }}>
                  <button className="btn btn-secondary" onClick={() => { setResult(null); }}>
                    Generate More
                  </button>
                  <button className="btn btn-primary" onClick={() => navigate('/my-questions')}>
                    View My Questions →
                  </button>
                </div>
              </div>
            )}
          </div>
        )}
      </div>
    </div>
  );
}