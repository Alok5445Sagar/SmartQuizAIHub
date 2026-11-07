import React, { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { aiAPI, referenceAPI } from '../services/api';
import toast from 'react-hot-toast';
import { useLanguage } from '../context/LanguageContext';

// The running job id survives page changes and reloads in this tab, so polling resumes on return.
export const GENERATION_JOB_KEY = 'smartquiz-ai-generation-job';
export const GENERATION_POLL_MS = 2000;

const isTerminal = (status) => status === 'SUCCEEDED' || status === 'FAILED';

export default function AIGeneratePage({ onBack }) {
  const { t } = useLanguage();
  const [stacks, setStacks] = useState([]);
  const [topics, setTopics] = useState([]);
  const [form, setForm] = useState({
    stackId: '',
    topicId: '',
    difficulty: 'Medium',
    numberOfQuestions: 3,
    generateMCQ: true,
    generateMSQ: false,
  });
  const [submitting, setSubmitting] = useState(false);
  const [jobId, setJobId] = useState(() => sessionStorage.getItem(GENERATION_JOB_KEY));
  const [job, setJob] = useState(null);
  const [result, setResult] = useState(null);
  const navigate = useNavigate();
  const loading = submitting || !!jobId;

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

  useEffect(() => {
    if (!jobId) return undefined;
    let cancelled = false;
    let timer = null;

    const finish = () => {
      sessionStorage.removeItem(GENERATION_JOB_KEY);
      setJobId(null);
      setJob(null);
    };

    const poll = async () => {
      try {
        const res = await aiAPI.getGenerationJob(jobId);
        if (cancelled) return;
        const data = res.data;
        if (isTerminal(data.status)) {
          finish();
          if (data.status === 'SUCCEEDED') {
            setResult(data);
            toast.success(data.message);
          } else {
            toast.error(data.error || t('aiGenerationFailedTryAgain'));
          }
          return;
        }
        setJob(data);
      } catch (err) {
        if (cancelled) return;
        if (err.response?.status === 404) {
          // Job expired or belongs to someone else (e.g. a different login in this tab).
          finish();
          return;
        }
        // Network blip or server restart: keep polling; a restart marks the job FAILED.
      }
      timer = setTimeout(poll, GENERATION_POLL_MS);
    };

    poll();
    return () => {
      cancelled = true;
      clearTimeout(timer);
    };
  }, [jobId, t]);

  const set = (field, value) => setForm(f => ({ ...f, [field]: value }));

  const handleGenerate = async () => {
    if (!form.stackId) return toast.error(t('pleaseSelectTechnologyStack'));
    if (!form.topicId) return toast.error(t('pleaseSelectTopic'));
    if (!form.numberOfQuestions || form.numberOfQuestions < 1 || form.numberOfQuestions > 20) {
      return toast.error(t('numberQuestionsBetween1And20'));
    }
    if (!form.generateMCQ && !form.generateMSQ) {
      return toast.error(t('selectAtLeastOneQuestionType'));
    }
    setSubmitting(true);
    setResult(null);
    try {
      const res = await aiAPI.startGeneration({
        stackId: parseInt(form.stackId),
        topicId: parseInt(form.topicId),
        difficulty: form.difficulty,
        numberOfQuestions: parseInt(form.numberOfQuestions),
        generateMCQ: !!form.generateMCQ,
        generateMSQ: !!form.generateMSQ,
      });
      sessionStorage.setItem(GENERATION_JOB_KEY, res.data.jobId);
      setJob(res.data);
      setJobId(res.data.jobId);
    } catch (err) {
      toast.error((err.response?.data?.detail || err.response?.data?.message) || t('aiGenerationFailedTryAgain'));
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div>
      <div className="page-header">
        <div>
          <h1 className="page-title">{t('aiGenerateTitle')}</h1>
          <p className="page-subtitle">
            {t('aiGenerateSubtitle')}
          </p>
        </div>
        <button className="btn btn-ghost" onClick={onBack}>← {t('back')}</button>
      </div>

      <div className="card ai-generate-card" style={{ maxWidth: 800 }}>
        {/* Form */}
        <div className="ai-generate-grid" style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(170px, 1fr))', gap: 16, marginBottom: 16 }}>
          <div className="form-group" style={{ margin: 0 }}>
            <label className="form-label">{t('technologyStack').toUpperCase()}</label>
            <select className="form-select" value={form.stackId} onChange={e => set('stackId', e.target.value)}>
              <option value="">{t('selectStack')}</option>
              {stacks.map(s => <option key={s.stackId} value={s.stackId}>{s.stackName}</option>)}
            </select>
          </div>
          <div className="form-group" style={{ margin: 0 }}>
            <label className="form-label">{t('topic').toUpperCase()}</label>
            <select className="form-select" value={form.topicId} onChange={e => set('topicId', e.target.value)} disabled={!form.stackId}>
              <option value="">{t('selectTopic')}</option>
              {topics.map(t => <option key={t.topicId} value={t.topicId}>{t.topicName}</option>)}
            </select>
          </div>
          <div className="form-group" style={{ margin: 0 }}>
            <label className="form-label">{t('difficulty').toUpperCase()}</label>
            <select className="form-select" value={form.difficulty} onChange={e => set('difficulty', e.target.value)}>
              <option value="Easy">{t('easy')}</option>
              <option value="Medium">{t('medium')}</option>
              <option value="Hard">{t('hard')}</option>
            </select>
          </div>
          <div className="form-group" style={{ margin: 0 }}>
            <label className="form-label">{t('numberOfQuestions').toUpperCase()}</label>
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

        <div style={{ marginBottom: 24 }}>
          <label className="form-label" style={{ marginBottom: 8, display: 'block' }}>
            {t('questionTypesToGenerate').toUpperCase()}
          </label>
          <div style={{ display: 'flex', gap: 12, flexWrap: 'wrap' }}>
            <label style={{
              display: 'inline-flex', alignItems: 'center', gap: 8,
              padding: '8px 12px', borderRadius: 8,
              border: '1px solid var(--border)', background: 'var(--bg-card)'
            }}>
              <input
                type="checkbox"
                checked={form.generateMCQ}
                onChange={e => set('generateMCQ', e.target.checked)}
              />
              <span style={{ fontWeight: 600, fontSize: 13 }}>{t('mcqSingleAnswer')}</span>
            </label>

            <label style={{
              display: 'inline-flex', alignItems: 'center', gap: 8,
              padding: '8px 12px', borderRadius: 8,
              border: '1px solid var(--border)', background: 'var(--bg-card)'
            }}>
              <input
                type="checkbox"
                checked={form.generateMSQ}
                onChange={e => set('generateMSQ', e.target.checked)}
              />
              <span style={{ fontWeight: 600, fontSize: 13 }}>{t('msqMultipleAnswers')}</span>
            </label>
          </div>
        </div>

        <div className="ai-generate-actions" style={{ display: 'flex', justifyContent: 'flex-end' }}>
          <button className="btn btn-primary btn-lg" onClick={handleGenerate} disabled={loading}>
            {loading
              ? <><span className="spinner" /> {t('aiGeneratingQuestions')}</>
              : `🤖 ${t('generateQuestions')}`}
          </button>
        </div>

        {/* Loading state */}
        {loading && (
          <div style={{
            marginTop: 24, padding: 20, background: 'var(--accent-light)',
            borderRadius: 12, textAlign: 'center'
          }}>
            <div style={{ fontSize: 32, marginBottom: 8 }}>🤖</div>
            <p style={{ fontWeight: 600, color: 'var(--accent)' }}>
              {job?.status === 'QUEUED' && job.queuePosition
                ? `${t('aiGenerationQueuePosition')} ${job.queuePosition}`
                : t('aiGeneratingYourQuestions')}
            </p>
            {job?.status === 'RUNNING' && job.requested > 0 && (
              <p data-testid="ai-generation-progress" style={{ fontSize: 13, marginTop: 4 }}>
                {t('aiGenerationProgress')}: {job.generatedSoFar} / {job.requested}
              </p>
            )}
            <p style={{ fontSize: 13, color: 'var(--text-secondary)', marginTop: 4 }}>
              {t('aiSimilarityScreeningNote')}
            </p>
            <p style={{ fontSize: 12, color: 'var(--text-secondary)', marginTop: 4 }}>
              {t('aiGenerationContinuesInBackground')}
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
                  ({result.requested - result.generated} {t('questionsFilteredDueToHighSimilarity')})
                </span>
              )}
            </div>

            {result.questions?.length > 0 && (
              <div style={{ marginTop: 16 }}>
                <p style={{ fontWeight: 700, fontSize: 14, marginBottom: 12, color: 'var(--text-secondary)' }}>
                  {t('generatedQuestionsSavedAsDraft')}
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
                        ✅ {t('passedSimilarityCheck')}
                      </span>
                      <span style={{ fontSize: 12, background: '#ecfeff', color: '#0e7490', padding: '2px 8px', borderRadius: 4, fontWeight: 600 }}>
                        {(q.questionType || 'MCQ').toUpperCase()}
                      </span>
                    </div>
                    <p style={{ fontSize: 14, fontWeight: 600, marginBottom: 10 }}>{q.questionStem}</p>
                    <div className="options-grid">
                      {['A', 'B', 'C', 'D', 'E', 'F']
                        .filter(opt => q[`option${opt}`])
                        .map(opt => (
                        <div
                          key={opt}
                          className={`option-item ${(q.correctAnswer || '').split(',').map(a => a.trim()).includes(opt) ? 'correct' : ''}`}
                        >
                          <span className="option-label">{opt}</span>
                          <span style={{ fontSize: 12 }}>{q[`option${opt}`]}</span>
                          {(q.correctAnswer || '').split(',').map(a => a.trim()).includes(opt) && <span style={{ marginLeft: 'auto' }}>✓</span>}
                        </div>
                      ))}
                    </div>
                  </div>
                ))}

                <div className="ai-generate-actions" style={{ display: 'flex', gap: 10, marginTop: 16, justifyContent: 'flex-end' }}>
                  <button className="btn btn-secondary" onClick={() => { setResult(null); }}>
                    {t('generateMore')}
                  </button>
                  <button className="btn btn-primary" onClick={() => navigate('/my-questions')}>
                    {t('viewMyQuestions')} →
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