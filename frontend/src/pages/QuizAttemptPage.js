import React, { useEffect, useMemo, useRef, useState } from 'react';
import { useLocation, useNavigate, useParams } from 'react-router-dom';
import toast from 'react-hot-toast';
import { quizPublicAPI } from '../services/api';
import { setParticipantSession } from '../services/participantSession';
import { useLanguage } from '../context/LanguageContext';

const sanitizeQuestionStem = (stem) => {
  if (!stem) return '';
  return stem.replace(/^\[SMOKE-[^\]]+\]\s*/i, '');
};

const toWordCount = (count) => {
  const wordMap = {
    1: 'one',
    2: 'two',
    3: 'three',
    4: 'four',
    5: 'five',
    6: 'six',
  };
  return wordMap[count] || String(count);
};

const formatTimeTaken = (seconds) => {
  const safe = Number.isFinite(Number(seconds)) ? Math.max(0, Number(seconds)) : 0;
  const mins = Math.floor(safe / 60);
  const secs = safe % 60;
  return `${mins}:${String(secs).padStart(2, '0')}`;
};

const participantRankLabel = (rank) => {
  const safeRank = Number(rank);
  if (safeRank === 1) return '🥇';
  if (safeRank === 2) return '🥈';
  if (safeRank === 3) return '🥉';
  return `#${rank}`;
};

export default function QuizAttemptPage() {
  const { quizCode } = useParams();
  const location = useLocation();
  const navigate = useNavigate();
  const { language, t } = useLanguage();
  const draftStorageKey = `quiz-attempt-draft:${quizCode}`;

  const [quiz, setQuiz] = useState(null);
  const [leaderboard, setLeaderboard] = useState(null);
  const [loading, setLoading] = useState(true);
  const [submitting, setSubmitting] = useState(false);
  const [employeeLoggingIn, setEmployeeLoggingIn] = useState(false);
  const [error, setError] = useState('');

  const [mode, setMode] = useState('guest');
  const [guestName, setGuestName] = useState('');
  const [employeeId, setEmployeeId] = useState('');
  const [employeePassword, setEmployeePassword] = useState('');
  const [employeeProfile, setEmployeeProfile] = useState(null);

  const [answers, setAnswers] = useState({});
  const [submittedResult, setSubmittedResult] = useState(null);
  const [showLeaderboard, setShowLeaderboard] = useState(false);
  const [attemptStep, setAttemptStep] = useState(1);
  const [questionTypeFilter, setQuestionTypeFilter] = useState('ALL');
  const [currentQuestionIndex, setCurrentQuestionIndex] = useState(0);
  const [attemptStartedAtMs, setAttemptStartedAtMs] = useState(null);
  const [timeLeftSeconds, setTimeLeftSeconds] = useState(null);
  const [lastThreeMinAlertShown, setLastThreeMinAlertShown] = useState(false);
  const [isMobile, setIsMobile] = useState(() => (typeof window !== 'undefined' ? window.innerWidth <= 768 : false));
  const leaderboardSectionRef = useRef(null);

  useEffect(() => {
    const onResize = () => setIsMobile(window.innerWidth <= 768);
    window.addEventListener('resize', onResize);
    return () => window.removeEventListener('resize', onResize);
  }, []);

  const loadQuiz = async () => {
    try {
      const participantProfile = location.state?.participantProfile;
      const quizIdentity = {
        participantEnterpriseId: participantProfile?.participantEnterpriseId || '',
        participantName: participantProfile?.participantName || '',
        languageCode: language,
      };
      const [quizRes, leaderboardRes] = await Promise.all([
        quizPublicAPI.getQuiz(quizCode, quizIdentity),
        quizPublicAPI.getLeaderboard(quizCode),
      ]);
      setQuiz(quizRes.data);
      setLeaderboard(leaderboardRes.data);
      setError('');

      if (quizRes.data?.participantNotice && quizRes.data?.canAttempt) {
        toast.success(quizRes.data.participantNotice);
      }
    } catch (err) {
      setError(err.response?.data?.message || t('failedToLoadQuizLink'));
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadQuiz();
  }, [quizCode, language]);

  useEffect(() => {
    const profile = location.state?.participantProfile;
    if (!profile) return;

    if (profile.participantType === 'EMPLOYEE' && profile.participantEnterpriseId) {
      setMode('employee');
      setEmployeeId(profile.participantEnterpriseId);
      setEmployeeProfile({
        enterpriseId: profile.participantEnterpriseId,
        fullName: profile.participantName || '',
        authToken: profile.authToken || '',
      });
      setAttemptStep(2);
      return;
    }

    if (profile.participantName) {
      setMode('guest');
      setGuestName(profile.participantName);
      setAttemptStep(2);
    }
  }, [location.state]);

  const handleGuestMode = () => {
    setMode('guest');
    setEmployeeProfile(null);
    setEmployeeId('');
    setEmployeePassword('');
  };

  const handleEmployeeLogin = async () => {
    if (!employeeId || !employeePassword) {
      toast.error(t('enterEmployeeIdPassword'));
      return;
    }

    setEmployeeLoggingIn(true);
    try {
      const res = await quizPublicAPI.participantLogin(quizCode, {
        enterpriseId: employeeId,
        password: employeePassword,
      });
      setEmployeeProfile(res.data);
      setMode('employee');
      setAttemptStep(2);
      toast.success(`${t('signedInAs')} ${res.data.fullName}`);
    } catch (err) {
      toast.error(err.response?.data?.message || t('employeeLoginFailed'));
      setEmployeeProfile(null);
    } finally {
      setEmployeeLoggingIn(false);
    }
  };

  const participantName = mode === 'employee'
    ? (employeeProfile?.fullName || '')
    : guestName.trim();

  const hasParticipantIdentity = useMemo(() => {
    if (mode === 'employee') {
      return Boolean(employeeProfile?.enterpriseId);
    }
    return Boolean(guestName.trim());
  }, [mode, employeeProfile, guestName]);

  const totalQuestions = quiz?.questions?.length || 0;
  const answeredQuestions = useMemo(() => {
    return (quiz?.questions || []).reduce((count, question) => {
      const selected = answers[question.questionId] || [];
      return selected.length > 0 ? count + 1 : count;
    }, 0);
  }, [quiz, answers]);

  const unansweredCount = Math.max(totalQuestions - answeredQuestions, 0);
  const resolvedDurationMinutes = Math.max(1, Number(quiz?.durationMinutes || 15));

  const canSubmit = useMemo(() => {
    if (!quiz?.canAttempt) return false;
    if (!hasParticipantIdentity || !participantName) return false;
    if (totalQuestions === 0) return false;
    if (answeredQuestions !== totalQuestions) return false;
    return true;
  }, [quiz, hasParticipantIdentity, participantName, totalQuestions, answeredQuestions]);

  const visibleQuestions = useMemo(() => {
    const all = quiz?.questions || [];
    if (questionTypeFilter === 'ALL') return all;
    return all.filter((q) => q.questionType === questionTypeFilter);
  }, [quiz, questionTypeFilter]);

  const shownQuestions = useMemo(() => {
    if (!isMobile) return visibleQuestions;
    if (!visibleQuestions.length) return [];
    return [visibleQuestions[Math.min(currentQuestionIndex, visibleQuestions.length - 1)]];
  }, [isMobile, visibleQuestions, currentQuestionIndex]);

  useEffect(() => {
    setCurrentQuestionIndex(0);
  }, [questionTypeFilter, attemptStep]);

  useEffect(() => {
    if (currentQuestionIndex > 0 && currentQuestionIndex >= visibleQuestions.length) {
      setCurrentQuestionIndex(Math.max(visibleQuestions.length - 1, 0));
    }
  }, [visibleQuestions.length, currentQuestionIndex]);

  useEffect(() => {
    if (!hasParticipantIdentity || attemptStep !== 2) return;
    try {
      const savedJson = localStorage.getItem(draftStorageKey);
      if (!savedJson) return;
      const saved = JSON.parse(savedJson);
      if (saved?.participantKey !== (mode === 'employee' ? employeeProfile?.enterpriseId : guestName.trim())) {
        return;
      }
      if (saved?.answers && typeof saved.answers === 'object') {
        setAnswers(saved.answers);
        if (typeof saved.startedAtMs === 'number' && saved.startedAtMs > 0) {
          setAttemptStartedAtMs(saved.startedAtMs);
        }
        toast(t('loadedSavedResponses'), { icon: '💾' });
      }
    } catch {
      // Ignore malformed drafts.
    }
  }, [attemptStep, hasParticipantIdentity, mode, employeeProfile, guestName, draftStorageKey]);

  const setMcqAnswer = (questionId, choice) => {
    setAnswers((prev) => ({ ...prev, [questionId]: [choice] }));
  };

  const toggleMsqAnswer = (questionId, choice) => {
    setAnswers((prev) => {
      const current = prev[questionId] || [];
      const exists = current.includes(choice);
      const next = exists ? current.filter((c) => c !== choice) : [...current, choice];
      return { ...prev, [questionId]: next };
    });
  };

  const submitAttempt = async () => {
    if (!canSubmit) {
      if (!hasParticipantIdentity) {
        toast.error(t('enterNameOrCompleteEmployeeLogin'));
        return;
      }
      if (unansweredCount > 0) {
        toast.error(`${t('pleaseAnswerAllQuestionsBeforeSubmitting')} (${unansweredCount} ${t('left')})`);
        return;
      }
      toast.error(t('completeRequiredDetailsBeforeSubmitting'));
      return;
    }

    const confirmSubmit = window.confirm(t('areYouSureToSubmit'));
    if (!confirmSubmit) {
      return;
    }

    const elapsedSeconds = attemptStartedAtMs
      ? Math.max(0, Math.floor((Date.now() - attemptStartedAtMs) / 1000))
      : 0;

    const payload = {
      participantName,
      participantEnterpriseId: mode === 'employee' ? employeeProfile?.enterpriseId : null,
      participantAuthToken: mode === 'employee' ? employeeProfile?.authToken : null,
      elapsedSeconds,
      autoSubmitted: false,
      answers: (quiz?.questions || []).map((question) => ({
        questionId: question.questionId,
        selectedAnswers: answers[question.questionId] || [],
      })),
    };

    setSubmitting(true);
    try {
      const res = await quizPublicAPI.submitAttempt(quizCode, payload);
      setSubmittedResult(res.data);
      setAttemptStep(3);
      setShowLeaderboard(false);
      localStorage.removeItem(draftStorageKey);
      setTimeLeftSeconds(0);
      toast.success(`${t('submittedScore')}: ${res.data.score}/${res.data.totalQuestions}`);
      const leaderboardRes = await quizPublicAPI.getLeaderboard(quizCode);
      setLeaderboard(leaderboardRes.data);
    } catch (err) {
      toast.error(err.response?.data?.message || t('failedToSubmitAttempt'));
    } finally {
      setSubmitting(false);
    }
  };

  const autoSubmitAttempt = async () => {
    if (submitting || submittedResult || !quiz?.canAttempt || !hasParticipantIdentity) {
      return;
    }

    const elapsedSeconds = attemptStartedAtMs
      ? Math.max(0, Math.floor((Date.now() - attemptStartedAtMs) / 1000))
      : (resolvedDurationMinutes * 60);

    const payload = {
      participantName,
      participantEnterpriseId: mode === 'employee' ? employeeProfile?.enterpriseId : null,
      participantAuthToken: mode === 'employee' ? employeeProfile?.authToken : null,
      elapsedSeconds,
      autoSubmitted: true,
      answers: (quiz?.questions || []).map((question) => ({
        questionId: question.questionId,
        selectedAnswers: answers[question.questionId] || [],
      })),
    };

    setSubmitting(true);
    try {
      const res = await quizPublicAPI.submitAttempt(quizCode, payload);
      setSubmittedResult(res.data);
      setAttemptStep(3);
      setShowLeaderboard(false);
      localStorage.removeItem(draftStorageKey);
      setTimeLeftSeconds(0);
      toast.success(t('timeUpQuizAutoSubmitted'));
      const leaderboardRes = await quizPublicAPI.getLeaderboard(quizCode);
      setLeaderboard(leaderboardRes.data);
    } catch (err) {
      toast.error(err.response?.data?.message || t('autoSubmitFailed'));
    } finally {
      setSubmitting(false);
    }
  };

  const saveResponses = () => {
    if (!hasParticipantIdentity) {
      toast.error(t('completeIdentityBeforeSavingResponses'));
      return;
    }
    const participantKey = mode === 'employee' ? employeeProfile?.enterpriseId : guestName.trim();
    localStorage.setItem(draftStorageKey, JSON.stringify({
      participantKey,
      answers,
      startedAtMs: attemptStartedAtMs || Date.now(),
      savedAt: new Date().toISOString(),
    }));
    toast.success(t('responsesSaved'));
  };

  const goToParticipantHome = () => {
    if (mode === 'employee' && employeeProfile?.enterpriseId) {
      setParticipantSession(JSON.stringify({
        enterpriseId: employeeProfile.enterpriseId,
        fullName: employeeProfile.fullName,
        authToken: employeeProfile.authToken,
        participantType: 'EMPLOYEE',
        preferredLanguage: language,
      }));
    } else if (participantName) {
      setParticipantSession(JSON.stringify({
        enterpriseId: null,
        fullName: participantName,
        authToken: null,
        participantType: 'GUEST',
        preferredLanguage: language,
      }));
    }
    navigate('/participant');
  };

  const continueAsGuest = () => {
    if (!guestName.trim()) {
      toast.error(t('enterYourName'));
      return;
    }
    if (!attemptStartedAtMs) {
      setAttemptStartedAtMs(Date.now());
    }
    setAttemptStep(2);
  };

  useEffect(() => {
    if (!quiz?.canAttempt || submittedResult || attemptStep !== 2 || !hasParticipantIdentity) {
      return;
    }

    if (!attemptStartedAtMs) {
      setAttemptStartedAtMs(Date.now());
    }
  }, [quiz, submittedResult, attemptStep, hasParticipantIdentity, attemptStartedAtMs]);

  useEffect(() => {
    if (!quiz?.canAttempt || submittedResult || attemptStep !== 2 || !hasParticipantIdentity || !attemptStartedAtMs) {
      return;
    }

    const totalDurationSeconds = resolvedDurationMinutes * 60;
    if (totalDurationSeconds <= 0) {
      return;
    }

    const tick = () => {
      const elapsed = Math.max(0, Math.floor((Date.now() - attemptStartedAtMs) / 1000));
      const left = Math.max(totalDurationSeconds - elapsed, 0);
      setTimeLeftSeconds(left);

      if (left <= 180 && left > 0 && !lastThreeMinAlertShown) {
        setLastThreeMinAlertShown(true);
        window.alert(t('lastThreeMinutesWarning'));
      }

      if (left <= 0) {
        clearInterval(intervalId);
        autoSubmitAttempt();
      }
    };

    tick();
    const intervalId = setInterval(tick, 1000);
    return () => clearInterval(intervalId);
  }, [quiz, submittedResult, attemptStep, hasParticipantIdentity, attemptStartedAtMs, lastThreeMinAlertShown, resolvedDurationMinutes]);

  if (loading) {
    return <div className="loading-container"><div className="spinner spinner-dark" /></div>;
  }

  if (error) {
    return (
      <div style={{ maxWidth: 860, margin: '24px auto', padding: '0 16px' }}>
        <div className="card">
          <h2 style={{ marginBottom: 10 }}>{t('quizLinkError')}</h2>
          <p style={{ color: 'var(--text-secondary)' }}>{error}</p>
        </div>
      </div>
    );
  }

  return (
    <div style={{ maxWidth: 1080, margin: '24px auto', padding: '0 16px 40px' }}>
      <div
        className="card"
        style={{
          marginBottom: 16,
          background: 'linear-gradient(135deg, #0f172a 0%, #1e3a8a 55%, #0ea5e9 100%)',
          color: '#fff',
          border: 'none',
          boxShadow: 'var(--shadow-lg)',
        }}
      >
        <h1 style={{ fontSize: 26, marginBottom: 4 }}>{quiz.title}</h1>
        <div style={{ marginTop: 14, display: 'flex', gap: 10, flexWrap: 'wrap' }}>
          <span style={{ background: 'rgba(255,255,255,0.16)', borderRadius: 999, padding: '4px 10px', fontSize: 12 }}>
            {t('status')}: {quiz.status}
          </span>
          {quiz.canAttempt && (
            <>
              <span style={{ background: 'rgba(255,255,255,0.16)', borderRadius: 999, padding: '4px 10px', fontSize: 12 }}>
                {t('duration')}: {resolvedDurationMinutes} {t('minutes')}
              </span>
              <span style={{ background: 'rgba(255,255,255,0.16)', borderRadius: 999, padding: '4px 10px', fontSize: 12 }}>
                {t('questions')}: {quiz.questionCount}
              </span>
              <span style={{ background: 'rgba(255,255,255,0.16)', borderRadius: 999, padding: '4px 10px', fontSize: 12 }}>
                {t('answered')}: {answeredQuestions}/{totalQuestions}
              </span>
            </>
          )}
        </div>
      </div>

      {!quiz.canAttempt && quiz.participantNotice && (
        <div className="alert alert-info" style={{ marginBottom: 16 }}>
          {quiz.participantNotice}
        </div>
      )}

      {quiz.canAttempt && !submittedResult && attemptStep === 2 && hasParticipantIdentity && timeLeftSeconds !== null && (
        <div className="quiz-timer-floating">
          <div
            style={{
              background: timeLeftSeconds <= 180 ? 'rgba(220, 38, 38, 0.96)' : 'rgba(15, 23, 42, 0.96)',
              color: '#fff',
              borderRadius: 999,
              padding: '8px 16px',
              fontWeight: 700,
              fontSize: 13,
              letterSpacing: '0.02em',
              boxShadow: '0 8px 24px rgba(15, 23, 42, 0.2)',
              pointerEvents: 'auto',
            }}
          >
            {t('timeLeft')}: {Math.floor(timeLeftSeconds / 60)}:{String(timeLeftSeconds % 60).padStart(2, '0')}
          </div>
        </div>
      )}

      {quiz.canAttempt && !submittedResult && attemptStep === 1 && (
        <div className="card" style={{ marginBottom: 16 }}>
          <h3 style={{ marginBottom: 12 }}>{t('chooseLoginMethod')}</h3>
          <p style={{ color: 'var(--text-secondary)', marginBottom: 10 }}>
            {t('quizIdentityRequiredMessage')}
          </p>
          <div className="mobile-action-row" style={{ display: 'flex', gap: 10, marginBottom: 12 }}>
            <button
              className={`btn btn-sm ${mode === 'guest' ? 'btn-primary' : 'btn-secondary'}`}
              onClick={handleGuestMode}
            >
              {t('continueAsGuest')}
            </button>
            <button
              className={`btn btn-sm ${mode === 'employee' ? 'btn-primary' : 'btn-secondary'}`}
              onClick={() => {
                setMode('employee');
                setGuestName('');
              }}
            >
              {t('loginWithEmployeeId')}
            </button>
          </div>

          {mode === 'guest' ? (
            <div className="form-group" style={{ maxWidth: 420 }}>
              <label className="form-label">{t('yourName')} <span style={{ color: '#dc2626' }}>*</span></label>
              <input
                className="form-input"
                value={guestName}
                onChange={(e) => setGuestName(e.target.value)}
                placeholder={t('enterYourFullName')}
              />
              <button className="btn btn-primary" style={{ marginTop: 10 }} onClick={continueAsGuest}>
                {t('continueToQuestions')}
              </button>
            </div>
          ) : (
            <div style={{ maxWidth: 460 }}>
              {employeeProfile ? (
                <>
                  <div className="alert alert-success">
                    {t('loggedInAs')} <strong>{employeeProfile.fullName}</strong> ({employeeProfile.enterpriseId})
                  </div>
                  <button className="btn btn-primary" onClick={() => setAttemptStep(2)}>
                    {t('continueToQuestions')}
                  </button>
                </>
              ) : (
                <>
                  <div className="form-group">
                    <label className="form-label">{t('enterpriseId')} <span style={{ color: '#dc2626' }}>*</span></label>
                    <input className="form-input" value={employeeId} onChange={(e) => setEmployeeId(e.target.value)} />
                  </div>
                  <div className="form-group">
                    <label className="form-label">{t('password')} <span style={{ color: '#dc2626' }}>*</span></label>
                    <input type="password" className="form-input" value={employeePassword} onChange={(e) => setEmployeePassword(e.target.value)} />
                  </div>
                  <button className="btn btn-secondary" onClick={handleEmployeeLogin} disabled={employeeLoggingIn}>
                    {employeeLoggingIn ? <><span className="spinner" /> {t('verifying')}</> : t('verifyEmployeeLogin')}
                  </button>
                </>
              )}
            </div>
          )}
        </div>
      )}

      {quiz.canAttempt && !submittedResult && attemptStep === 2 && (
        <div className="card" style={{ marginBottom: 16 }}>
          <div style={{ display: 'flex', justifyContent: 'space-between', gap: 12, alignItems: 'center', marginBottom: 12, flexWrap: 'wrap' }}>
            <h3>{t('attemptQuestions')}</h3>
            <span
              className={`badge ${unansweredCount === 0 ? 'badge-approved' : 'badge-under-review'}`}
              style={{ fontSize: 12 }}
            >
              {unansweredCount === 0 ? t('allQuestionsAnswered') : `${unansweredCount} ${t('unanswered')}`}
            </span>
          </div>

          <div className="mobile-action-row" style={{ display: 'flex', gap: 8, alignItems: 'center', flexWrap: 'wrap', marginBottom: 12 }}>
            <span style={{ fontSize: 13, color: 'var(--text-secondary)' }}>{t('filter')}:</span>
            <button
              type="button"
              className={`btn btn-sm ${questionTypeFilter === 'ALL' ? 'btn-primary' : 'btn-secondary'}`}
              onClick={() => setQuestionTypeFilter('ALL')}
            >
              {t('all')}
            </button>
            <button
              type="button"
              className={`btn btn-sm ${questionTypeFilter === 'MCQ' ? 'btn-primary' : 'btn-secondary'}`}
              onClick={() => setQuestionTypeFilter('MCQ')}
            >
              MCQ
            </button>
            <button
              type="button"
              className={`btn btn-sm ${questionTypeFilter === 'MSQ' ? 'btn-primary' : 'btn-secondary'}`}
              onClick={() => setQuestionTypeFilter('MSQ')}
            >
              MSQ
            </button>
            <span style={{ fontSize: 12, color: 'var(--text-muted)' }}>
              {t('showing')} {visibleQuestions.length} {t('of')} {totalQuestions}
            </span>
            {isMobile && visibleQuestions.length > 0 && (
              <span className="mobile-question-progress">
                {currentQuestionIndex + 1}/{visibleQuestions.length}
              </span>
            )}
          </div>

          {!hasParticipantIdentity && (
            <div className="alert alert-info" style={{ marginBottom: 14 }}>
              {t('unlockAnswerSelectionHint')}
            </div>
          )}

          <div style={{ display: 'flex', flexDirection: 'column', gap: 14 }}>
            {shownQuestions.map((question) => {
              const originalIndex = (quiz.questions || []).findIndex((q) => q.questionId === question.questionId);
              const options = [
                ['A', question.optionA],
                ['B', question.optionB],
                ['C', question.optionC],
                ['D', question.optionD],
                ['E', question.optionE],
                ['F', question.optionF],
              ].filter(([, value]) => value);

              const selected = answers[question.questionId] || [];
              const isAnswered = selected.length > 0;
              const shouldShowMsqHint = question.questionType === 'MSQ' && Number(question.msqRequiredSelections) > 1;

              return (
                <div
                  key={question.questionId}
                  style={{
                    border: isAnswered ? '1px solid #86efac' : '1px solid var(--border)',
                    background: isAnswered ? '#f0fdf4' : '#fff',
                    borderRadius: 12,
                    padding: 14,
                    transition: 'all 0.2s ease',
                  }}
                >
                  <p style={{ fontWeight: 700, marginBottom: 10 }}>
                    Q{originalIndex + 1}. {sanitizeQuestionStem(question.questionStem)}
                    {shouldShowMsqHint && (
                      <span style={{ marginLeft: 8, fontWeight: 500, fontSize: 12, color: 'var(--text-secondary)' }}>
                        ({t('selectAny')} {toWordCount(Number(question.msqRequiredSelections))})
                      </span>
                    )}
                  </p>

                  <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
                    {options.map(([letter, label]) => (
                      <label key={letter} style={{ display: 'flex', gap: 8, alignItems: 'flex-start' }}>
                        <input
                          className="quiz-option-input"
                          type={question.questionType === 'MSQ' ? 'checkbox' : 'radio'}
                          checked={selected.includes(letter)}
                          name={`question-${question.questionId}`}
                          disabled={!hasParticipantIdentity}
                          onChange={() => {
                            if (question.questionType === 'MSQ') {
                              toggleMsqAnswer(question.questionId, letter);
                            } else {
                              setMcqAnswer(question.questionId, letter);
                            }
                          }}
                        />
                        <span>{letter}. {label}</span>
                      </label>
                    ))}
                  </div>
                </div>
              );
            })}
          </div>

          {isMobile && visibleQuestions.length > 1 && (
            <div style={{ display: 'flex', justifyContent: 'space-between', gap: 8, marginTop: 12 }}>
              <button
                type="button"
                className="btn btn-secondary"
                onClick={() => setCurrentQuestionIndex((prev) => Math.max(prev - 1, 0))}
                disabled={currentQuestionIndex === 0}
              >
                {t('prev')}
              </button>
              <button
                type="button"
                className="btn btn-primary"
                onClick={() => setCurrentQuestionIndex((prev) => Math.min(prev + 1, visibleQuestions.length - 1))}
                disabled={currentQuestionIndex === visibleQuestions.length - 1}
              >
                {t('next')}
              </button>
            </div>
          )}

          <div className="mobile-submit-bar mobile-action-row" style={{ marginTop: 16, display: 'flex', gap: 10, flexWrap: 'wrap' }}>
            <button className="btn btn-secondary" onClick={saveResponses}>
              {t('saveResponses')}
            </button>
            <button className="btn btn-primary" onClick={submitAttempt} disabled={!canSubmit || submitting}>
              {submitting ? <><span className="spinner" /> {t('submitting')}</> : t('submitQuiz')}
            </button>
          </div>
          <p className="form-hint" style={{ marginTop: 8 }}>
            {t('submissionEnabledAfterAllAnswered')} {totalQuestions} {t('questions')}. 
          </p>
        </div>
      )}

      {submittedResult && (
        <div className="card" style={{ marginBottom: 16 }}>
          <h3 style={{ marginBottom: 6 }}>{t('submissionComplete')}</h3>
          <p style={{ color: 'var(--text-secondary)' }}>
            {submittedResult.participantName}, {t('yourScoreIs')} <strong>{submittedResult.score}/{submittedResult.totalQuestions}</strong>.
          </p>
          <p style={{ color: 'var(--text-secondary)', marginTop: 6 }}>
            {t('timeTakenToSubmit')}: <strong>{formatTimeTaken(submittedResult.timeTakenSeconds)}</strong>
          </p>
          <div className="mobile-action-row" style={{ display: 'flex', justifyContent: 'center', gap: 10, marginTop: 14, flexWrap: 'wrap' }}>
            <button className="btn btn-secondary" onClick={goToParticipantHome}>
              {t('goToHome')}
            </button>
            <button
              className="btn btn-primary"
              onClick={() => {
                setShowLeaderboard(true);
                setTimeout(() => {
                  leaderboardSectionRef.current?.scrollIntoView({ behavior: 'smooth', block: 'start' });
                }, 50);
              }}
            >
              {t('viewLeaderboard')}
            </button>
          </div>
        </div>
      )}

      {(showLeaderboard || !quiz.canAttempt) && (
      <div className="card" style={{ padding: 0 }} ref={leaderboardSectionRef}>
        <div style={{ padding: '14px 16px', borderBottom: '1px solid var(--border)', display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: 10, flexWrap: 'wrap' }}>
          <h3 style={{ fontSize: 16, fontWeight: 700 }}>{t('leaderboard')}</h3>
          {!quiz.canAttempt && (
            <button className="btn btn-secondary btn-sm" onClick={goToParticipantHome}>
              {t('goToHome')}
            </button>
          )}
        </div>

        {!leaderboard?.leaderboard?.length ? (
          <div className="empty-state"><h3>{t('noAttemptsYet')}</h3></div>
        ) : (
          <div className="table-wrapper mobile-cards">
            <table>
              <thead>
                <tr>
                  <th>{t('rank')}</th>
                  <th>{t('participant')}</th>
                  <th>{t('enterpriseId')}</th>
                  <th>{t('score')}</th>
                  <th>{t('timeTaken')}</th>
                  <th>{t('submitted')}</th>
                </tr>
              </thead>
              <tbody>
                {leaderboard.leaderboard.map((entry) => (
                  <tr key={`${entry.rank}-${entry.participantName}-${entry.submittedAt}`}>
                    <td data-label={t('rank')}>{participantRankLabel(entry.rank)}</td>
                    <td data-label={t('participant')}>{entry.participantName}</td>
                    <td data-label={t('enterpriseId')}>{entry.participantEnterpriseId || '-'}</td>
                    <td data-label={t('score')}>{entry.score}/{entry.totalQuestions}</td>
                    <td data-label={t('timeTaken')}>{formatTimeTaken(entry.timeTakenSeconds)}</td>
                    <td data-label={t('submitted')}>{new Date(entry.submittedAt).toLocaleString()}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>
      )}
    </div>
  );
}
