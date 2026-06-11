import React, { useState, useRef } from 'react';
import { useNavigate } from 'react-router-dom';
import { mcqAPI } from '../services/api';
import MCQForm from '../components/MCQForm';
import toast from 'react-hot-toast';
import AIGeneratePage from './AIGeneratePage';

export default function AddQuestionPage() {
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
      toast.success('Question saved as Draft!');
      navigate('/my-questions');
    } catch (err) {
      const msg = err.response?.data?.message || 'Failed to save question';
      if (msg.toLowerCase().includes('duplicate')) {
        toast.error('❌ ' + msg, { duration: 5000 });
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
      toast.success('Question saved as Draft. Please use Edit page Duplicate Check, then Save & Send for Review.');
      navigate('/my-questions');
    } catch (err) {
      const msg = err.response?.data?.message || 'Failed to submit question';
      if (msg.toLowerCase().includes('duplicate')) {
        toast.error('❌ ' + msg, { duration: 5000 });
      } else {
        toast.error(msg);
      }
    } finally {
      setLoading(false);
    }
  };

  const handleBulkUpload = async () => {
    if (!bulkFile) return toast.error('Please select a file first');
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
        toast.success(`${res.data.length} questions imported successfully!`);
      }
      // HTTP 207 — partial success
      if (res.data?.imported) {
        setBulkResult(res.data);
        toast(`${res.data.imported.length} imported, ${res.data.errors.length} rows had errors.`, { icon: '⚠️' });
      }
    } catch (err) {
      const msg = err.response?.data?.message || 'Bulk upload failed';
      setBulkErrors(msg.split('\n'));
      toast.error('Upload failed. See errors below.');
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
      toast.error('Download failed');
    }
  };

  // Choose mode
  if (!mode) {
    return (
      <div>
        <div className="page-header">
          <div>
            <h1 className="page-title">Add Question</h1>
            <p className="page-subtitle">Choose how to add questions</p>
          </div>
        </div>
        <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr 1fr', gap: 20, maxWidth: 860 }}>
          <button onClick={() => setMode('single')} style={modeCardStyle}>
            <span style={{ fontSize: 40, display: 'block', marginBottom: 12 }}>✍️</span>
            <h3 style={{ fontSize: 16, fontWeight: 700, marginBottom: 6 }}>Add from UI</h3>
            <p style={{ fontSize: 13, color: 'var(--text-secondary)' }}>Create a single question using the form</p>
          </button>
          <button onClick={() => setMode('bulk')} style={modeCardStyle}>
            <span style={{ fontSize: 40, display: 'block', marginBottom: 12 }}>📊</span>
            <h3 style={{ fontSize: 16, fontWeight: 700, marginBottom: 6 }}>Bulk Upload</h3>
            <p style={{ fontSize: 13, color: 'var(--text-secondary)' }}>Upload multiple questions via Excel file</p>
          </button>
          <button onClick={() => setMode('ai')} style={{ ...modeCardStyle, borderColor: 'var(--accent)', background: 'var(--accent-light)' }}>
            <span style={{ fontSize: 40, display: 'block', marginBottom: 12 }}>🤖</span>
            <h3 style={{ fontSize: 16, fontWeight: 700, marginBottom: 6, color: 'var(--accent)' }}>Generate with AI</h3>
            <p style={{ fontSize: 13, color: 'var(--text-secondary)' }}>Generate questions using AI based on topic</p>
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
            <h1 className="page-title">Add Single Question</h1>
            <p className="page-subtitle">Fill in the question details below</p>
          </div>
          <button className="btn btn-ghost" onClick={() => setMode(null)}>← Back</button>
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
            <h1 className="page-title">Bulk Upload Questions</h1>
            <p className="page-subtitle">Upload multiple questions using an Excel file</p>
          </div>
          <button className="btn btn-ghost" onClick={() => setMode(null)}>← Back</button>
        </div>

        <div className="card" style={{ maxWidth: 560 }}>
          <div style={{ textAlign: 'center', marginBottom: 24 }}>
            <p style={{ fontSize: 13, color: 'var(--text-secondary)', marginBottom: 16 }}>
              Download the template, fill in your questions, then upload the file.
            </p>
            <button className="btn btn-secondary" onClick={handleDownloadTemplate}>
              📥 Download Template (template.xlsx)
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
                  {(bulkFile.size / 1024).toFixed(1)} KB — Click to change
                </p>
              </div>
            ) : (
              <div>
                <p style={{ fontWeight: 600 }}>Drop your Excel file here</p>
                <p style={{ fontSize: 12, color: 'var(--text-muted)', marginTop: 4 }}>or click to browse (.xlsx, .xls)</p>
              </div>
            )}
          </div>

          {bulkResult?.imported?.length > 0 && (
            <div className="alert alert-success" style={{ marginTop: 16 }}>
              ✅ Successfully imported {bulkResult.imported.length} questions as Draft.
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

          <div style={{ marginTop: 16, display: 'flex', gap: 10, justifyContent: 'flex-end' }}>
            <button className="btn btn-primary" onClick={handleBulkUpload} disabled={!bulkFile || bulkLoading}>
              {bulkLoading ? <><span className="spinner" /> Uploading...</> : '⬆️ Upload Questions'}
            </button>
            {bulkResult && (
              <button className="btn btn-secondary" onClick={() => navigate('/my-questions')}>
                View My Questions →
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
