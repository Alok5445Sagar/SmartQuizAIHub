import React, { useState, useEffect, useRef } from 'react';
import { useNavigate, useParams, useLocation } from 'react-router-dom';
import { mcqAPI, aiAPI, adminAPI } from '../services/api';
import MCQForm from '../components/MCQForm';
import StatusBadge from '../components/StatusBadge';
import toast from 'react-hot-toast';
import { useAuth } from '../context/AuthContext';
import { useLanguage } from '../context/LanguageContext';

function AssignReviewerModal({ mcq, onClose, onDone }) {
  const { t, language } = useLanguage();
  const [reviewers, setReviewers] = useState([]);
  const [selectedReviewer, setSelectedReviewer] = useState('');
  const [loading, setLoading] = useState(false);
  const [fetching, setFetching] = useState(true);

  useEffect(() => {
    adminAPI.getReviewers(mcq.stackId, mcq.creatorEnterpriseId)
      .then(r => setReviewers(r.data))
      .catch(() => toast.error(t('failedToLoadReviewers')))
      .finally(() => setFetching(false));
  }, [mcq.stackId, mcq.creatorEnterpriseId]);

  const handleAssign = async () => {
    if (!selectedReviewer) return toast.error(t('pleaseSelectReviewer'));
    setLoading(true);
    try {
      await adminAPI.assignReviewer(mcq.questionId, selectedReviewer);
      toast.success(t('reviewerAssignedSuccessfully'));
      onDone();
    } catch (err) {
      toast.error(err.response?.data?.message || t('failedToAssignReviewer'));
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="modal-overlay" onClick={e => e.target === e.currentTarget && onClose()}>
      <div className="modal" style={{ maxWidth: 500 }}>
        <div className="modal-header">
          <h2>{t('assignReviewer')}</h2>
          <button className="modal-close" onClick={onClose}>×</button>
        </div>
        <div className="modal-body">
          {fetching ? (
            <div style={{ textAlign: 'center', padding: 20 }}><div className="spinner spinner-dark" style={{ margin: '0 auto' }} /></div>
          ) : reviewers.length === 0 ? (
            <div className="alert alert-info">{t('noEligibleReviewersForStack')}</div>
          ) : (
            <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
              {reviewers.map(r => (
                <label key={r.enterpriseId} style={{
                  display: 'flex', alignItems: 'center', gap: 12,
                  padding: '10px 14px',
                  border: `1.5px solid ${selectedReviewer === r.enterpriseId ? 'var(--accent)' : 'var(--border)'}`,
                  borderRadius: 10, cursor: 'pointer',
                  background: selectedReviewer === r.enterpriseId ? 'var(--accent-light)' : 'var(--bg-card)',
                }}>
                  <input
                    type="radio"
                    name="reviewer"
                    value={r.enterpriseId}
                    checked={selectedReviewer === r.enterpriseId}
                    onChange={() => setSelectedReviewer(r.enterpriseId)}
                    style={{ accentColor: 'var(--accent)' }}
                  />
                  <div style={{ flex: 1 }}>
                    <div style={{ fontWeight: 600, fontSize: 14 }}>{r.fullName}</div>
                    <div style={{ fontSize: 11, fontFamily: 'var(--font-mono)', color: 'var(--text-muted)' }}>{r.enterpriseId}</div>
                  </div>
                </label>
              ))}
            </div>
          )}
        </div>
        <div className="modal-footer">
          <button className="btn btn-secondary" onClick={onClose}>{t('cancel')}</button>
          <button className="btn btn-primary" onClick={handleAssign} disabled={loading || !selectedReviewer || fetching}>
            {loading ? <><span className="spinner" /> {t('assigning')}</> : `👤 ${t('assignReviewer')}`}
          </button>
        </div>
      </div>
    </div>
  );
}

function SimilarityResultModal({ result, onClose, onProceed }) {
  const { t } = useLanguage();
  return (
    <div className="modal-overlay" onClick={e => e.target === e.currentTarget && onClose()}>
      <div className="modal" style={{ maxWidth: 640 }}>
        <div className="modal-header" style={{ background: '#fef3c7', borderBottom: '1px solid #f59e0b' }}>
          <div>
            <h2 style={{ color: '#92400e' }}>⚠️ {t('duplicateCheckFailedTitle')}</h2>
            <p style={{ fontSize: 13, color: '#92400e', marginTop: 2 }}>
              {t('similarityMatchDetectedMessage')}
            </p>
          </div>
          <button className="modal-close" onClick={onClose}>×</button>
        </div>
        <div className="modal-body">
          <p style={{ fontSize: 13, marginBottom: 16, color: 'var(--text-secondary)' }}>
            {t('similarQuestionIdsLabel')}: {result.similarQuestions.map(q => q.questionId).join(` ${t('and')} `)}.
            {result.similarQuestions.map(q => ` ${t('mcqSimilarityPrefix')} ${q.similarityPercent}% ${t('toQuestionId')} ${q.questionId}`).join(` ${t('and')} `)}.
          </p>

          {result.similarQuestions.map(q => (
            <div key={q.questionId} style={{
              background: '#fef9c3', border: '1px solid #f59e0b',
              borderRadius: 10, padding: 16, marginBottom: 12
            }}>
              <p style={{ fontWeight: 700, color: '#92400e', marginBottom: 8 }}>
                {t('questionIdLabel')} {q.questionId} - {q.similarityPercent}% {t('similar')}
              </p>
              <p style={{ fontWeight: 600, marginBottom: 8, fontSize: 14 }}>
                {t('questionStem')}: {q.questionStem}
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
            {t('reviewRequiresSimilarityBelow30')}
          </div>
        </div>
        <div className="modal-footer">
          <button className="btn btn-secondary" onClick={onClose}>{t('editQuestionTitle')}</button>
        </div>
      </div>
    </div>
  );
}

export default function EditQuestionPage() {
  const { user } = useAuth();
  const { t, language } = useLanguage();
  const { id } = useParams();
  const navigate = useNavigate();
  const location = useLocation();
  const returnToPage = location.state?.returnToPage ?? 0;
  const returnFilters = location.state?.returnFilters;

  const [mcq, setMcq] = useState(null);
  const [loading, setLoading] = useState(false);
  const [fetching, setFetching] = useState(true);
  const [similarityResult, setSimilarityResult] = useState(null);
  const [checkingDuplicate, setCheckingDuplicate] = useState(false);
  const [assignAfterSave, setAssignAfterSave] = useState(false);
  const [translationLanguage, setTranslationLanguage] = useState('es');
  const [translationDraft, setTranslationDraft] = useState({
    questionStem: '',
    optionA: '',
    optionB: '',
    optionC: '',
    optionD: '',
    optionE: '',
    optionF: '',
  });
  const [loadingTranslation, setLoadingTranslation] = useState(false);
  const [savingTranslation, setSavingTranslation] = useState(false);
  const [existingTranslations, setExistingTranslations] = useState([]);
  const similarityCacheRef = useRef({ signature: null, result: null });

  const shouldSkipDuplicateCheck = Boolean(mcq?.aiGenerated || mcq?.bulkUploaded);

  const buildSimilarityPayload = (data) => ({
    questionStem: (data.questionStem || '').trim(),
    optionA: data.optionA || '',
    optionB: data.optionB || '',
    optionC: data.optionC || '',
    optionD: data.optionD || '',
    optionE: data.optionE || null,
    optionF: data.optionF || null,
    stackId: parseInt(data.stackId, 10),
    topicId: parseInt(data.topicId, 10),
    excludeQuestionId: parseInt(id, 10),
  });

  const buildSimilaritySignature = (payload) => JSON.stringify(payload);

  useEffect(() => {
    mcqAPI.getById(id, { languageCode: 'en' })
      .then(r => setMcq(r.data))
      .catch(() => {
        toast.error(t('failedToLoadQuestion'));
          navigate('/my-questions', { state: { returnToPage, returnFilters } });
      })
      .finally(() => setFetching(false));
  }, [id, navigate, returnToPage, returnFilters, t, language]);

  const loadTranslations = async (questionId) => {
    try {
      const res = await mcqAPI.getTranslations(questionId);
      setExistingTranslations(res.data?.translations || []);
    } catch {
      setExistingTranslations([]);
    }
  };

  useEffect(() => {
    if (!mcq?.questionId) return;
    loadTranslations(mcq.questionId);
  }, [mcq?.questionId]);

  useEffect(() => {
    const existing = existingTranslations.find((item) => item.languageCode === translationLanguage);
    if (!existing) {
      setTranslationDraft({
        questionStem: '',
        optionA: '',
        optionB: '',
        optionC: '',
        optionD: '',
        optionE: '',
        optionF: '',
      });
      return;
    }
    setTranslationDraft({
      questionStem: existing.questionStem || '',
      optionA: existing.optionA || '',
      optionB: existing.optionB || '',
      optionC: existing.optionC || '',
      optionD: existing.optionD || '',
      optionE: existing.optionE || '',
      optionF: existing.optionF || '',
    });
  }, [translationLanguage, existingTranslations]);

        const goBack = () => navigate('/my-questions', { state: { returnToPage, returnFilters } });

  const runSimilarityCheck = async (payload) => {
    setCheckingDuplicate(true);
    try {
      const res = await aiAPI.checkSimilarity(payload);
      return res.data;
    } catch (err) {
      toast.error(t('similarityCheckFailedTryAgain'));
      return null;
    } finally {
      setCheckingDuplicate(false);
    }
  };

  const getSimilarityCheckResult = async (data) => {
    const payload = buildSimilarityPayload(data);
    const signature = buildSimilaritySignature(payload);

    if (similarityCacheRef.current.signature === signature && similarityCacheRef.current.result) {
      return similarityCacheRef.current.result;
    }

    const result = await runSimilarityCheck(payload);
    if (result) {
      similarityCacheRef.current = { signature, result };
    }
    return result;
  };

  const handleDuplicateCheck = async (data) => {
    if (shouldSkipDuplicateCheck) {
      toast(t('duplicateCheckSkippedForAiBulk'), { icon: 'ℹ️' });
      return;
    }

    const result = await getSimilarityCheckResult(data);
    if (!result) return;

    if (result.hasSimilar) {
      setSimilarityResult(result);
    } else {
      toast.success(`✅ ${t('noSimilarQuestionsFoundUnique')}`);
      setSimilarityResult(null);
    }
  };

  const handleSave = async (data) => {
    setLoading('save');
    try {
      await mcqAPI.update(id, data);
      toast.success(t('questionUpdated'));
      goBack();
    } catch (err) {
      const msg = err.response?.data?.message || 'Failed to update';
      toast.error(msg.includes('uplicate') ? '❌ ' + msg : msg,
        { duration: msg.includes('uplicate') ? 5000 : 3000 });
    } finally { setLoading(false); }
  };

  const handleSaveAndSend = async (data) => {
    // AI-generated and bulk-uploaded questions are pre-checked; skip edit-page duplicate check.
    if (!shouldSkipDuplicateCheck) {
      const result = await getSimilarityCheckResult(data);
      if (!result) return;

      if (result.hasSimilar) {
        setSimilarityResult(result);
        return; // Block — show modal
      }
    }

    // Similarity passed — proceed to save and send
    setLoading('send');
    try {
      await mcqAPI.update(id, { ...data, sendForReview: true });
      toast.success(t('questionSubmittedForReview'));
      goBack();
    } catch (err) {
      const msg = err.response?.data?.message || t('failedToSubmit');
      toast.error(msg.includes('uplicate') ? '❌ ' + msg : msg,
        { duration: msg.includes('uplicate') ? 5000 : 3000 });
    } finally { setLoading(false); }
  };

  const handleSaveAndAssign = async (data) => {
    if (!shouldSkipDuplicateCheck) {
      const result = await getSimilarityCheckResult(data);
      if (!result) return;
      if (result.hasSimilar) {
        setSimilarityResult(result);
        return;
      }
    }

    setLoading('send');
    try {
      await mcqAPI.update(id, { ...data, sendForReview: false });
      toast.success(t('questionUpdatedPleaseAssignReviewer'));
      setAssignAfterSave(true);
    } catch (err) {
      const msg = err.response?.data?.message || 'Failed to update';
      toast.error(msg.includes('uplicate') ? '❌ ' + msg : msg,
        { duration: msg.includes('uplicate') ? 5000 : 3000 });
    } finally {
      setLoading(false);
    }
  };

  const handleDelete = async () => {
    const confirmed = window.confirm(
      t('deleteQuestionConfirmPermanent')
    );
    if (!confirmed) return;

    setLoading('delete');
    try {
      await mcqAPI.delete(id);
      toast.success(t('questionDeletedSuccessfully'));
      goBack();
    } catch (err) {
      const msg = err.response?.data?.message || t('failedToDeleteQuestion');
      toast.error(msg);
    } finally {
      setLoading(false);
    }
  };

  const handleSuggestTranslation = async () => {
    setLoadingTranslation(true);
    try {
      const res = await mcqAPI.suggestTranslation(id, translationLanguage);
      setTranslationDraft({
        questionStem: res.data?.questionStem || '',
        optionA: res.data?.optionA || '',
        optionB: res.data?.optionB || '',
        optionC: res.data?.optionC || '',
        optionD: res.data?.optionD || '',
        optionE: res.data?.optionE || '',
        optionF: res.data?.optionF || '',
      });
      toast.success(t('translationSuggestionGeneratedReviewBeforeSaving'));
    } catch (err) {
      toast.error(err.response?.data?.message || t('failedToGenerateTranslation'));
    } finally {
      setLoadingTranslation(false);
    }
  };

  const handleSaveTranslation = async () => {
    setSavingTranslation(true);
    try {
      await mcqAPI.saveTranslation(id, {
        languageCode: translationLanguage,
        ...translationDraft,
      });
      toast.success(t('translationSavedSuccessfully'));
      loadTranslations(id);
    } catch (err) {
      toast.error(err.response?.data?.message || t('failedToSaveTranslation'));
    } finally {
      setSavingTranslation(false);
    }
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

  const isAdmin = user?.role === 'ADMIN';
  const isAdminOwnDraft = isAdmin
    && mcq.status === 'DRAFT'
    && mcq.creatorEnterpriseId === user?.enterpriseId;

  return (
    <div>
      <div className="page-header">
        <div>
          <div style={{ display: 'flex', alignItems: 'center', gap: 10, marginBottom: 4 }}>
            <h1 className="page-title">{t('editQuestionTitle')}</h1>
            <StatusBadge status={mcq.status} />
          </div>
          <p className="page-subtitle">{t('question')} #{mcq.questionId} · {mcq.stackName}</p>
        </div>
        <button className="btn btn-ghost" onClick={goBack}>← {t('back')}</button>
      </div>

      <div className="card">
        <MCQForm
          initial={initial}
          onSave={handleSave}
          onSaveAndSend={isAdminOwnDraft ? handleSaveAndAssign : handleSaveAndSend}
          onDuplicateCheck={handleDuplicateCheck}
          loading={loading}
          checkingDuplicate={checkingDuplicate}
          isEdit
          onDelete={handleDelete}
          rejectedComments={mcq.status === 'REJECTED' ? mcq.reviewerComments : null}
          showDuplicateCheck={!shouldSkipDuplicateCheck}
          saveAndSendLabel={isAdminOwnDraft ? `👤 ${t('saveAndAssignReviewer')}` : `🚀 ${t('saveAndSendForReview')}`}
        />
      </div>

      <div className="card" style={{ marginTop: 16 }}>
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', gap: 10, flexWrap: 'wrap', marginBottom: 14 }}>
          <div>
            <h3 style={{ marginBottom: 4 }}>{t('questionTranslation')}</h3>
            <p className="form-hint">{t('questionTranslationHint')}</p>
          </div>
          <div style={{ display: 'flex', gap: 8, alignItems: 'center' }}>
            <select className="form-input" value={translationLanguage} onChange={(e) => setTranslationLanguage(e.target.value)} style={{ width: 140 }}>
              <option value="es">{t('spanish')}</option>
              <option value="fr">{t('french')}</option>
              <option value="ro">{t('romanian')}</option>
            </select>
            <button className="btn btn-secondary" onClick={handleSuggestTranslation} disabled={loadingTranslation}>
              {loadingTranslation ? <><span className="spinner" /> {t('generating')}</> : t('translateQuestion')}
            </button>
            <button className="btn btn-primary" onClick={handleSaveTranslation} disabled={savingTranslation}>
              {savingTranslation ? <><span className="spinner" /> {t('saving')}</> : t('saveTranslation')}
            </button>
          </div>
        </div>

        <div className="form-group">
          <label className="form-label">{t('translatedQuestionStem')}</label>
          <textarea className="form-input" rows={3} value={translationDraft.questionStem} onChange={(e) => setTranslationDraft((prev) => ({ ...prev, questionStem: e.target.value }))} />
        </div>
        <div className="grid-2">
          <div className="form-group">
            <label className="form-label">{t('optionA')}</label>
            <input className="form-input" value={translationDraft.optionA} onChange={(e) => setTranslationDraft((prev) => ({ ...prev, optionA: e.target.value }))} />
          </div>
          <div className="form-group">
            <label className="form-label">{t('optionB')}</label>
            <input className="form-input" value={translationDraft.optionB} onChange={(e) => setTranslationDraft((prev) => ({ ...prev, optionB: e.target.value }))} />
          </div>
          <div className="form-group">
            <label className="form-label">{t('optionC')}</label>
            <input className="form-input" value={translationDraft.optionC} onChange={(e) => setTranslationDraft((prev) => ({ ...prev, optionC: e.target.value }))} />
          </div>
          <div className="form-group">
            <label className="form-label">{t('optionD')}</label>
            <input className="form-input" value={translationDraft.optionD} onChange={(e) => setTranslationDraft((prev) => ({ ...prev, optionD: e.target.value }))} />
          </div>
          <div className="form-group">
            <label className="form-label">{t('optionEOptional')}</label>
            <input className="form-input" value={translationDraft.optionE} onChange={(e) => setTranslationDraft((prev) => ({ ...prev, optionE: e.target.value }))} />
          </div>
          <div className="form-group">
            <label className="form-label">{t('optionFOptional')}</label>
            <input className="form-input" value={translationDraft.optionF} onChange={(e) => setTranslationDraft((prev) => ({ ...prev, optionF: e.target.value }))} />
          </div>
        </div>
      </div>

      {similarityResult && (
        <SimilarityResultModal
          result={similarityResult}
          onClose={() => setSimilarityResult(null)}
          onProceed={() => setSimilarityResult(null)}
        />
      )}

      {assignAfterSave && (
        <AssignReviewerModal
          mcq={mcq}
          onClose={() => setAssignAfterSave(false)}
          onDone={() => {
            setAssignAfterSave(false);
            goBack();
          }}
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