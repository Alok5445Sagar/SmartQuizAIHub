import React, { useEffect, useMemo, useState } from 'react';
import toast from 'react-hot-toast';
import { quizAdminAPI, quizPublicAPI, referenceAPI } from '../services/api';
import Pagination from '../components/Pagination';
import { useLanguage } from '../context/LanguageContext';

const QUESTION_TYPES = ['MCQ', 'MSQ'];
const AUTO_REFRESH_MS = 1000;

const sanitizeQuestionStem = (stem) => {
  if (!stem) return '';
  return stem.replace(/^\[SMOKE-[^\]]+\]\s*/i, '');
};

const formatTimeTaken = (seconds) => {
  const safe = Number.isFinite(Number(seconds)) ? Math.max(0, Number(seconds)) : 0;
  const mins = Math.floor(safe / 60);
  const secs = safe % 60;
  return `${mins}:${String(secs).padStart(2, '0')}`;
};

const formatPercent = (value) => {
  const numeric = Number(value);
  if (!Number.isFinite(numeric)) return '0.00%';
  return `${numeric.toFixed(2)}%`;
};

const toSafeNumber = (value) => {
  const numeric = Number(value);
  return Number.isFinite(numeric) ? numeric : 0;
};

const medal = (badge, rank) => {
  if (badge === 'GOLD' || rank === 1) return '🥇';
  if (badge === 'SILVER' || rank === 2) return '🥈';
  if (badge === 'BRONZE' || rank === 3) return '🥉';
  return `#${rank}`;
};

const toSessionRow = (entry) => {
  const totalQuestions = toSafeNumber(entry.totalQuestions);
  const score = toSafeNumber(entry.score);
  const scorePercent = totalQuestions > 0 ? (score / totalQuestions) * 100 : 0;
  return {
    rank: toSafeNumber(entry.rank),
    rankBadge: entry.rankBadge,
    participantName: entry.participantName || '-',
    participantEnterpriseId: entry.participantEnterpriseId || '',
    attemptedCount: toSafeNumber(entry.attemptedCount) || totalQuestions,
    correctCount: toSafeNumber(entry.correctCount) || score,
    wrongCount: toSafeNumber(entry.wrongCount),
    scorePercent,
    timeTakenSeconds: toSafeNumber(entry.timeTakenSeconds),
    quizzesPlayed: 1,
    topicsCovered: 1,
  };
};

const toTopicRow = (entry) => ({
  rank: toSafeNumber(entry.rank),
  rankBadge: entry.rankBadge,
  participantName: entry.participantName || '-',
  participantEnterpriseId: entry.participantEnterpriseId || '-',
  attemptedCount: toSafeNumber(entry.attemptedCount),
  correctCount: toSafeNumber(entry.correctCount),
  wrongCount: toSafeNumber(entry.wrongCount),
  scorePercent: toSafeNumber(entry.scorePercent),
  timeTakenSeconds: toSafeNumber(entry.averageTimeTakenSeconds),
  quizzesPlayed: toSafeNumber(entry.quizzesPlayed),
  topicsCovered: 1,
});

const toOverallRow = (entry) => ({
  rank: toSafeNumber(entry.rank),
  rankBadge: entry.rankBadge,
  participantName: entry.participantName || '-',
  participantEnterpriseId: entry.participantEnterpriseId || '-',
  attemptedCount: toSafeNumber(entry.attemptedCount),
  correctCount: toSafeNumber(entry.correctCount),
  wrongCount: toSafeNumber(entry.wrongCount),
  scorePercent: toSafeNumber(entry.scorePercent),
  timeTakenSeconds: toSafeNumber(entry.averageTimeTakenSeconds),
  quizzesPlayed: toSafeNumber(entry.quizzesPlayed),
  topicsCovered: toSafeNumber(entry.topicsCovered),
});

const buildDifficultySplit = (totalQuestions) => {
  const safeTotal = Math.max(1, totalQuestions || 1);
  const base = Math.floor(safeTotal / 3);
  const remainder = safeTotal % 3;
  return {
    easy: base + (remainder > 0 ? 1 : 0),
    medium: base + (remainder > 1 ? 1 : 0),
    hard: base,
  };
};

export default function AdminQuizDashboardPage() {
  const { t } = useLanguage();
  const [stacks, setStacks] = useState([]);
  const [topics, setTopics] = useState([]);
  const [hostedTopicOptions, setHostedTopicOptions] = useState([]);
  const [selectedStackIds, setSelectedStackIds] = useState([]);
  const [selectedTopicIds, setSelectedTopicIds] = useState([]);
  const [title, setTitle] = useState('');

  const [approvedQuestions, setApprovedQuestions] = useState([]);
  const [selectedQuestionIds, setSelectedQuestionIds] = useState([]);

  const [quizzesPage, setQuizzesPage] = useState(0);
  const [quizzesData, setQuizzesData] = useState(null);

  const [loadingQuestions, setLoadingQuestions] = useState(false);
  const [autoPicking, setAutoPicking] = useState(false);
  const [publishing, setPublishing] = useState(false);
  const [loadingQuizzes, setLoadingQuizzes] = useState(false);
  const [leaderboardQuiz, setLeaderboardQuiz] = useState(null);
  const [leaderboardData, setLeaderboardData] = useState(null);
  const [loadingLeaderboard, setLoadingLeaderboard] = useState(false);
  const [leaderboardView, setLeaderboardView] = useState('SESSION');
  const [participantTypeFilter, setParticipantTypeFilter] = useState('ALL');
  const [selectedTopicId, setSelectedTopicId] = useState('');
  const [topicLeaderboard, setTopicLeaderboard] = useState(null);
  const [overallLeaderboard, setOverallLeaderboard] = useState(null);
  const [loadingTopicLeaderboard, setLoadingTopicLeaderboard] = useState(false);
  const [loadingOverallLeaderboard, setLoadingOverallLeaderboard] = useState(false);
  const [sortBy, setSortBy] = useState('rank');
  const [sortOrder, setSortOrder] = useState('asc');
  const [difficultyDistribution, setDifficultyDistribution] = useState({
    easy: 5,
    medium: 5,
    hard: 5,
  });
  const [quizComposition, setQuizComposition] = useState({
    totalQuestions: 15,
    mcqCount: 10,
    msqCount: 5,
  });
  const [durationMinutes, setDurationMinutes] = useState(15);

  useEffect(() => {
    referenceAPI.getStacks()
      .then((res) => {
        setStacks(res.data || []);
      })
      .catch(() => toast.error(t('failedToLoadStacks')));
  }, []);

  const loadHostedTopicOptions = async () => {
    try {
      const res = await quizAdminAPI.getHostedTopicsForLeaderboard();
      setHostedTopicOptions(res.data || []);
    } catch {
      setHostedTopicOptions([]);
    }
  };

  useEffect(() => {
    loadHostedTopicOptions();
  }, []);

  useEffect(() => {
    if (!selectedStackIds.length) {
      setTopics([]);
      setSelectedTopicIds([]);
      return;
    }

    Promise.all(selectedStackIds.map((stackId) => referenceAPI.getTopics(stackId)))
      .then((responses) => {
        const merged = responses
          .flatMap((res) => res.data || [])
          .filter((topic, index, arr) => arr.findIndex((item) => item.topicId === topic.topicId) === index);
        setTopics(merged);

        setSelectedTopicIds((prev) => prev.filter((topicId) => merged.some((topic) => String(topic.topicId) === String(topicId))));
      })
      .catch(() => toast.error(t('failedToLoadTopics')));
  }, [selectedStackIds]);

  const fetchQuizzes = async (page = quizzesPage) => {
    setLoadingQuizzes(true);
    try {
      const res = await quizAdminAPI.getQuizzes(page, 10);
      setQuizzesData(res.data);
    } catch (err) {
      toast.error((err.response?.data?.detail || err.response?.data?.message) || t('failedToLoadPublishedQuizzes'));
    } finally {
      setLoadingQuizzes(false);
    }
  };

  useEffect(() => {
    fetchQuizzes(quizzesPage);
  }, [quizzesPage]);

  const loadApprovedQuestions = async () => {
    if (!selectedStackIds.length && !selectedTopicIds.length) {
      toast.error(t('selectTopicSubtopicFirst'));
      return;
    }

    setLoadingQuestions(true);
    try {
      const res = await quizAdminAPI.getApprovedQuestionsByFilters({
        stackIds: selectedStackIds,
        topicIds: selectedTopicIds,
      });
      const questions = res.data || [];
      setApprovedQuestions(questions);
      setSelectedQuestionIds([]);
      if (!questions.length) {
        toast(t('noApprovedQuestionsForTopic'));
      }
    } catch (err) {
      toast.error((err.response?.data?.detail || err.response?.data?.message) || t('failedToLoadApprovedQuestions'));
    } finally {
      setLoadingQuestions(false);
    }
  };

  const toggleSelectQuestion = (questionId) => {
    setSelectedQuestionIds(prev => {
      if (prev.includes(questionId)) {
        return prev.filter(id => id !== questionId);
      }
      if (prev.length >= quizComposition.totalQuestions) {
        toast.error(`${t('youCanSelectOnly')} ${quizComposition.totalQuestions} ${t('questions')}`);
        return prev;
      }
      return [...prev, questionId];
    });
  };

  const selectedQuestions = useMemo(
    () => approvedQuestions.filter(q => selectedQuestionIds.includes(q.questionId)),
    [approvedQuestions, selectedQuestionIds]
  );

  const selectedMcqCount = selectedQuestions.filter(q => q.questionType === 'MCQ').length;
  const selectedMsqCount = selectedQuestions.filter(q => q.questionType === 'MSQ').length;

  const selectedStackNames = useMemo(
    () => stacks.filter((stack) => selectedStackIds.includes(String(stack.stackId))).map((stack) => stack.stackName),
    [stacks, selectedStackIds]
  );

  const selectedTopicNames = useMemo(
    () => topics.filter((topic) => selectedTopicIds.includes(String(topic.topicId))).map((topic) => topic.topicName),
    [topics, selectedTopicIds]
  );

  const publishedQuizCount = toSafeNumber(quizzesData?.totalElements);
  const liveQuizCount = (quizzesData?.content || []).filter((quiz) => quiz.status === 'PUBLISHED').length;
  const selectionProgressPercent = quizComposition.totalQuestions > 0
    ? Math.min(100, Math.round((selectedQuestionIds.length / quizComposition.totalQuestions) * 100))
    : 0;

  const canPublish =
    quizComposition.totalQuestions > 0
    && quizComposition.mcqCount >= 0
    && quizComposition.msqCount >= 0
    && (quizComposition.mcqCount + quizComposition.msqCount === quizComposition.totalQuestions)
    && selectedQuestionIds.length === quizComposition.totalQuestions
    && selectedMcqCount === quizComposition.mcqCount
    && selectedMsqCount === quizComposition.msqCount;

  const handleDistributionChange = (key, value) => {
    const parsed = Number(value);
    const safe = Number.isNaN(parsed) ? 0 : Math.max(0, Math.min(quizComposition.totalQuestions, parsed));
    setDifficultyDistribution(prev => ({ ...prev, [key]: safe }));
  };

  const handleCompositionChange = (key, value) => {
    const parsed = Number(value);
    const safe = Number.isNaN(parsed) ? 0 : Math.max(0, Math.min(200, parsed));
    setQuizComposition((prev) => {
      if (key !== 'totalQuestions') {
        return { ...prev, [key]: safe };
      }

      const safeTotal = Math.max(1, safe);
      const nextMcq = Math.min(prev.mcqCount, safeTotal);
      const nextMsq = Math.max(0, safeTotal - nextMcq);
      setDifficultyDistribution(buildDifficultySplit(safeTotal));

      return {
        ...prev,
        totalQuestions: safeTotal,
        mcqCount: nextMcq,
        msqCount: nextMsq,
      };
    });
  };

  const handleStackToggle = (stackId) => {
    setSelectedStackIds((prev) => {
      const exists = prev.includes(stackId);
      return exists ? prev.filter((id) => id !== stackId) : [...prev, stackId];
    });
  };

  const handleTopicToggle = (topicId) => {
    setSelectedTopicIds((prev) => {
      const exists = prev.includes(topicId);
      return exists ? prev.filter((id) => id !== topicId) : [...prev, topicId];
    });
  };

  const handleSelectAllStacks = () => {
    setSelectedStackIds(stacks.map((stack) => String(stack.stackId)));
  };

  const handleClearStacks = () => {
    setSelectedStackIds([]);
    setSelectedTopicIds([]);
  };

  const handleSelectAllTopics = () => {
    setSelectedTopicIds(topics.map((topic) => String(topic.topicId)));
  };

  const handleClearTopics = () => {
    setSelectedTopicIds([]);
  };

  const handleAutoPick = async () => {
    if (!selectedStackIds.length && !selectedTopicIds.length) {
      toast.error(t('selectTopicSubtopicFirst'));
      return;
    }

    const totalRequested = difficultyDistribution.easy + difficultyDistribution.medium + difficultyDistribution.hard;
    if (quizComposition.totalQuestions <= 0) {
      toast.error(t('totalQuestionsMustBeGreaterThanZero'));
      return;
    }
    if (quizComposition.mcqCount + quizComposition.msqCount !== quizComposition.totalQuestions) {
      toast.error(t('mcqMsqMustMatchTotalQuestions'));
      return;
    }
    if (totalRequested !== quizComposition.totalQuestions) {
      toast.error(`${t('easyMediumHardMustTotal')} ${quizComposition.totalQuestions}`);
      return;
    }

    setAutoPicking(true);
    try {
      const topicIds = selectedTopicIds.map((id) => Number(id));
      const stackIds = selectedStackIds.map((id) => Number(id));

      const response = await quizAdminAPI.autoPickQuestions({
        topicIds,
        stackIds,
        totalQuestions: Number(quizComposition.totalQuestions),
        mcqCount: Number(quizComposition.mcqCount),
        msqCount: Number(quizComposition.msqCount),
        easyCount: Number(difficultyDistribution.easy),
        mediumCount: Number(difficultyDistribution.medium),
        hardCount: Number(difficultyDistribution.hard),
      });
      const pickedIds = response.data?.questionIds || [];

      if (!approvedQuestions.length || !pickedIds.every((id) => approvedQuestions.some((q) => q.questionId === id))) {
        const approvedRes = await quizAdminAPI.getApprovedQuestionsByFilters({ topicIds, stackIds });
        setApprovedQuestions(approvedRes.data || []);
      }

      setSelectedQuestionIds(pickedIds);
      toast.success(`${t('autoPicked')} ${pickedIds.length} ${t('questionsLabel')} (${t('unused')}: ${response.data?.unusedSelectedCount || 0}, ${t('reused')}: ${response.data?.reusedSelectedCount || 0})`);
    } catch (err) {
      toast.error((err.response?.data?.detail || err.response?.data?.message) || t('autoPickFailed'));
    } finally {
      setAutoPicking(false);
    }
  };

  const handlePublish = async () => {
    if (!canPublish) {
      toast.error(`${t('selectionMustBeExactly')} ${quizComposition.mcqCount} MCQ ${t('and')} ${quizComposition.msqCount} MSQ (${quizComposition.totalQuestions} ${t('total').toLowerCase()})`);
      return;
    }

    setPublishing(true);
    try {
      const response = await quizAdminAPI.publishQuiz({
        topicIds: selectedTopicIds.map((id) => Number(id)),
        totalQuestions: Number(quizComposition.totalQuestions),
        mcqCount: Number(quizComposition.mcqCount),
        msqCount: Number(quizComposition.msqCount),
        durationMinutes: Number(durationMinutes),
        title: title.trim(),
        questionIds: selectedQuestionIds,
      });
      toast.success(t('quizPublishedSuccessfully'));
      const link = response.data?.quizLink;
      if (link) {
        navigator.clipboard?.writeText(link).catch(() => {});
      }
      setSelectedQuestionIds([]);
      setTitle('');
      fetchQuizzes(0);
      loadHostedTopicOptions();
      setQuizzesPage(0);
    } catch (err) {
      toast.error((err.response?.data?.detail || err.response?.data?.message) || t('failedToPublishQuiz'));
    } finally {
      setPublishing(false);
    }
  };

  const handleCloseQuiz = async (quizId) => {
    try {
      await quizAdminAPI.closeQuiz(quizId);
      toast.success(t('quizClosed'));
      fetchQuizzes(quizzesPage);
    } catch (err) {
      toast.error((err.response?.data?.detail || err.response?.data?.message) || t('failedToCloseQuiz'));
    }
  };

  const handleReopenQuiz = async (quizId) => {
    try {
      await quizAdminAPI.reopenQuiz(quizId);
      toast.success(t('quizReopenedLeaderboardAvailable'));
      fetchQuizzes(quizzesPage);
    } catch (err) {
      toast.error((err.response?.data?.detail || err.response?.data?.message) || t('failedToReopenQuiz'));
    }
  };

  const handleRehostQuiz = async (quizId) => {
    try {
      const res = await quizAdminAPI.rehostQuiz(quizId);
      const newLink = res.data?.quizLink;
      if (newLink) {
        navigator.clipboard?.writeText(newLink).catch(() => {});
      }
      toast.success(t('closedQuizHostedAgain'));
      setQuizzesPage(0);
      fetchQuizzes(0);
      loadHostedTopicOptions();
    } catch (err) {
      toast.error((err.response?.data?.detail || err.response?.data?.message) || t('failedToHostClosedQuizAgain'));
    }
  };

  const handleDownloadLeaderboard = async (quiz) => {
    try {
      const res = await quizAdminAPI.downloadLeaderboardExcel(quiz.quizId);
      const blob = new Blob([res.data], { type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet' });
      const url = window.URL.createObjectURL(blob);
      const link = document.createElement('a');
      link.href = url;
      link.download = `${quiz.quizCode}-leaderboard.xlsx`;
      document.body.appendChild(link);
      link.click();
      link.remove();
      window.URL.revokeObjectURL(url);
      toast.success(t('leaderboardDownloaded'));
    } catch (err) {
      toast.error((err.response?.data?.detail || err.response?.data?.message) || t('failedToDownloadLeaderboard'));
    }
  };

  const handleViewLeaderboard = async (quiz) => {
    setLeaderboardView('SESSION');
    setLeaderboardQuiz(quiz);
    setLoadingLeaderboard(true);
    try {
      const res = await quizPublicAPI.getLeaderboard(quiz.quizCode);
      setLeaderboardData(res.data);
    } catch (err) {
      setLeaderboardData(null);
      toast.error((err.response?.data?.detail || err.response?.data?.message) || t('failedToLoadLeaderboard'));
    } finally {
      setLoadingLeaderboard(false);
    }
  };

  const handleLoadTopicLeaderboard = async () => {
    if (!selectedTopicId) {
      toast.error(t('selectTopicFirst'));
      return;
    }
    setLoadingTopicLeaderboard(true);
    try {
      const res = await quizAdminAPI.getTopicLeaderboard(Number(selectedTopicId), participantTypeFilter);
      setTopicLeaderboard(res.data);
    } catch (err) {
      setTopicLeaderboard(null);
      toast.error((err.response?.data?.detail || err.response?.data?.message) || t('failedToLoadTopicLeaderboard'));
    } finally {
      setLoadingTopicLeaderboard(false);
    }
  };

  const handleLoadOverallLeaderboard = async () => {
    setLoadingOverallLeaderboard(true);
    try {
      const res = await quizAdminAPI.getOverallLeaderboard(participantTypeFilter);
      setOverallLeaderboard(res.data);
    } catch (err) {
      setOverallLeaderboard(null);
      toast.error((err.response?.data?.detail || err.response?.data?.message) || t('failedToLoadOverallLeaderboard'));
    } finally {
      setLoadingOverallLeaderboard(false);
    }
  };

  const topicOptions = useMemo(() => {
    return (hostedTopicOptions || []).map((item) => ({
      topicId: Number(item.topicId),
      label: `${item.stackName || ''} / ${item.topicName || ''}`.trim(),
    }));
  }, [hostedTopicOptions]);

  useEffect(() => {
    if (leaderboardView === 'OVERALL') {
      handleLoadOverallLeaderboard();
    }
  }, [leaderboardView, participantTypeFilter]);

  useEffect(() => {
    if (leaderboardView === 'TOPIC' && selectedTopicId) {
      handleLoadTopicLeaderboard();
    }
  }, [leaderboardView, selectedTopicId, participantTypeFilter]);

  useEffect(() => {
    const refreshNow = async () => {
      if (document.visibilityState === 'hidden') return;

      try {
        const res = await quizAdminAPI.getQuizzes(quizzesPage, 10);
        setQuizzesData(res.data);
      } catch {
        // Silent auto-refresh; keep current data.
      }

      try {
        const res = await quizAdminAPI.getHostedTopicsForLeaderboard();
        setHostedTopicOptions(res.data || []);
      } catch {
        // Silent auto-refresh; keep current data.
      }

      if (selectedStackIds.length || selectedTopicIds.length) {
        try {
          const res = await quizAdminAPI.getApprovedQuestionsByFilters({
            stackIds: selectedStackIds,
            topicIds: selectedTopicIds,
          });
          const latestQuestions = res.data || [];
          setApprovedQuestions(latestQuestions);
          setSelectedQuestionIds((prev) => prev.filter((id) => latestQuestions.some((q) => q.questionId === id)));
        } catch {
          // Silent auto-refresh; keep current data.
        }
      }

      if (leaderboardView === 'SESSION' && leaderboardQuiz?.quizCode) {
        try {
          const res = await quizPublicAPI.getLeaderboard(leaderboardQuiz.quizCode);
          setLeaderboardData(res.data);
        } catch {
          // Silent auto-refresh; keep current data.
        }
      }

      if (leaderboardView === 'TOPIC' && selectedTopicId) {
        try {
          const res = await quizAdminAPI.getTopicLeaderboard(Number(selectedTopicId), participantTypeFilter);
          setTopicLeaderboard(res.data);
        } catch {
          // Silent auto-refresh; keep current data.
        }
      }

      if (leaderboardView === 'OVERALL') {
        try {
          const res = await quizAdminAPI.getOverallLeaderboard(participantTypeFilter);
          setOverallLeaderboard(res.data);
        } catch {
          // Silent auto-refresh; keep current data.
        }
      }

    };

    const intervalId = setInterval(refreshNow, AUTO_REFRESH_MS);

    const handleVisibility = () => {
      if (document.visibilityState === 'visible') {
        refreshNow();
      }
    };

    window.addEventListener('focus', refreshNow);
    document.addEventListener('visibilitychange', handleVisibility);

    return () => {
      clearInterval(intervalId);
      window.removeEventListener('focus', refreshNow);
      document.removeEventListener('visibilitychange', handleVisibility);
    };
  }, [
    quizzesPage,
    selectedStackIds,
    selectedTopicIds,
    leaderboardView,
    leaderboardQuiz,
    selectedTopicId,
    participantTypeFilter,
  ]);

  const sortableRows = useMemo(() => {
    let rows = [];
    if (leaderboardView === 'SESSION') {
      rows = (leaderboardData?.leaderboard || []).map(toSessionRow);
      rows = rows.filter((row) => {
        if (participantTypeFilter === 'ALL') return true;
        const hasEnterpriseId = !!(row.participantEnterpriseId && String(row.participantEnterpriseId).trim());
        return participantTypeFilter === 'EMPLOYEE' ? hasEnterpriseId : !hasEnterpriseId;
      });
    } else if (leaderboardView === 'TOPIC') {
      rows = (topicLeaderboard?.leaderboard || []).map(toTopicRow);
    } else {
      rows = (overallLeaderboard?.leaderboard || []).map(toOverallRow);
    }

    const dir = sortOrder === 'asc' ? 1 : -1;
    return rows.slice().sort((a, b) => {
      const av = a[sortBy];
      const bv = b[sortBy];
      if (typeof av === 'string' || typeof bv === 'string') {
        return String(av || '').localeCompare(String(bv || '')) * dir;
      }
      return (toSafeNumber(av) - toSafeNumber(bv)) * dir;
    });
  }, [leaderboardData, leaderboardView, overallLeaderboard, participantTypeFilter, sortBy, sortOrder, topicLeaderboard]);

  const toggleSort = (key) => {
    if (sortBy === key) {
      setSortOrder((prev) => (prev === 'asc' ? 'desc' : 'asc'));
      return;
    }
    setSortBy(key);
    setSortOrder(key === 'rank' ? 'asc' : 'desc');
  };

  const sortIndicator = (key) => {
    if (sortBy !== key) return '';
    return sortOrder === 'asc' ? ' ▲' : ' ▼';
  };

  const ariaSortValue = (key) => {
    if (sortBy !== key) return 'none';
    return sortOrder === 'asc' ? 'ascending' : 'descending';
  };

  const sortLabel = (key) => {
    const labels = {
      rank: t('rank'),
      participantName: t('participant'),
      quizzesPlayed: t('quizzes'),
      topicsCovered: t('topics'),
      attemptedCount: t('attempted'),
      correctCount: t('correct'),
      wrongCount: t('wrong'),
      scorePercent: t('score'),
      timeTakenSeconds: t('avgTime'),
    };
    return labels[key] || key;
  };

  const sortAnnouncement = `${sortLabel(sortBy)} sorted ${sortOrder === 'asc' ? 'ascending' : 'descending'}`;

  return (
    <div className="admin-quiz-dashboard-page">
      <div className="page-header">
        <div>
          <h1 className="page-title">{t('liveQuizzesDashboard')}</h1>
          <p className="page-subtitle">{t('createPublishManageQuizLinks')}</p>
        </div>
      </div>

      <div className="admin-quiz-sr-only" aria-live="polite" aria-atomic="true">
        {sortAnnouncement}
      </div>

      <div className="card admin-quiz-builder-card dashboard-token-shell" style={{ marginBottom: 20 }}>
        <div className="admin-quiz-kpi-strip">
          <div className="admin-quiz-kpi-item admin-quiz-kpi-blue">
            <span>{t('publishedQuizzes')}</span>
            <strong>{publishedQuizCount}</strong>
          </div>
          <div className="admin-quiz-kpi-item admin-quiz-kpi-green">
            <span>{t('liveQuizzes')}</span>
            <strong>{liveQuizCount}</strong>
          </div>
          <div className="admin-quiz-kpi-item admin-quiz-kpi-indigo">
            <span>{t('selected')}</span>
            <strong>{selectionProgressPercent}%</strong>
          </div>
          <div className="admin-quiz-kpi-item admin-quiz-kpi-amber">
            <span>{t('topicsSelected')}</span>
            <strong>{selectedStackNames.length}/{selectedTopicNames.length}</strong>
          </div>
        </div>

        <div className="grid-3" style={{ marginBottom: 14, alignItems: 'start' }}>
          <div className="form-group">
            <label className="form-label">{t('topics')}</label>
            <div style={{ display: 'flex', gap: 8, marginBottom: 8 }} role="group" aria-label={t('topics')}>
              <button
                type="button"
                className="btn btn-ghost btn-sm"
                onClick={handleSelectAllStacks}
                disabled={!stacks.length || selectedStackIds.length === stacks.length}
              >
                {t('selectAll')}
              </button>
              <button
                type="button"
                className="btn btn-ghost btn-sm"
                onClick={handleClearStacks}
                disabled={!selectedStackIds.length}
              >
                {t('clearAll')}
              </button>
            </div>
            <div
              style={{
                border: '1px solid var(--border)',
                borderRadius: 10,
                padding: 10,
                maxHeight: 180,
                overflowY: 'auto',
                background: 'var(--bg-card)',
              }}
            >
              {stacks.map((stack) => {
                const value = String(stack.stackId);
                const checked = selectedStackIds.includes(value);
                return (
                  <label key={stack.stackId} style={{ display: 'flex', gap: 8, alignItems: 'center', marginBottom: 8, fontSize: 13 }}>
                    <input
                      type="checkbox"
                      checked={checked}
                      onChange={() => handleStackToggle(value)}
                    />
                    <span>{stack.stackName}</span>
                  </label>
                );
              })}
              {!stacks.length && <span style={{ fontSize: 12, color: 'var(--text-muted)' }}>{t('noTopicsAvailable')}</span>}
            </div>
          </div>

          <div className="form-group">
            <label className="form-label">{t('subtopics')}</label>
            <div style={{ display: 'flex', gap: 8, marginBottom: 8 }} role="group" aria-label={t('subtopics')}>
              <button
                type="button"
                className="btn btn-ghost btn-sm"
                onClick={handleSelectAllTopics}
                disabled={!topics.length || selectedTopicIds.length === topics.length || !selectedStackIds.length}
              >
                {t('selectAll')}
              </button>
              <button
                type="button"
                className="btn btn-ghost btn-sm"
                onClick={handleClearTopics}
                disabled={!selectedTopicIds.length}
              >
                {t('clearAll')}
              </button>
            </div>
            <div
              style={{
                border: '1px solid var(--border)',
                borderRadius: 10,
                padding: 10,
                maxHeight: 180,
                overflowY: 'auto',
                background: selectedStackIds.length ? 'var(--bg-card)' : 'var(--bg)',
                opacity: selectedStackIds.length ? 1 : 0.6,
              }}
            >
              {topics.map((topic) => {
                const value = String(topic.topicId);
                const checked = selectedTopicIds.includes(value);
                return (
                  <label key={topic.topicId} style={{ display: 'flex', gap: 8, alignItems: 'center', marginBottom: 8, fontSize: 13 }}>
                    <input
                      type="checkbox"
                      checked={checked}
                      onChange={() => handleTopicToggle(value)}
                      disabled={!selectedStackIds.length}
                    />
                    <span>{topic.topicName}</span>
                  </label>
                );
              })}
              {!selectedStackIds.length && <span style={{ fontSize: 12, color: 'var(--text-muted)' }}>{t('selectAtLeastOneTopicFirst')}</span>}
              {selectedStackIds.length > 0 && !topics.length && <span style={{ fontSize: 12, color: 'var(--text-muted)' }}>{t('noSubtopicsAvailable')}</span>}
            </div>
          </div>

          <div className="form-group">
            <label className="form-label">{t('quizTitleOptional')}</label>
            <input
              className="form-input"
              value={title}
              onChange={(e) => setTitle(e.target.value)}
              placeholder={t('quizTitlePlaceholder')}
            />

            <div style={{ marginTop: 12, border: '1px solid var(--border)', borderRadius: 10, padding: 10, background: 'var(--bg)' }}>
              <div style={{ fontWeight: 700, marginBottom: 8, fontSize: 13 }}>{t('selectedFiltersWindow')}</div>
              <div style={{ fontSize: 12, color: 'var(--text-secondary)', marginBottom: 8 }}>
                {t('topicsSelected')}: {selectedStackNames.length} | {t('subtopicsSelected')}: {selectedTopicNames.length}
              </div>
              <div style={{ marginBottom: 8 }}>
                <div style={{ fontSize: 12, fontWeight: 600, color: 'var(--text-muted)', marginBottom: 4 }}>{t('topics')}</div>
                {selectedStackNames.length ? (
                  <div style={{ display: 'flex', flexWrap: 'wrap', gap: 6 }}>
                    {selectedStackNames.map((name) => (
                      <span key={name} style={{ fontSize: 11, background: '#ede9ff', color: '#5b21b6', padding: '2px 8px', borderRadius: 12 }}>{name}</span>
                    ))}
                  </div>
                ) : (
                  <div style={{ fontSize: 12, color: 'var(--text-muted)' }}>{t('noneSelected')}</div>
                )}
              </div>
              <div>
                <div style={{ fontSize: 12, fontWeight: 600, color: 'var(--text-muted)', marginBottom: 4 }}>{t('subtopics')}</div>
                {selectedTopicNames.length ? (
                  <div style={{ display: 'flex', flexWrap: 'wrap', gap: 6 }}>
                    {selectedTopicNames.map((name) => (
                      <span key={name} style={{ fontSize: 11, background: '#dbeafe', color: '#1d4ed8', padding: '2px 8px', borderRadius: 12 }}>{name}</span>
                    ))}
                  </div>
                ) : (
                  <div style={{ fontSize: 12, color: 'var(--text-muted)' }}>{t('noneSelected')}</div>
                )}
              </div>
            </div>
          </div>
        </div>

        <div className="admin-inline-form-row" style={{ display: 'flex', gap: 12, alignItems: 'flex-end', flexWrap: 'wrap', marginBottom: 10 }}>
          <div className="form-group" style={{ width: 130, marginBottom: 0 }}>
            <label className="form-label">{t('duration')} ({t('minutes')})</label>
            <input
              type="number"
              min={1}
              max={300}
              className="form-input"
              value={durationMinutes}
              onChange={(e) => {
                const parsed = Number(e.target.value);
                setDurationMinutes(Number.isNaN(parsed) ? 1 : Math.max(1, Math.min(300, parsed)));
              }}
            />
          </div>
          <div className="form-group" style={{ width: 150, marginBottom: 0 }}>
            <label className="form-label">{t('totalQuestions')}</label>
            <input
              type="number"
              min={1}
              max={200}
              className="form-input"
              value={quizComposition.totalQuestions}
              onChange={(e) => handleCompositionChange('totalQuestions', e.target.value)}
            />
          </div>
          <div className="form-group" style={{ width: 120, marginBottom: 0 }}>
            <label className="form-label">{t('mcqCount')}</label>
            <input
              type="number"
              min={0}
              max={200}
              className="form-input"
              value={quizComposition.mcqCount}
              onChange={(e) => handleCompositionChange('mcqCount', e.target.value)}
            />
          </div>
          <div className="form-group" style={{ width: 120, marginBottom: 0 }}>
            <label className="form-label">{t('msqCount')}</label>
            <input
              type="number"
              min={0}
              max={200}
              className="form-input"
              value={quizComposition.msqCount}
              onChange={(e) => handleCompositionChange('msqCount', e.target.value)}
            />
          </div>
          <span style={{ fontSize: 12, color: (quizComposition.mcqCount + quizComposition.msqCount) === quizComposition.totalQuestions ? 'var(--success)' : 'var(--danger)' }}>
            MCQ + MSQ: {quizComposition.mcqCount + quizComposition.msqCount}/{quizComposition.totalQuestions}
          </span>
        </div>

        <div className="admin-inline-form-row" style={{ display: 'flex', gap: 12, alignItems: 'flex-end', flexWrap: 'wrap', marginBottom: 10 }}>
          <div className="form-group" style={{ width: 130, marginBottom: 0 }}>
            <label className="form-label">{t('easy')}</label>
            <input
              type="number"
              min={0}
              max={quizComposition.totalQuestions}
              className="form-input"
              value={difficultyDistribution.easy}
              onChange={(e) => handleDistributionChange('easy', e.target.value)}
            />
          </div>
          <div className="form-group" style={{ width: 130, marginBottom: 0 }}>
            <label className="form-label">{t('medium')}</label>
            <input
              type="number"
              min={0}
              max={quizComposition.totalQuestions}
              className="form-input"
              value={difficultyDistribution.medium}
              onChange={(e) => handleDistributionChange('medium', e.target.value)}
            />
          </div>
          <div className="form-group" style={{ width: 130, marginBottom: 0 }}>
            <label className="form-label">{t('hard')}</label>
            <input
              type="number"
              min={0}
              max={quizComposition.totalQuestions}
              className="form-input"
              value={difficultyDistribution.hard}
              onChange={(e) => handleDistributionChange('hard', e.target.value)}
            />
          </div>
          <span style={{ fontSize: 12, color: (difficultyDistribution.easy + difficultyDistribution.medium + difficultyDistribution.hard) === quizComposition.totalQuestions ? 'var(--success)' : 'var(--danger)' }}>
            {t('totalCount')}: {difficultyDistribution.easy + difficultyDistribution.medium + difficultyDistribution.hard}/{quizComposition.totalQuestions}
          </span>
        </div>

        <div style={{ display: 'flex', gap: 10, alignItems: 'center', flexWrap: 'wrap' }} role="group" aria-label={t('quizActions')}>
          <button className="btn btn-secondary" onClick={loadApprovedQuestions} disabled={(!selectedStackIds.length && !selectedTopicIds.length) || loadingQuestions}>
            {loadingQuestions ? t('loading') : t('loadApprovedQuestions')}
          </button>
          <button className="btn btn-autopick" onClick={handleAutoPick} disabled={autoPicking || (!selectedStackIds.length && !selectedTopicIds.length)}>
            {autoPicking ? t('autoPicking') : t('autoPick')}
          </button>
          <button className="btn btn-primary" onClick={handlePublish} disabled={!canPublish || publishing}>
            {publishing ? t('publishing') : t('publishQuiz')}
          </button>

          <span style={{ fontSize: 12, color: 'var(--text-muted)' }}>
            {t('selected')}: {selectedQuestionIds.length}/{quizComposition.totalQuestions} | MCQ: {selectedMcqCount}/{quizComposition.mcqCount} | MSQ: {selectedMsqCount}/{quizComposition.msqCount}
          </span>
        </div>
      </div>

      <div className="card admin-quiz-section-card dashboard-token-shell" style={{ padding: 0, marginBottom: 20 }}>
        <div className="admin-quiz-section-head" style={{ padding: '14px 16px', borderBottom: '1px solid var(--border)', fontWeight: 700 }}>
          {t('approvedQuestionsForSelectedFilters')}
        </div>
        {loadingQuestions ? (
          <div className="loading-container"><div className="spinner spinner-dark" /></div>
        ) : approvedQuestions.length === 0 ? (
          <div className="empty-state">
            <div className="empty-icon">📘</div>
            <h3>{t('noQuestionsLoaded')}</h3>
              <p>{t('selectTopicsAndLoadApproved')}</p>
          </div>
        ) : (
          <div className="table-wrapper mobile-cards">
            <table>
              <thead>
                <tr>
                  <th>{t('select')}</th>
                  <th>{t('id')}</th>
                  <th>{t('type')}</th>
                  <th>{t('question')}</th>
                  <th>{t('difficulty')}</th>
                </tr>
              </thead>
              <tbody>
                {approvedQuestions.map(question => (
                  <tr key={question.questionId}>
                    <td data-label={t('select')}>
                      <input
                        type="checkbox"
                        checked={selectedQuestionIds.includes(question.questionId)}
                        onChange={() => toggleSelectQuestion(question.questionId)}
                      />
                    </td>
                    <td data-label={t('id')}>{question.questionId}</td>
                    <td data-label={t('type')}>{QUESTION_TYPES.includes(question.questionType) ? question.questionType : 'MCQ'}</td>
                    <td data-label={t('question')}>
                      <div className="truncate" title={sanitizeQuestionStem(question.questionStem)}>{sanitizeQuestionStem(question.questionStem)}</div>
                    </td>
                    <td data-label={t('difficulty')}>{question.difficulty}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>

      <div className="card admin-quiz-section-card dashboard-token-shell" style={{ padding: 0 }}>
        <div className="admin-quiz-section-head" style={{ padding: '14px 16px', borderBottom: '1px solid var(--border)', fontWeight: 700 }}>
          {t('publishedQuizzes')}
        </div>

        {loadingQuizzes ? (
          <div className="loading-container"><div className="spinner spinner-dark" /></div>
        ) : !quizzesData?.content?.length ? (
          <div className="empty-state">
            <div className="empty-icon">🚀</div>
            <h3>{t('noPublishedQuizzesYet')}</h3>
            <p>{t('publishFirstLiveQuiz')}</p>
          </div>
        ) : (
          <>
            <div className="table-wrapper mobile-cards">
              <table>
                <thead>
                  <tr>
                    <th>{t('title')}</th>
                    <th>{t('topic')}</th>
                    <th>{t('status')}</th>
                    <th>{t('questions')}</th>
                    <th>{t('duration')}</th>
                    <th>{t('actions')}</th>
                  </tr>
                </thead>
                <tbody>
                  {quizzesData.content.map(quiz => (
                    <tr key={quiz.quizId}>
                      <td data-label={t('title')}>{quiz.title}</td>
                      <td data-label={t('topic')}>{quiz.stackName} / {quiz.topicName}</td>
                      <td data-label={t('status')}>{quiz.status}</td>
                      <td data-label={t('questions')}>{quiz.questionCount}</td>
                      <td data-label={t('duration')}>{quiz.durationMinutes || 15} {t('minutes')}</td>
                      <td data-label={t('actions')}>
                        <div style={{ display: 'flex', gap: 8, alignItems: 'center', flexWrap: 'wrap' }}>
                          <button className="btn btn-secondary btn-sm" onClick={() => handleViewLeaderboard(quiz)}>
                            {t('viewLeaderboard')}
                          </button>
                          <button className="btn btn-secondary btn-sm" onClick={() => handleDownloadLeaderboard(quiz)}>
                            {t('downloadLeaderboard')}
                          </button>
                          {quiz.status === 'PUBLISHED' ? (
                            <button className="btn btn-danger btn-sm" onClick={() => handleCloseQuiz(quiz.quizId)}>
                              {t('closeQuiz')}
                            </button>
                          ) : (
                            <>
                              <button className="btn btn-success btn-sm" onClick={() => handleReopenQuiz(quiz.quizId)}>
                                {t('reopenQuiz')}
                              </button>
                              <button className="btn btn-primary btn-sm" onClick={() => handleRehostQuiz(quiz.quizId)}>
                                {t('hostAgainNew')}
                              </button>
                            </>
                          )}
                        </div>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
            <div style={{ padding: '12px 16px', borderTop: '1px solid var(--border)', display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
              <span style={{ fontSize: 12, color: 'var(--text-muted)' }}>
                {t('showing')} {quizzesData.content.length} {t('of')} {quizzesData.totalElements} {t('quizzes')}
              </span>
              <Pagination page={quizzesPage} totalPages={quizzesData.totalPages} onPageChange={setQuizzesPage} />
            </div>
          </>
        )}
      </div>

      <div className="card admin-quiz-section-card dashboard-token-shell" style={{ padding: 0, marginTop: 20 }}>
        <div className="admin-quiz-section-head" style={{ padding: '14px 16px', borderBottom: '1px solid var(--border)', fontWeight: 700 }}>
          {t('liveQuizLeaderboard')}
        </div>

        <div className="admin-quiz-leaderboard-toolbar dashboard-token-toolbar" style={{ padding: 16, borderBottom: '1px solid var(--border)' }}>
          <div style={{ display: 'flex', justifyContent: 'space-between', gap: 8, flexWrap: 'wrap' }}>
            <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap' }} role="group" aria-label={t('leaderboardView')}>
              <button className={`btn btn-sm ${leaderboardView === 'SESSION' ? 'btn-primary' : 'btn-secondary'}`} onClick={() => setLeaderboardView('SESSION')} aria-pressed={leaderboardView === 'SESSION'}>{t('session')}</button>
              <button className={`btn btn-sm ${leaderboardView === 'TOPIC' ? 'btn-primary' : 'btn-secondary'}`} onClick={() => setLeaderboardView('TOPIC')} aria-pressed={leaderboardView === 'TOPIC'}>{t('topicWise')}</button>
              <button className={`btn btn-sm ${leaderboardView === 'OVERALL' ? 'btn-primary' : 'btn-secondary'}`} onClick={() => setLeaderboardView('OVERALL')} aria-pressed={leaderboardView === 'OVERALL'}>{t('overall')}</button>
            </div>
            <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap' }} role="group" aria-label={t('participantType')}>
              <button className={`btn btn-sm ${participantTypeFilter === 'ALL' ? 'btn-primary' : 'btn-secondary'}`} onClick={() => setParticipantTypeFilter('ALL')} aria-pressed={participantTypeFilter === 'ALL'}>{t('all')}</button>
              <button className={`btn btn-sm ${participantTypeFilter === 'EMPLOYEE' ? 'btn-primary' : 'btn-secondary'}`} onClick={() => setParticipantTypeFilter('EMPLOYEE')} aria-pressed={participantTypeFilter === 'EMPLOYEE'}>{t('employee')}</button>
              <button className={`btn btn-sm ${participantTypeFilter === 'GUEST' ? 'btn-primary' : 'btn-secondary'}`} onClick={() => setParticipantTypeFilter('GUEST')} aria-pressed={participantTypeFilter === 'GUEST'}>{t('guest')}</button>
            </div>
          </div>
        </div>

        <div style={{ padding: 16, borderBottom: '1px solid var(--border)' }}>
          {leaderboardView === 'SESSION' && (
            <>
              <div style={{ fontWeight: 700, marginBottom: 8 }}>{t('sessionLeaderboard')}</div>
              <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap' }}>
                {(quizzesData?.content || []).slice(0, 10).map((quiz) => (
                  <button
                    key={`session-btn-${quiz.quizId}`}
                    className="btn btn-secondary btn-sm"
                    onClick={() => handleViewLeaderboard(quiz)}
                  >
                    {quiz.title}
                  </button>
                ))}
              </div>
              {!leaderboardQuiz && (
                <div style={{ marginTop: 8, fontSize: 12, color: 'var(--text-muted)' }}>
                  {t('selectSessionToLoadLeaderboard')}
                </div>
              )}
              {leaderboardQuiz && (
                <div style={{ marginTop: 8, fontSize: 12, color: 'var(--text-muted)' }}>
                  {t('showing')}: {leaderboardQuiz.title} ({leaderboardQuiz.quizCode})
                </div>
              )}
            </>
          )}

          {leaderboardView === 'TOPIC' && (
            <>
              <div style={{ fontWeight: 700, marginBottom: 8 }}>{t('topicWiseLeaderboard')}</div>
              <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap' }}>
                <select
                  className="form-select"
                  value={selectedTopicId}
                  onChange={(e) => setSelectedTopicId(e.target.value)}
                  style={{ minWidth: 220, width: '100%', maxWidth: 320 }}
                >
                  <option value="">{t('selectTopic')}</option>
                  {topicOptions.map((topic) => (
                    <option key={`lb-topic-${topic.topicId}`} value={topic.topicId}>{topic.label}</option>
                  ))}
                </select>
                <button className="btn btn-primary btn-sm" onClick={handleLoadTopicLeaderboard} disabled={loadingTopicLeaderboard}>
                  {loadingTopicLeaderboard ? t('loading') : t('loadTopicLeaderboard')}
                </button>
              </div>
                <div style={{ marginTop: 8, fontSize: 12, color: 'var(--text-muted)' }}>
                  {topicOptions.length ? t('selectTopicToLoadLeaderboard') : t('noTopicsToLoadLeaderboard')}
                </div>
            </>
          )}

          {leaderboardView === 'OVERALL' && (
            <>
              <div style={{ fontWeight: 700, marginBottom: 8 }}>{t('overallLeaderboard')}</div>
              <button className="btn btn-primary btn-sm" onClick={handleLoadOverallLeaderboard} disabled={loadingOverallLeaderboard}>
                {loadingOverallLeaderboard ? t('loading') : t('loadOverallLeaderboard')}
              </button>
            </>
          )}
        </div>

        {(loadingLeaderboard && leaderboardView === 'SESSION') || (loadingTopicLeaderboard && leaderboardView === 'TOPIC') || (loadingOverallLeaderboard && leaderboardView === 'OVERALL') ? (
          <div className="loading-container"><div className="spinner spinner-dark" /></div>
        ) : (
          <div className="table-wrapper mobile-cards">
            <table>
              <thead>
                <tr>
                  <th aria-sort={ariaSortValue('rank')}>
                    <button type="button" className="admin-quiz-sort-button" onClick={() => toggleSort('rank')}>
                      {t('rank')}{sortIndicator('rank')}
                    </button>
                  </th>
                  <th aria-sort={ariaSortValue('participantName')}>
                    <button type="button" className="admin-quiz-sort-button" onClick={() => toggleSort('participantName')}>
                      {t('participant')}{sortIndicator('participantName')}
                    </button>
                  </th>
                  <th aria-sort={ariaSortValue('quizzesPlayed')}>
                    <button type="button" className="admin-quiz-sort-button" onClick={() => toggleSort('quizzesPlayed')}>
                      {t('quizzes')}{sortIndicator('quizzesPlayed')}
                    </button>
                  </th>
                  <th aria-sort={ariaSortValue('topicsCovered')}>
                    <button type="button" className="admin-quiz-sort-button" onClick={() => toggleSort('topicsCovered')}>
                      {t('topics')}{sortIndicator('topicsCovered')}
                    </button>
                  </th>
                  <th aria-sort={ariaSortValue('attemptedCount')}>
                    <button type="button" className="admin-quiz-sort-button" onClick={() => toggleSort('attemptedCount')}>
                      {t('attempted')}{sortIndicator('attemptedCount')}
                    </button>
                  </th>
                  <th aria-sort={ariaSortValue('correctCount')}>
                    <button type="button" className="admin-quiz-sort-button" onClick={() => toggleSort('correctCount')}>
                      {t('correct')}{sortIndicator('correctCount')}
                    </button>
                  </th>
                  <th aria-sort={ariaSortValue('wrongCount')}>
                    <button type="button" className="admin-quiz-sort-button" onClick={() => toggleSort('wrongCount')}>
                      {t('wrong')}{sortIndicator('wrongCount')}
                    </button>
                  </th>
                  <th aria-sort={ariaSortValue('scorePercent')}>
                    <button type="button" className="admin-quiz-sort-button" onClick={() => toggleSort('scorePercent')}>
                      {t('score')}{sortIndicator('scorePercent')}
                    </button>
                  </th>
                  <th aria-sort={ariaSortValue('timeTakenSeconds')}>
                    <button type="button" className="admin-quiz-sort-button" onClick={() => toggleSort('timeTakenSeconds')}>
                      {t('avgTime')}{sortIndicator('timeTakenSeconds')}
                    </button>
                  </th>
                </tr>
              </thead>
              <tbody>
                {!sortableRows.length ? (
                  <tr>
                    <td colSpan={9} style={{ textAlign: 'center', color: 'var(--text-muted)' }}>
                      {t('noLeaderboardDataForView')}
                    </td>
                  </tr>
                ) : sortableRows.map((entry) => (
                  <tr key={`live-lb-row-${leaderboardView}-${entry.rank}-${entry.participantName}-${entry.participantEnterpriseId}`}>
                    <td data-label={t('rank')}>{medal(entry.rankBadge, entry.rank)}</td>
                    <td data-label={t('participant')}>
                      {entry.participantName}
                      <div style={{ fontSize: 11, color: 'var(--text-muted)' }}>{entry.participantEnterpriseId || '-'}</div>
                    </td>
                    <td data-label={t('quizzes')}>{entry.quizzesPlayed}</td>
                    <td data-label={t('topics')}>{entry.topicsCovered}</td>
                    <td data-label={t('attempted')}>{entry.attemptedCount}</td>
                    <td data-label={t('correct')}>{entry.correctCount}</td>
                    <td data-label={t('wrong')}>{entry.wrongCount}</td>
                    <td data-label={t('score')}>{formatPercent(entry.scorePercent)}</td>
                    <td data-label={t('avgTime')}>{formatTimeTaken(entry.timeTakenSeconds)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>
    </div>
  );
}
