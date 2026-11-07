import React, { useEffect, useMemo, useState } from 'react';
import toast from 'react-hot-toast';
import { quizAdminAPI } from '../services/api';
import { useLanguage } from '../context/LanguageContext';

const formatPercent = (value) => {
  const numeric = Number(value);
  if (!Number.isFinite(numeric)) return '0.00%';
  return `${numeric.toFixed(2)}%`;
};

const toSafeNumber = (value) => {
  const numeric = Number(value);
  return Number.isFinite(numeric) ? numeric : 0;
};

const clampPercent = (value) => Math.max(0, Math.min(100, toSafeNumber(value)));

const toParticipantKey = (row) => `${row.participantType}-${row.participantEnterpriseId || row.participantName}`;

const StatusBadge = ({ status }) => {
  const map = {
    Strong: { bg: '#d1fae5', fg: '#065f46' },
    'Needs Improvement': { bg: '#fef3c7', fg: '#92400e' },
    Weak: { bg: '#fee2e2', fg: '#991b1b' },
  };
  const palette = map[status] || { bg: '#e5e7eb', fg: '#374151' };
  return (
    <span style={{ background: palette.bg, color: palette.fg, borderRadius: 999, padding: '2px 10px', fontSize: 11, fontWeight: 700 }}>
      {status}
    </span>
  );
};

const HorizontalBar = ({ label, value, color = '#4f46e5' }) => (
  <div style={{ marginBottom: 8 }}>
    <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: 12, color: 'var(--text-secondary)' }}>
      <span>{label}</span>
      <strong style={{ color: 'var(--text-primary)' }}>{formatPercent(value)}</strong>
    </div>
    <div style={{ marginTop: 4, height: 9, borderRadius: 999, background: '#e5e7eb', overflow: 'hidden' }}>
      <div style={{ width: `${clampPercent(value)}%`, height: '100%', background: color }} />
    </div>
  </div>
);

const SectionHeader = ({ title, subtitle }) => (
  <div className="participant-analytics-section-head">
    <h3>{title}</h3>
    {subtitle && <span>{subtitle}</span>}
  </div>
);

const TrendPill = ({ label, value, tone = 'neutral' }) => (
  <div className={`participant-analytics-pill participant-analytics-pill-${tone}`}>
    <span>{label}</span>
    <strong>{value}</strong>
  </div>
);

const ListCard = ({ title, children }) => (
  <div className="participant-analytics-subcard">
    <div className="participant-analytics-subcard-title">{title}</div>
    {children}
  </div>
);

const buildStatusDistributionGradient = (engagementBands, topicCount) => {
  if (!topicCount || topicCount <= 0) {
    return 'conic-gradient(#cbd5e1 0% 100%)';
  }

  const strongPct = Math.max(0, Math.min(100, (engagementBands.strong / topicCount) * 100));
  const needsPct = Math.max(0, Math.min(100 - strongPct, (engagementBands.needsImprovement / topicCount) * 100));
  const weakStart = strongPct + needsPct;

  return `conic-gradient(#10b981 0% ${strongPct}%, #f59e0b ${strongPct}% ${weakStart}%, #ef4444 ${weakStart}% 100%)`;
};

const topicSubtopicLabel = (stackName, topicName) => `${stackName || 'General'} / ${topicName || 'General'}`;

export default function ParticipantAnalyticsDashboardPage() {
  const { t } = useLanguage();
  const [participantTypeFilter, setParticipantTypeFilter] = useState('ALL');
  const [participantAnalytics, setParticipantAnalytics] = useState(null);
  const [loadingParticipantAnalytics, setLoadingParticipantAnalytics] = useState(false);

  const fetchParticipantAnalytics = async (participantType = participantTypeFilter) => {
    setLoadingParticipantAnalytics(true);
    try {
      const res = await quizAdminAPI.getParticipantAnalytics(participantType);
      setParticipantAnalytics(res.data);
    } catch (err) {
      setParticipantAnalytics(null);
      toast.error((err.response?.data?.detail || err.response?.data?.message) || t('failedToLoadParticipantAnalytics'));
    } finally {
      setLoadingParticipantAnalytics(false);
    }
  };

  useEffect(() => {
    fetchParticipantAnalytics(participantTypeFilter);
  }, [participantTypeFilter]);

  const derived = useMemo(() => {
    const rows = participantAnalytics?.rows || [];
    const topicPerformance = participantAnalytics?.topicPerformance || [];
    const questionAnalytics = participantAnalytics?.questionAnalytics || [];

    const uniqueParticipants = new Set(rows.map((row) => toParticipantKey(row))).size;
    const engagementBands = topicPerformance.reduce((acc, topic) => {
      if (topic.status === 'Strong') acc.strong += 1;
      else if (topic.status === 'Needs Improvement') acc.needsImprovement += 1;
      else acc.weak += 1;
      return acc;
    }, { strong: 0, needsImprovement: 0, weak: 0 });

    const averagePerformanceFromRows = rows.length
      ? rows.reduce((sum, row) => sum + toSafeNumber(row.performancePercent), 0) / rows.length
      : 0;

    const averageEngagementFromRows = rows.length
      ? rows.reduce((sum, row) => sum + toSafeNumber(row.engagementPercent), 0) / rows.length
      : 0;

    const questionTopicStatsMap = questionAnalytics.reduce((acc, question) => {
      const key = topicSubtopicLabel(question.stackName, question.topicName);
      const existing = acc.get(key) || {
        key,
        stackName: question.stackName || 'General',
        topicName: question.topicName || 'General',
        attemptedCount: 0,
        incorrectCount: 0,
        successRateSum: 0,
        questionCount: 0,
        hasDifficultySignal: false,
      };

      existing.attemptedCount += toSafeNumber(question.attemptedCount);
      existing.incorrectCount += toSafeNumber(question.incorrectCount);
      existing.successRateSum += toSafeNumber(question.successRatePercent);
      existing.questionCount += 1;
      existing.hasDifficultySignal = existing.hasDifficultySignal
        || question.difficultySignal === 'Too Easy'
        || question.difficultySignal === 'Too Hard';

      acc.set(key, existing);
      return acc;
    }, new Map());

    const questionTopicStats = Array.from(questionTopicStatsMap.values()).map((entry) => ({
      ...entry,
      averageSuccessRatePercent: entry.questionCount > 0 ? entry.successRateSum / entry.questionCount : 0,
    }));

    return {
      uniqueParticipants,
      engagementBands,
      averagePerformanceFromRows,
      averageEngagementFromRows,
      mostAttemptedTopicsFromQuestions: questionTopicStats
        .slice()
        .sort((a, b) => b.attemptedCount - a.attemptedCount)
        .slice(0, 5),
      mostIncorrectTopicsFromQuestions: questionTopicStats
        .slice()
        .sort((a, b) => b.incorrectCount - a.incorrectCount)
        .slice(0, 5),
      lowSuccessTopicsFromQuestions: questionTopicStats
        .slice()
        .filter((entry) => entry.averageSuccessRatePercent < 50 || entry.hasDifficultySignal)
        .sort((a, b) => a.averageSuccessRatePercent - b.averageSuccessRatePercent)
        .slice(0, 6),
      weakTopics: topicPerformance
        .slice()
        .sort((a, b) => toSafeNumber(a.averageScorePercent) - toSafeNumber(b.averageScorePercent))
        .slice(0, 4),
    };
  }, [participantAnalytics]);

  const topicCount = (participantAnalytics?.topicPerformance || []).length;
  const statusDistributionGradient = buildStatusDistributionGradient(derived.engagementBands, topicCount);

  return (
    <div className="participant-analytics-page">
      <div className="page-header">
        <div>
          <h1 className="page-title">{t('participantAnalytics')} {t('performanceAndEngagement')}</h1>
          <p className="page-subtitle">{t('participantAnalyticsDetailedInsights')}</p>
        </div>
      </div>

      <div className="card participant-analytics-shell dashboard-token-shell">
        <div className="participant-analytics-toolbar dashboard-token-toolbar">
          <span>{t('analyticsDashboard')}</span>
          <div className="participant-analytics-filter-group" role="group" aria-label={t('participantType')}>
            <button
              type="button"
              className={`btn btn-sm ${participantTypeFilter === 'ALL' ? 'btn-primary' : 'btn-secondary'}`}
              onClick={() => setParticipantTypeFilter('ALL')}
              aria-pressed={participantTypeFilter === 'ALL'}
            >
              {t('allParticipants')}
            </button>
            <button
              type="button"
              className={`btn btn-sm ${participantTypeFilter === 'EMPLOYEE' ? 'btn-primary' : 'btn-secondary'}`}
              onClick={() => setParticipantTypeFilter('EMPLOYEE')}
              aria-pressed={participantTypeFilter === 'EMPLOYEE'}
            >
              {t('employeeParticipants')}
            </button>
            <button
              type="button"
              className={`btn btn-sm ${participantTypeFilter === 'GUEST' ? 'btn-primary' : 'btn-secondary'}`}
              onClick={() => setParticipantTypeFilter('GUEST')}
              aria-pressed={participantTypeFilter === 'GUEST'}
            >
              {t('guestParticipants')}
            </button>
          </div>
        </div>

        {loadingParticipantAnalytics ? (
          <div className="loading-container"><div className="spinner spinner-dark" /></div>
        ) : !participantAnalytics?.rows?.length ? (
          <div className="empty-state">
            <div className="empty-icon">📊</div>
            <h3>{t('noAnalyticsData')}</h3>
            <p>{t('noParticipantAttemptsFoundYetPrefix')} {participantTypeFilter.toLowerCase()} {t('noParticipantAttemptsFoundYetSuffix')}</p>
          </div>
        ) : (
          <>
            <div className="participant-analytics-metadata-row">
              <span>{t('hostedQuizzesConsidered')}: <strong>{participantAnalytics.totalHostedQuizzes}</strong></span>
              <span>{t('completionRate')}: <strong>{formatPercent(participantAnalytics.quizAnalytics?.completionRatePercent ?? 0)}</strong></span>
            </div>

            <div className="participant-analytics-summary-grid">
              <div className="participant-analytics-summary-card participant-analytics-summary-card-blue">
                <span>{t('totalParticipants')}</span>
                <strong>{participantAnalytics.quizAnalytics?.totalParticipants ?? derived.uniqueParticipants}</strong>
              </div>
              <div className="participant-analytics-summary-card participant-analytics-summary-card-amber">
                <span>{t('averagePerformance')}</span>
                <strong>{formatPercent(participantAnalytics.quizAnalytics?.averageScorePercent ?? derived.averagePerformanceFromRows)}</strong>
              </div>
              <div className="participant-analytics-summary-card participant-analytics-summary-card-green">
                <span>{t('averageEngagement')}</span>
                <strong>{formatPercent(derived.averageEngagementFromRows)}</strong>
              </div>
              <div className="participant-analytics-summary-card participant-analytics-summary-card-indigo">
                <span>{t('totalQuizzesConducted')}</span>
                <strong>{participantAnalytics.quizAnalytics?.totalQuizzesConducted ?? participantAnalytics.totalHostedQuizzes}</strong>
              </div>
            </div>

            <div className="participant-analytics-pills">
              <TrendPill label={t('highestScore')} value={formatPercent(participantAnalytics.quizAnalytics?.highestScorePercent ?? 0)} tone="good" />
              <TrendPill label={t('lowestScore')} value={formatPercent(participantAnalytics.quizAnalytics?.lowestScorePercent ?? 0)} tone="warn" />
              <TrendPill label={t('activeUsers')} value={participantAnalytics.userEngagement?.activeUsers ?? derived.uniqueParticipants} tone="neutral" />
              <TrendPill label="Gap Alerts" value={participantAnalytics.knowledgeGaps?.length || 0} tone="risk" />
            </div>

            <div className="participant-analytics-section dashboard-token-section">
              <SectionHeader title={t('quizAnalytics')} subtitle={t('quizAnalyticsSubtitle')} />
              <div className="participant-analytics-mini-grid">
                <div className="participant-analytics-mini-card">
                  <span>{t('totalQuizzesConducted')}</span>
                  <strong>{participantAnalytics.quizAnalytics?.totalQuizzesConducted ?? participantAnalytics.totalHostedQuizzes}</strong>
                </div>
                <div className="participant-analytics-mini-card">
                  <span>{t('totalParticipants')}</span>
                  <strong>{participantAnalytics.quizAnalytics?.totalParticipants ?? derived.uniqueParticipants}</strong>
                </div>
                <div className="participant-analytics-mini-card">
                  <span>{t('completionRate')}</span>
                  <strong>{formatPercent(participantAnalytics.quizAnalytics?.completionRatePercent ?? 0)}</strong>
                </div>
              </div>
              <div className="participant-analytics-mini-grid">
                <div className="participant-analytics-mini-card">
                  <span>{t('averageScore')}</span>
                  <strong>{formatPercent(participantAnalytics.quizAnalytics?.averageScorePercent ?? derived.averagePerformanceFromRows)}</strong>
                </div>
                <div className="participant-analytics-mini-card">
                  <span>{t('highestScore')}</span>
                  <strong>{formatPercent(participantAnalytics.quizAnalytics?.highestScorePercent ?? 0)}</strong>
                </div>
                <div className="participant-analytics-mini-card">
                  <span>{t('lowestScore')}</span>
                  <strong>{formatPercent(participantAnalytics.quizAnalytics?.lowestScorePercent ?? 0)}</strong>
                </div>
              </div>
            </div>

            <div className="participant-analytics-section dashboard-token-section">
              <SectionHeader title={t('topicPerformance')} subtitle={t('topicPerformanceSubtitle')} />
              <div className="grid-2">
                <ListCard title={t('topicPerformanceBarChart')}>
                  {(participantAnalytics.topicPerformance || []).slice(0, 8).map((topic) => (
                    <div key={`${topic.stackName}-${topic.topicName}`}>
                      <HorizontalBar
                        label={`${topic.stackName} / ${topic.topicName}`}
                        value={topic.averageScorePercent}
                        color={topic.averageScorePercent < 55 ? '#dc2626' : topic.averageScorePercent < 75 ? '#f59e0b' : '#10b981'}
                      />
                      <div style={{ marginTop: -4, marginBottom: 8 }}>
                        <StatusBadge status={topic.status} />
                      </div>
                    </div>
                  ))}
                </ListCard>
                <ListCard title={t('statusDistributionPie')}>
                  <div className="participant-analytics-pie-wrap">
                    <div
                      className="participant-analytics-pie"
                      style={{ background: statusDistributionGradient }}
                    />
                    <div className="participant-analytics-legend">
                      <div><span className="participant-analytics-legend-dot legend-strong" /> {t('strong')}: {derived.engagementBands.strong}</div>
                      <div><span className="participant-analytics-legend-dot legend-mid" /> {t('needsImprovement')}: {derived.engagementBands.needsImprovement}</div>
                      <div><span className="participant-analytics-legend-dot legend-weak" /> {t('weak')}: {derived.engagementBands.weak}</div>
                    </div>
                  </div>
                  {!!derived.weakTopics.length && (
                    <div className="participant-analytics-watchlist">
                      {derived.weakTopics.map((topic) => (
                        <div key={`watch-${topic.topicId}-${topic.topicName}`} className="participant-analytics-watchlist-row">
                          <span>{topic.stackName} / {topic.topicName}</span>
                          <strong>{formatPercent(topic.averageScorePercent)}</strong>
                        </div>
                      ))}
                    </div>
                  )}
                </ListCard>
              </div>
            </div>

            <div className="participant-analytics-section dashboard-token-section">
              <SectionHeader title={t('questionAnalytics')} subtitle={t('questionAnalyticsSubtitle')} />
              <div className="grid-3">
                <ListCard title={t('mostAttemptedQuestions')}>
                  {derived.mostAttemptedTopicsFromQuestions.length ? derived.mostAttemptedTopicsFromQuestions.map((entry) => (
                    <div key={`attempt-topic-${entry.key}`} className="participant-analytics-list-row">
                      <span>{topicSubtopicLabel(entry.stackName, entry.topicName)}</span>
                      <strong>{entry.attemptedCount} {t('attempts')}</strong>
                    </div>
                  )) : <div style={{ fontSize: 12, color: 'var(--text-muted)' }}>{t('noData')}</div>}
                </ListCard>
                <ListCard title={t('mostIncorrectQuestions')}>
                  {derived.mostIncorrectTopicsFromQuestions.length ? derived.mostIncorrectTopicsFromQuestions.map((entry) => (
                    <div key={`incorrect-topic-${entry.key}`} className="participant-analytics-list-row">
                      <span>{topicSubtopicLabel(entry.stackName, entry.topicName)}</span>
                      <strong>{entry.incorrectCount} {t('incorrect')}</strong>
                    </div>
                  )) : <div style={{ fontSize: 12, color: 'var(--text-muted)' }}>{t('noData')}</div>}
                </ListCard>
                <ListCard title={t('lowSuccessDifficultySignal')}>
                  {derived.lowSuccessTopicsFromQuestions.map((entry) => (
                    <div key={`signal-topic-${entry.key}`} className="participant-analytics-list-row">
                      <span>{topicSubtopicLabel(entry.stackName, entry.topicName)}</span>
                      <strong>{formatPercent(entry.averageSuccessRatePercent)}</strong>
                    </div>
                  ))}
                  {!derived.lowSuccessTopicsFromQuestions.length && <div style={{ fontSize: 12, color: 'var(--text-muted)' }}>{t('noData')}</div>}
                </ListCard>
              </div>
            </div>

            <div className="participant-analytics-section dashboard-token-section">
              <SectionHeader title={t('userEngagement')} subtitle={t('userEngagementSubtitle')} />
              <div className="grid-3">
                <div className="participant-analytics-mini-card participant-analytics-mini-card-accent">
                  <span>{t('activeUsers')}</span>
                  <strong>{participantAnalytics.userEngagement?.activeUsers ?? derived.uniqueParticipants}</strong>
                </div>
                <ListCard title={t('topTopicsUsed')}>
                  {(participantAnalytics.userEngagement?.topTopics || []).slice(0, 5).map((topic) => (
                    <div key={`topic-${topic.stackName}-${topic.topicName}`} className="participant-analytics-list-row">
                      <span>{topic.stackName} / {topic.topicName}</span>
                      <strong>{topic.attemptsCount}</strong>
                    </div>
                  ))}
                </ListCard>
                <ListCard title={t('highParticipationQuizzes')}>
                  {(participantAnalytics.userEngagement?.topQuizzes || []).slice(0, 5).map((quiz) => (
                    <div key={`quiz-${quiz.quizId}`} className="participant-analytics-list-row">
                      <span>{quiz.title}</span>
                      <strong>{quiz.participantsCount} {t('participants')}</strong>
                    </div>
                  ))}
                </ListCard>
              </div>
            </div>

            <div className="participant-analytics-section dashboard-token-section">
              <SectionHeader title={t('knowledgeGapDetection')} subtitle={t('knowledgeGapDetectionSubtitle')} />
              {!participantAnalytics.knowledgeGaps?.length ? (
                <div className="alert alert-success">{t('noMajorKnowledgeGaps')}</div>
              ) : (
                <div className="participant-analytics-gaps">
                  {participantAnalytics.knowledgeGaps.map((gap) => (
                    <div
                      key={`gap-${gap.stackName}-${gap.topicName}`}
                      className="participant-analytics-gap-item"
                    >
                      <div>
                        <div className="participant-analytics-gap-title">{gap.stackName} / {gap.topicName}</div>
                        <div className="participant-analytics-gap-meta">
                          {t('avgScore')}: {formatPercent(gap.averageScorePercent)} | {t('learnersBelow50')}: {gap.learnersBelow50Percent} | {t('attempts')}: {gap.attemptsCount}
                        </div>
                        <div className="participant-analytics-gap-message">{gap.message}</div>
                      </div>
                    </div>
                  ))}
                </div>
              )}
            </div>

            <div className="participant-analytics-section participant-analytics-matrix-section dashboard-token-section">
              <SectionHeader title={t('participantTopicMatrix')} subtitle={t('participantTopicMatrixSubtitle')} />
              <div className="table-wrapper mobile-cards">
                <table>
                  <thead>
                    <tr>
                      <th>{t('participant')}</th>
                      <th>{t('type')}</th>
                      <th>{t('topic')}</th>
                      <th>{t('subtopic')}</th>
                      <th>{t('hosted')}</th>
                      <th>{t('attended')}</th>
                      <th>{t('engagement')}</th>
                      <th>{t('performance')}</th>
                      <th>{t('suggestion')}</th>
                    </tr>
                  </thead>
                  <tbody>
                    {participantAnalytics.rows.map((row) => (
                      <tr key={`${row.participantType}-${row.participantEnterpriseId || row.participantName}-${row.topicName}`}>
                        <td data-label={t('participant')}>
                          {row.participantName}
                          <div style={{ fontSize: 11, color: 'var(--text-muted)' }}>{row.participantEnterpriseId || '-'}</div>
                        </td>
                        <td data-label={t('type')}>{row.participantType}</td>
                        <td data-label={t('topic')}>{row.stackName}</td>
                        <td data-label={t('subtopic')}>{row.topicName}</td>
                        <td data-label={t('hosted')}>{row.hostedQuizCount}</td>
                        <td data-label={t('attended')}>{row.attendedQuizCount}</td>
                        <td data-label={t('engagement')}>
                          <span className="participant-analytics-chip participant-analytics-chip-soft dashboard-token-chip">{formatPercent(row.engagementPercent)}</span>
                        </td>
                        <td data-label={t('performance')}>
                          {row.correctAnswers}/{row.totalQuestions}
                          <div style={{ fontSize: 11, color: 'var(--text-muted)' }}>{formatPercent(row.performancePercent)}</div>
                        </td>
                        <td data-label={t('suggestion')}>{row.recommendation}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </div>

          </>
        )}
      </div>
    </div>
  );
}
