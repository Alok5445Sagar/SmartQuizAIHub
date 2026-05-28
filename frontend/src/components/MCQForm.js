import React, { useState, useEffect } from 'react';
import { referenceAPI } from '../services/api';

const DIFFICULTIES = ['Easy', 'Medium', 'Hard'];
const ANSWERS = ['A', 'B', 'C', 'D'];

export default function MCQForm({ initial = {}, onSave, onSaveAndSend, loading, isEdit = false, rejectedComments }) {
  const [stacks, setStacks] = useState([]);
  const [topics, setTopics] = useState([]);
  const [form, setForm] = useState({
    questionStem: '',
    stackId: '',
    topicId: '',
    difficulty: 'Medium',
    optionA: '',
    optionB: '',
    optionC: '',
    optionD: '',
    correctAnswer: 'A',
    ...initial,
  });
  const [errors, setErrors] = useState({});

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

  const set = (field, value) => {
    setForm(f => ({ ...f, [field]: value }));
    setErrors(e => ({ ...e, [field]: '' }));
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
    setErrors(e);
    return Object.keys(e).length === 0;
  };

  const handleSave = () => { if (validate()) onSave({ ...form, sendForReview: false }); };
  const handleSaveAndSend = () => { if (validate()) onSaveAndSend({ ...form, sendForReview: true }); };

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
      </div>

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
      <p style={{ fontWeight: 700, fontSize: 13, marginBottom: 12, color: 'var(--text-secondary)' }}>OPTIONS</p>

      <div className="grid-2">
        {['A', 'B', 'C', 'D'].map(opt => (
          <div className="form-group" key={opt}>
            <label className="form-label" style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
              <span style={{ fontFamily: 'var(--font-mono)', color: 'var(--accent)', fontWeight: 700 }}>{opt}</span>
              {' '}Option {opt} *
            </label>
            <input
              className="form-input"
              placeholder={`Option ${opt}`}
              value={form[`option${opt}`]}
              onChange={e => set(`option${opt}`, e.target.value)}
            />
            {errors[`option${opt}`] && <span className="form-error">{errors[`option${opt}`]}</span>}
          </div>
        ))}
      </div>

      <div className="form-group" style={{ maxWidth: 200 }}>
        <label className="form-label">Correct Answer *</label>
        <select className="form-select" value={form.correctAnswer} onChange={e => set('correctAnswer', e.target.value)}>
          {ANSWERS.map(a => <option key={a} value={a}>Option {a}</option>)}
        </select>
      </div>

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
