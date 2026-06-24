import React, { useState, useRef } from 'react';
import { useNavigate } from 'react-router-dom';
import { mcqAPI } from '../services/api';
import MCQForm from '../components/MCQForm';
import toast from 'react-hot-toast';
import AIGeneratePage from './AIGeneratePage';
import { useLanguage } from '../context/LanguageContext';

export default function AddQuestionPage() {
  const { t } = useLanguage();
  const [mode, setMode] = useState(null); // null = choose, 'single', 'bulk'
  const [loading, setLoading] = useState(false);
  const [bulkFile, setBulkFile] = useState(null);
  const [bulkLoading, setBulkLoading] = useState(false);
  const [bulkResult, setBulkResult] = useState(null);
  const [bulkErrors, setBulkErrors] = useState([]);
  const fileRef = useRef();
  const navigate = useNavigate();

  const handleSave = async (data) => {
    setLoading('save');
    try {
      await mcqAPI.create(data);
      toast.success(t('questionSavedAsDraft'));
      navigate('/my-questions');
    } catch (err) {
      const msg = err.response?.data?.message || t('failedToSaveQuestion');
      if (msg.toLowerCase().includes('duplicate')) {
        toast.error(`${t('duplicatePrefix')} ${msg}`, { duration: 5000 });
      } else {
        toast.error(msg);
      }
    } finally {
      setLoading(false);
    }
  };

  const handleSaveAndSend = async (data) => {
    setLoading('send');
    try {
      await mcqAPI.create({ ...data, sendForReview: false });
      toast.success(t('questionSavedDraftEditForReview'));
      navigate('/my-questions');
    } catch (err) {
      const msg = err.response?.data?.message || t('failedToSubmitQuestion');
      if (msg.toLowerCase().includes('duplicate')) {
        toast.error(`${t('duplicatePrefix')} ${msg}`, { duration: 5000 });
      } else {
        toast.error(msg);
      }
    } finally {
      setLoading(false);
    }
  };

  const handleBulkUpload = async () => {
    if (!bulkFile) return toast.error(t('pleaseSelectFileFirst'));
    setBulkLoading(true);
    setBulkResult(null);
    setBulkErrors([]);
    try {
      const formData = new FormData();
      formData.append('file', bulkFile);
      const res = await mcqAPI.bulkUpload(formData);
      // HTTP 200 — full success
      if (Array.isArray(res.data)) {
        setBulkResult({ imported: res.data, errors: [] });
        toast.success(`${res.data.length} ${t('questionsImportedSuccessfully')}`);
      }
      // HTTP 207 — partial success
      if (res.data?.imported) {
        setBulkResult(res.data);
        toast(`${res.data.imported.length} ${t('importedRows')}, ${res.data.errors.length} ${t('rowsHadErrors')}.`, { icon: '⚠️' });
      }
    } catch (err) {
      const msg = err.response?.data?.message || t('bulkUploadFailed');
      setBulkErrors(msg.split('\n'));
      toast.error(t('uploadFailedSeeErrors'));
    } finally {
      setBulkLoading(false);
    }
  };

  const handleDownloadTemplate = async () => {
    try {
      const res = await mcqAPI.downloadTemplate();
      const url = URL.createObjectURL(new Blob([res.data]));
      const a = document.createElement('a');
      a.href = url;
      a.download = 'template.xlsx';
      a.click();
      URL.revokeObjectURL(url);
    } catch {
      toast.error(t('downloadFailed'));
    }
  };

  // Choose mode
  if (!mode) {
    return (
      <div>
        <div className="page-header">
          <div>
            <h1 className="page-title">{t('addQuestionTitle')}</h1>
            <p className="page-subtitle">{t('chooseAddMode')}</p>
          </div>
        </div>
        <div className="add-question-mode-grid" style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(220px, 1fr))', gap: 20, maxWidth: 860 }}>
          <button onClick={() => setMode('single')} style={modeCardStyle}>
            <span style={{ fontSize: 40, display: 'block', marginBottom: 12 }}>✍️</span>
            <h3 style={{ fontSize: 16, fontWeight: 700, marginBottom: 6 }}>{t('addFromUI')}</h3>
            <p style={{ fontSize: 13, color: 'var(--text-secondary)' }}>{t('createSingleQuestionUsingForm')}</p>
          </button>
          <button onClick={() => setMode('bulk')} style={modeCardStyle}>
            <span style={{ fontSize: 40, display: 'block', marginBottom: 12 }}>📊</span>
            <h3 style={{ fontSize: 16, fontWeight: 700, marginBottom: 6 }}>{t('bulkUpload')}</h3>
            <p style={{ fontSize: 13, color: 'var(--text-secondary)' }}>{t('uploadMultipleQuestionsViaExcel')}</p>
          </button>
          <button onClick={() => setMode('ai')} style={{ ...modeCardStyle, borderColor: 'var(--accent)', background: 'var(--accent-light)' }}>
            <span style={{ fontSize: 40, display: 'block', marginBottom: 12 }}>🤖</span>
            <h3 style={{ fontSize: 16, fontWeight: 700, marginBottom: 6, color: 'var(--accent)' }}>{t('generateWithAI')}</h3>
            <p style={{ fontSize: 13, color: 'var(--text-secondary)' }}>{t('generateQuestionsUsingAiByTopic')}</p>
          </button>
        </div>
      </div>
    );
  }

  if (mode === 'single') {
    return (
      <div>
        <div className="page-header">
          <div>
            <h1 className="page-title">{t('addSingleQuestion')}</h1>
            <p className="page-subtitle">{t('fillQuestionDetailsBelow')}</p>
          </div>
          <button className="btn btn-ghost" onClick={() => setMode(null)}>← {t('back')}</button>
        </div>
        <div className="card">
          <MCQForm onSave={handleSave} onSaveAndSend={handleSaveAndSend} loading={loading} />
        </div>
      </div>
    );
  }

  if (mode === 'bulk') {
    return (
      <div>
        <div className="page-header">
          <div>
            <h1 className="page-title">{t('bulkUploadQuestions')}</h1>
            <p className="page-subtitle">{t('uploadMultipleQuestionsUsingExcel')}</p>
          </div>
          <button className="btn btn-ghost" onClick={() => setMode(null)}>← {t('back')}</button>
        </div>

        <div className="card" style={{ maxWidth: 560 }}>
          <div style={{ textAlign: 'center', marginBottom: 24 }}>
            <p style={{ fontSize: 13, color: 'var(--text-secondary)', marginBottom: 16 }}>
                {t('downloadTemplateFillUpload')}
            </p>
            <button className="btn btn-secondary" onClick={handleDownloadTemplate}>
                📥 {t('downloadTemplate')}
            </button>
          </div>

          <div className="divider" />

          <div
            style={{
              border: '2px dashed var(--border-strong)',
              borderRadius: 12,
              padding: 32,
              textAlign: 'center',
              cursor: 'pointer',
              transition: 'all 0.15s',
              background: bulkFile ? 'var(--accent-light)' : 'var(--bg)',
            }}
            onClick={() => fileRef.current.click()}
            onDragOver={e => e.preventDefault()}
            onDrop={e => { e.preventDefault(); setBulkFile(e.dataTransfer.files[0]); setBulkResult(null); }}
          >
            <input ref={fileRef} type="file" accept=".xlsx,.xls" style={{ display: 'none' }}
              onChange={e => { setBulkFile(e.target.files[0]); setBulkResult(null); }} />
            <div style={{ fontSize: 36, marginBottom: 8 }}>{bulkFile ? '📄' : '☁️'}</div>
            {bulkFile ? (
              <div>
                <p style={{ fontWeight: 600, color: 'var(--accent)' }}>{bulkFile.name}</p>
                <p style={{ fontSize: 12, color: 'var(--text-muted)', marginTop: 4 }}>
                  {(bulkFile.size / 1024).toFixed(1)} KB - {t('clickToChange')}
                </p>
              </div>
            ) : (
              <div>
                <p style={{ fontWeight: 600 }}>{t('dropExcelFileHere')}</p>
                <p style={{ fontSize: 12, color: 'var(--text-muted)', marginTop: 4 }}>{t('orClickToBrowseExcel')}</p>
              </div>
            )}
          </div>

          {bulkResult?.imported?.length > 0 && (
            <div className="alert alert-success" style={{ marginTop: 16 }}>
                ✅ {t('successfullyImportedAsDraftPrefix')} {bulkResult.imported.length} {t('questions')}
            </div>
          )}

          {bulkResult?.errors?.length > 0 && (
            <div className="alert alert-error" style={{ marginTop: 16 }}>
              <div>
                <strong>⚠️ {bulkResult.errors.length} row(s) had errors and were skipped:</strong>
                <ul style={{ marginTop: 8, paddingLeft: 18, fontSize: 12 }}>
                  {bulkResult.errors.map((e, i) => <li key={i}>{e}</li>)}
                </ul>
              </div>
            </div>
          )}

          {bulkErrors.length > 0 && (
            <div className="alert alert-error" style={{ marginTop: 16 }}>
              <div>
                <strong>❌ Upload failed:</strong>
                <ul style={{ marginTop: 8, paddingLeft: 18, fontSize: 12 }}>
                  {bulkErrors.map((e, i) => <li key={i}>{e}</li>)}
                </ul>
              </div>
            </div>
          )}

          <div className="bulk-upload-actions" style={{ marginTop: 16, display: 'flex', gap: 10, justifyContent: 'flex-end' }}>
            <button className="btn btn-primary" onClick={handleBulkUpload} disabled={!bulkFile || bulkLoading}>
              {bulkLoading ? <><span className="spinner" /> {t('uploading')}</> : `⬆️ ${t('uploadQuestions')}`}
            </button>
            {bulkResult && (
              <button className="btn btn-secondary" onClick={() => navigate('/my-questions')}>
                {t('viewMyQuestions')} →
              </button>
            )}
          </div>
        </div>
      </div>
    );
  }


  if (mode === 'ai') {
    return <AIGeneratePage onBack={() => setMode(null)} />;
  }




}

const modeCardStyle = {
  padding: 28,
  border: '2px solid var(--border)',
  borderRadius: 16,
  background: 'var(--bg-card)',
  cursor: 'pointer',
  textAlign: 'left',
  transition: 'all 0.2s',
  fontFamily: 'var(--font)',
  boxShadow: 'var(--shadow)',
};
