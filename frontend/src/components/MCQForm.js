import React, { useState, useEffect, useRef } from 'react';
import { referenceAPI, mcqAPI } from '../services/api';

const DIFFICULTIES = ['Easy', 'Medium', 'Hard'];
const ALL_OPTIONS = ['A', 'B', 'C', 'D', 'E', 'F'];
const MCQ_OPTIONS = ['A', 'B', 'C', 'D'];

export default function MCQForm({ initial = {}, onSave, onSaveAndSend, loading, isEdit = false, rejectedComments }) {
  const [stacks, setStacks] = useState([]);
  const [topics, setTopics] = useState([]);
  const [form, setForm] = useState({
    questionStem: '',
    questionType: 'MCQ',
    stackId: '',
    topicId: '',
    difficulty: 'Medium',
    optionA: '',
    optionB: '',
    optionC: '',
    optionD: '',
    optionE: '',
    optionF: '',
    correctAnswer: '',
    ...initial,
  });

  const [errors, setErrors] = useState({});
  const [duplicateWarning, setDuplicateWarning] = useState('');
  const duplicateCheckTimer = useRef(null);

  const isMSQ = form.questionType === 'MSQ';

  // For MSQ, correctAnswer is a comma-separated string like "A,C"
  // We manage it as a Set of selected letters in the UI
  const selectedAnswers = form.correctAnswer
    ? form.correctAnswer.split(',').map(s => s.trim()).filter(Boolean)
    : [];

  useEffect(() => {
    referenceAPI.getStacks().then(r => setStacks(r.data));
  }, []);

  useEffect(() => {
    if (form.stackId) {
      referenceAPI.getTopics(form.stackId).then(r => {
        setTopics(r.data);
        if (!initial.topicId) setForm(f => ({ ...f, topicId: '' }));
      });
    }
  }, [form.stackId]);

  // When switching question type, reset correctAnswer and extra options
  const handleTypeChange = (type) => {
    setForm(f => ({
      ...f,
      questionType: type,
      correctAnswer: '',
      optionE: type === 'MCQ' ? '' : f.optionE,
      optionF: type === 'MCQ' ? '' : f.optionF,
    }));
    setErrors({});
  };

  const set = (field, value) => {
    setForm(f => ({ ...f, [field]: value }));
    setErrors(e => ({ ...e, [field]: '' }));

    // Check for duplicates when question stem, stack or topic changes
    if (field === 'questionStem') {
      setDuplicateWarning('');
      clearTimeout(duplicateCheckTimer.current);
      duplicateCheckTimer.current = setTimeout(() => {
        // Will be checked on save; warning cleared
      }, 500);
    }
    if (field === 'stackId' || field === 'topicId') {
      setDuplicateWarning('');
    }
  };

  // Toggle correct answer for MSQ
  const toggleMSQAnswer = (letter) => {
    const current = new Set(selectedAnswers);
    if (current.has(letter)) {
      current.delete(letter);
    } else {
      current.add(letter);
    }
    // Sort to keep consistent order A,B,C...
    const sorted = ALL_OPTIONS.filter(o => current.has(o));
    set('correctAnswer', sorted.join(','));
  };

  const validate = () => {
    const e = {};
    if (!form.questionStem.trim()) e.questionStem = 'Question stem is required';
    if (!form.stackId) e.stackId = 'Technology stack is required';
    if (!form.topicId) e.topicId = 'Topic is required';
    if (!form.optionA.trim()) e.optionA = 'Option A is required';
    if (!form.optionB.trim()) e.optionB = 'Option B is required';
    if (!form.optionC.trim()) e.optionC = 'Option C is required';
    if (!form.optionD.trim()) e.optionD = 'Option D is required';

    if (!form.correctAnswer) {
      e.correctAnswer = isMSQ
        ? 'Select at least 2 correct answers for MSQ'
        : 'Select the correct answer';
    } else if (isMSQ && selectedAnswers.length < 2) {
      e.correctAnswer = 'MSQ requires at least 2 correct answers';
    }

    // Validate that selected MSQ answers have non-empty options
    if (isMSQ) {
      selectedAnswers.forEach(letter => {
        const key = `option${letter}`;
        if (!form[key] || !form[key].trim()) {
          e.correctAnswer = `Option ${letter} is selected as correct but is empty`;
        }
      });
    }

    setErrors(e);
    return Object.keys(e).length === 0;
  };

  const handleSave = () => { if (validate()) onSave({ ...form, sendForReview: false }); };
  const handleSaveAndSend = () => { if (validate()) onSaveAndSend({ ...form, sendForReview: true }); };

  // Which options to show — always A-D, optionally E-F for MSQ
  const visibleOptions = isMSQ ? ALL_OPTIONS : MCQ_OPTIONS;

  return (
    <div>
      {rejectedComments && (
        <div className="alert alert-error" style={{ marginBottom: 20 }}>
          <div>
            <strong>Reviewer Comments:</strong>
            <p style={{ marginTop: 4 }}>{rejectedComments}</p>
          </div>
        </div>
      )}

      {/* Question Type Toggle */}
      <div className="form-group">
        <label className="form-label">Question Type *</label>
        <div style={{ display: 'flex', gap: 12 }}>
          {['MCQ', 'MSQ'].map(type => (
            <button
              key={type}
              type="button"
              onClick={() => handleTypeChange(type)}
              style={{
                padding: '10px 28px',
                borderRadius: 10,
                border: `2px solid ${form.questionType === type ? 'var(--accent)' : 'var(--border)'}`,
                background: form.questionType === type ? 'var(--accent-light)' : 'var(--bg-card)',
                color: form.questionType === type ? 'var(--accent)' : 'var(--text-secondary)',
                fontWeight: 700,
                fontSize: 14,
                cursor: 'pointer',
                transition: 'all 0.15s',
                fontFamily: 'var(--font)',
              }}
            >
              {type === 'MCQ' ? '🔘 MCQ' : '☑️ MSQ'}
              <div style={{ fontSize: 11, fontWeight: 400, marginTop: 2, color: 'inherit', opacity: 0.8 }}>
                {type === 'MCQ' ? 'Single correct answer' : 'Multiple correct answers'}
              </div>
            </button>
          ))}
        </div>
        {isMSQ && (
          <p style={{ fontSize: 12, color: 'var(--accent)', marginTop: 8 }}>
            ℹ️ MSQ supports 4–6 options. Select at least 2 correct answers.
          </p>
        )}
      </div>

      {/* Question Stem */}
      <div className="form-group">
        <label className="form-label">Question Stem *</label>
        <textarea
          className="form-textarea"
          rows={4}
          placeholder="Enter the question..."
          value={form.questionStem}
          onChange={e => set('questionStem', e.target.value)}
          style={{ minHeight: 100 }}
        />
        {errors.questionStem && <span className="form-error">{errors.questionStem}</span>}
        {duplicateWarning && (
          <div style={{
            marginTop: 6, padding: '8px 12px', borderRadius: 8,
            background: '#fef3c7', border: '1px solid #f59e0b',
            color: '#92400e', fontSize: 12, display: 'flex', alignItems: 'center', gap: 6
          }}>
            ⚠️ {duplicateWarning}
          </div>
        )}
      </div>

      {/* Stack / Topic / Difficulty */}
      <div className="grid-3">
        <div className="form-group">
          <label className="form-label">Technology Stack *</label>
          <select className="form-select" value={form.stackId} onChange={e => set('stackId', e.target.value)}>
            <option value="">Select Stack</option>
            {stacks.map(s => <option key={s.stackId} value={s.stackId}>{s.stackName}</option>)}
          </select>
          {errors.stackId && <span className="form-error">{errors.stackId}</span>}
        </div>
        <div className="form-group">
          <label className="form-label">Topic *</label>
          <select className="form-select" value={form.topicId} onChange={e => set('topicId', e.target.value)} disabled={!form.stackId}>
            <option value="">Select Topic</option>
            {topics.map(t => <option key={t.topicId} value={t.topicId}>{t.topicName}</option>)}
          </select>
          {errors.topicId && <span className="form-error">{errors.topicId}</span>}
        </div>
        <div className="form-group">
          <label className="form-label">Difficulty *</label>
          <select className="form-select" value={form.difficulty} onChange={e => set('difficulty', e.target.value)}>
            {DIFFICULTIES.map(d => <option key={d} value={d}>{d}</option>)}
          </select>
        </div>
      </div>

      <div className="divider" />

      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 12 }}>
        <p style={{ fontWeight: 700, fontSize: 13, color: 'var(--text-secondary)' }}>OPTIONS</p>
        {isMSQ && (
          <span style={{ fontSize: 12, color: 'var(--text-muted)' }}>
            Options E and F are optional for MSQ
          </span>
        )}
      </div>

      {/* Options Grid — always 2 columns */}
      <div className="grid-2">
        {visibleOptions.map(opt => (
          <div className="form-group" key={opt}>
            <label className="form-label" style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
              <span style={{ fontFamily: 'var(--font-mono)', color: 'var(--accent)', fontWeight: 700 }}>{opt}</span>
              {' '}Option {opt}
              {(opt === 'A' || opt === 'B' || opt === 'C' || opt === 'D') ? ' *' : ' (optional)'}
            </label>
            <input
              className="form-input"
              placeholder={`Option ${opt}${opt === 'E' || opt === 'F' ? ' (optional)' : ''}`}
              value={form[`option${opt}`]}
              onChange={e => set(`option${opt}`, e.target.value)}
            />
            {errors[`option${opt}`] && <span className="form-error">{errors[`option${opt}`]}</span>}
          </div>
        ))}
      </div>

      {/* Correct Answer */}
      <div className="divider" />

      {isMSQ ? (
        /* MSQ — checkbox style multi-select */
        <div className="form-group">
          <label className="form-label">Correct Answers * <span style={{ fontSize: 12, fontWeight: 400, color: 'var(--text-muted)' }}>(select all that apply — minimum 2)</span></label>
          <div style={{ display: 'flex', gap: 10, flexWrap: 'wrap', marginTop: 4 }}>
            {visibleOptions.map(opt => {
              // For E and F, show in correct answers only if the option field is visible
              // (always show A-D, show E/F regardless of content so user can select them)
              const optValue = form[`option${opt}`];
              const isSelected = selectedAnswers.includes(opt);
              return (
                <button
                  key={opt}
                  type="button"
                  onClick={() => toggleMSQAnswer(opt)}
                  style={{
                    padding: '8px 20px',
                    borderRadius: 8,
                    border: `2px solid ${isSelected ? 'var(--success)' : 'var(--border)'}`,
                    background: isSelected ? 'var(--success-light)' : 'var(--bg)',
                    color: isSelected ? '#065f46' : 'var(--text-secondary)',
                    fontWeight: 700,
                    cursor: 'pointer',
                    fontSize: 14,
                    fontFamily: 'var(--font-mono)',
                    transition: 'all 0.15s',
                    display: 'flex',
                    alignItems: 'center',
                    gap: 6,
                  }}
                >
                  {isSelected ? '✓' : '○'} {opt}
                  {optValue && <span style={{ fontSize: 11, fontFamily: 'var(--font)', fontWeight: 400, maxWidth: 120, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>— {optValue}</span>}
                </button>
              );
            })}
          </div>
          {selectedAnswers.length > 0 && (
            <p style={{ fontSize: 12, color: 'var(--success)', marginTop: 8 }}>
              ✓ Selected: {selectedAnswers.join(', ')}
            </p>
          )}
          {errors.correctAnswer && <span className="form-error">{errors.correctAnswer}</span>}
        </div>
      ) : (
        /* MCQ — single dropdown */
        <div className="form-group" style={{ maxWidth: 200 }}>
          <label className="form-label">Correct Answer *</label>
          <select className="form-select" value={form.correctAnswer} onChange={e => set('correctAnswer', e.target.value)}>
            <option value="">Select Answer</option>
            {MCQ_OPTIONS.map(a => <option key={a} value={a}>Option {a}</option>)}
          </select>
          {errors.correctAnswer && <span className="form-error">{errors.correctAnswer}</span>}
        </div>
      )}

      <div className="divider" />

      <div style={{ display: 'flex', gap: 10, justifyContent: 'flex-end' }}>
        <button className="btn btn-secondary" onClick={handleSave} disabled={loading}>
          {loading === 'save' ? <><span className="spinner spinner-dark" /> Saving...</> : '💾 Save as Draft'}
        </button>
        <button className="btn btn-primary" onClick={handleSaveAndSend} disabled={loading}>
          {loading === 'send' ? <><span className="spinner" /> Submitting...</> : '🚀 Save & Send for Review'}
        </button>
      </div>
    </div>
  );
}