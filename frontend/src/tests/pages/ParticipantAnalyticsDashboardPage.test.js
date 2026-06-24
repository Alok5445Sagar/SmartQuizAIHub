import React from 'react';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import ParticipantAnalyticsDashboardPage from '../../pages/ParticipantAnalyticsDashboardPage';
import { quizAdminAPI } from '../../services/api';

jest.mock('../../services/api', () => ({
  quizAdminAPI: {
    getParticipantAnalytics: jest.fn(),
  },
}));

jest.mock('react-hot-toast', () => ({
  __esModule: true,
  default: { success: jest.fn(), error: jest.fn() },
}));

describe('ParticipantAnalyticsDashboardPage', () => {
  beforeEach(() => {
    jest.clearAllMocks();

    quizAdminAPI.getParticipantAnalytics.mockImplementation((type) => {
      if (type === 'ALL') {
        return Promise.resolve({
          data: {
            participantType: 'ALL',
            totalHostedQuizzes: 3,
            totalRows: 2,
            quizAnalytics: {
              totalQuizzesConducted: 3,
              totalParticipants: 2,
              averageScorePercent: 53.34,
              highestScorePercent: 66.67,
              lowestScorePercent: 40,
              completionRatePercent: 100,
            },
            topicPerformance: [
              {
                topicId: 1025,
                stackName: 'Core Java',
                topicName: 'Introduction to Core Java',
                averageScorePercent: 53.34,
                status: 'Needs Improvement',
                attemptCount: 2,
              },
            ],
            questionAnalytics: [],
            userEngagement: {
              activeUsers: 2,
              activeParticipants: [],
              topTopics: [],
              topQuizzes: [],
            },
            knowledgeGaps: [],
            rows: [
              {
                participantName: 'Emp User',
                participantEnterpriseId: 'EMP001',
                participantType: 'EMPLOYEE',
                stackName: 'Core Java',
                topicName: 'Introduction to Core Java',
                hostedQuizCount: 3,
                attendedQuizCount: 2,
                correctAnswers: 8,
                totalQuestions: 12,
                engagementPercent: 66.67,
                performancePercent: 66.67,
                recommendation: 'Good progress. Continue practicing Introduction to Core Java.',
              },
              {
                participantName: 'Guest User',
                participantEnterpriseId: null,
                participantType: 'GUEST',
                stackName: 'Core Java',
                topicName: 'Introduction to Core Java',
                hostedQuizCount: 3,
                attendedQuizCount: 1,
                correctAnswers: 2,
                totalQuestions: 5,
                engagementPercent: 33.33,
                performancePercent: 40,
                recommendation: 'Revise Introduction to Core Java (Core Java) to improve correctness.',
              },
            ],
          },
        });
      }

      if (type === 'GUEST') {
        return Promise.resolve({
          data: {
            participantType: 'GUEST',
            totalHostedQuizzes: 3,
            totalRows: 1,
            quizAnalytics: {
              totalQuizzesConducted: 3,
              totalParticipants: 1,
              averageScorePercent: 40,
              highestScorePercent: 40,
              lowestScorePercent: 40,
              completionRatePercent: 100,
            },
            topicPerformance: [
              {
                topicId: 1025,
                stackName: 'Core Java',
                topicName: 'Introduction to Core Java',
                averageScorePercent: 40,
                status: 'Weak',
                attemptCount: 1,
              },
            ],
            questionAnalytics: [
              {
                questionId: 11,
                questionStem: 'What is JVM?',
                stackName: 'Core Java',
                topicName: 'Introduction to Core Java',
                attemptedCount: 1,
                incorrectCount: 1,
                successRatePercent: 0,
                difficultySignal: 'Too Hard',
              },
            ],
            userEngagement: {
              activeUsers: 1,
              activeParticipants: [],
              topTopics: [
                {
                  stackName: 'Core Java',
                  topicName: 'Introduction to Core Java',
                  attemptsCount: 1,
                  distinctParticipants: 1,
                },
              ],
              topQuizzes: [
                {
                  quizId: 101,
                  quizCode: 'QUIZ101',
                  title: 'Guest Quiz',
                  stackName: 'Core Java',
                  topicName: 'Introduction to Core Java',
                  participantsCount: 1,
                  attemptsCount: 1,
                },
              ],
            },
            knowledgeGaps: [
              {
                stackName: 'Core Java',
                topicName: 'Introduction to Core Java',
                averageScorePercent: 40,
                attemptsCount: 1,
                learnersBelow50Percent: 1,
                message: 'Most learners are scoring below 50% in Introduction to Core Java (Core Java). This is a major knowledge gap.',
              },
            ],
            rows: [
              {
                participantName: 'Guest User',
                participantEnterpriseId: null,
                participantType: 'GUEST',
                stackName: 'Core Java',
                topicName: 'Introduction to Core Java',
                hostedQuizCount: 3,
                attendedQuizCount: 1,
                correctAnswers: 2,
                totalQuestions: 5,
                engagementPercent: 33.33,
                performancePercent: 40,
                recommendation: 'Revise Introduction to Core Java (Core Java) to improve correctness.',
              },
            ],
          },
        });
      }

      return Promise.resolve({
        data: {
          participantType: 'EMPLOYEE',
          totalHostedQuizzes: 3,
          totalRows: 1,
          quizAnalytics: {
            totalQuizzesConducted: 3,
            totalParticipants: 1,
            averageScorePercent: 66.67,
            highestScorePercent: 66.67,
            lowestScorePercent: 66.67,
            completionRatePercent: 100,
          },
          topicPerformance: [
            {
              topicId: 1025,
              stackName: 'Core Java',
              topicName: 'Introduction to Core Java',
              averageScorePercent: 66.67,
              status: 'Needs Improvement',
              attemptCount: 1,
            },
          ],
          questionAnalytics: [
            {
              questionId: 12,
              questionStem: 'What is Spring?',
              stackName: 'Core Java',
              topicName: 'Introduction to Core Java',
              attemptedCount: 1,
              incorrectCount: 0,
              successRatePercent: 100,
              difficultySignal: 'Too Easy',
            },
          ],
          userEngagement: {
            activeUsers: 1,
            activeParticipants: [],
            topTopics: [
              {
                stackName: 'Core Java',
                topicName: 'Introduction to Core Java',
                attemptsCount: 1,
                distinctParticipants: 1,
              },
            ],
            topQuizzes: [
              {
                quizId: 102,
                quizCode: 'QUIZ102',
                title: 'Employee Quiz',
                stackName: 'Core Java',
                topicName: 'Introduction to Core Java',
                participantsCount: 1,
                attemptsCount: 1,
              },
            ],
          },
          knowledgeGaps: [],
          rows: [
            {
              participantName: 'Emp User',
              participantEnterpriseId: 'EMP001',
              participantType: 'EMPLOYEE',
              stackName: 'Core Java',
              topicName: 'Introduction to Core Java',
              hostedQuizCount: 3,
              attendedQuizCount: 2,
              correctAnswers: 8,
              totalQuestions: 12,
              engagementPercent: 66.67,
              performancePercent: 66.67,
              recommendation: 'Good progress. Continue practicing Introduction to Core Java.',
            },
          ],
        },
      });
    });
  });

  test('loads employee analytics by default', async () => {
    render(<ParticipantAnalyticsDashboardPage />);

    await waitFor(() => {
      expect(quizAdminAPI.getParticipantAnalytics).toHaveBeenCalledWith('ALL');
    });

    expect(screen.getByText('Emp User')).toBeInTheDocument();
    expect(screen.getByText('8/12')).toBeInTheDocument();
    expect(screen.getByText('Good progress. Continue practicing Introduction to Core Java.')).toBeInTheDocument();
    expect(screen.getByText('Quiz Analytics')).toBeInTheDocument();
    expect(screen.getByText('Topic Performance')).toBeInTheDocument();
    expect(screen.getByText('Question Analytics')).toBeInTheDocument();
    expect(screen.getByText('User Engagement')).toBeInTheDocument();
    expect(screen.getByText('Knowledge Gap Detection')).toBeInTheDocument();
  });

  test('switches to guest analytics when guest filter selected', async () => {
    render(<ParticipantAnalyticsDashboardPage />);

    await waitFor(() => {
      expect(quizAdminAPI.getParticipantAnalytics).toHaveBeenCalledWith('ALL');
    });

    fireEvent.click(screen.getByRole('button', { name: /guest participants/i }));

    await waitFor(() => {
      expect(quizAdminAPI.getParticipantAnalytics).toHaveBeenCalledWith('GUEST');
    });

    expect(screen.getAllByText('Guest User').length).toBeGreaterThan(0);
  });

  test('switches to all participants analytics', async () => {
    render(<ParticipantAnalyticsDashboardPage />);

    await waitFor(() => {
      expect(quizAdminAPI.getParticipantAnalytics).toHaveBeenCalledWith('ALL');
    });

    expect(screen.getAllByText('Emp User').length).toBeGreaterThan(0);
    expect(screen.getAllByText('Guest User').length).toBeGreaterThan(0);
  });

});
