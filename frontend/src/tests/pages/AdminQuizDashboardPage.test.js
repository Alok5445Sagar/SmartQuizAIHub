import React from 'react';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import AdminQuizDashboardPage from '../../pages/AdminQuizDashboardPage';
import { quizAdminAPI, quizPublicAPI, referenceAPI } from '../../services/api';

jest.mock('../../services/api', () => ({
  quizAdminAPI: {
    getApprovedQuestionsByTopic: jest.fn(),
    getApprovedQuestionsByFilters: jest.fn(),
    autoPickQuestions: jest.fn(),
    publishQuiz: jest.fn(),
    getQuizzes: jest.fn(),
    closeQuiz: jest.fn(),
    reopenQuiz: jest.fn(),
    rehostQuiz: jest.fn(),
    getParticipantAnalytics: jest.fn(),
    getHostedTopicsForLeaderboard: jest.fn(),
    getTopicLeaderboard: jest.fn(),
    getOverallLeaderboard: jest.fn(),
    downloadLeaderboardExcel: jest.fn(),
  },
  quizPublicAPI: {
    getLeaderboard: jest.fn(),
  },
  referenceAPI: {
    getStacks: jest.fn(),
    getTopics: jest.fn(),
  },
}));

jest.mock('react-hot-toast', () => ({
  __esModule: true,
  default: { success: jest.fn(), error: jest.fn() },
}));

describe('AdminQuizDashboardPage auto-pick behavior', () => {
  beforeEach(() => {
    jest.clearAllMocks();

    referenceAPI.getStacks.mockResolvedValue({
      data: [{ stackId: 1006, stackName: 'Core Java' }],
    });
    referenceAPI.getTopics.mockResolvedValue({
      data: [{ topicId: 1025, topicName: 'Introduction to Core Java' }],
    });
    quizAdminAPI.getQuizzes.mockResolvedValue({
      data: {
        content: [
          {
            quizId: 200,
            title: 'Employee Quiz',
            quizCode: 'QUIZ200',
            stackName: 'Core Java',
            topicName: 'Introduction to Core Java',
            topicId: 1025,
            status: 'PUBLISHED',
            questionCount: 15,
            durationMinutes: 15,
            quizLink: 'http://localhost:3000/quiz/QUIZ200',
          },
        ],
        totalElements: 1,
        totalPages: 1,
      },
    });
    quizPublicAPI.getLeaderboard.mockResolvedValue({
      data: { leaderboard: [] },
    });
    quizAdminAPI.getHostedTopicsForLeaderboard.mockResolvedValue({
      data: [
        {
          topicId: 1025,
          stackName: 'Core Java',
          topicName: 'Introduction to Core Java',
          quizzesHosted: 2,
        },
      ],
    });
    quizAdminAPI.getTopicLeaderboard.mockResolvedValue({
      data: {
        leaderboard: [
          {
            rank: 1,
            rankBadge: 'GOLD',
            participantName: 'Emp User',
            participantEnterpriseId: 'EMP001',
            quizzesPlayed: 1,
            attemptedCount: 12,
            correctCount: 8,
            wrongCount: 4,
            scorePercent: 66.67,
            averageTimeTakenSeconds: 120,
          },
        ],
      },
    });
    quizAdminAPI.getOverallLeaderboard.mockResolvedValue({ data: { leaderboard: [] } });

    quizAdminAPI.autoPickQuestions.mockResolvedValue({
      data: {
        questionIds: [27],
        selectedCount: 1,
        unusedSelectedCount: 1,
        reusedSelectedCount: 0,
      },
    });

    quizAdminAPI.getApprovedQuestionsByFilters.mockResolvedValue({
      data: [
        {
          questionId: 27,
          questionStem: 'Sample MCQ',
          questionType: 'MCQ',
          difficulty: 'Easy',
          optionA: 'A',
          optionB: 'B',
          optionC: 'C',
          optionD: 'D',
        },
      ],
    });
  });

  test('auto-pick works without preloading approved questions', async () => {
    render(<AdminQuizDashboardPage />);

    await waitFor(() => {
      expect(screen.getByLabelText('Core Java')).toBeInTheDocument();
    });

    fireEvent.click(screen.getByLabelText('Core Java'));

    await waitFor(() => {
      expect(screen.getByLabelText('Introduction to Core Java')).toBeInTheDocument();
    });

    fireEvent.click(screen.getByLabelText('Introduction to Core Java'));

    const autoPickButton = screen.getByRole('button', { name: /auto pick/i });
    await waitFor(() => {
      expect(autoPickButton).not.toBeDisabled();
    });

    fireEvent.click(autoPickButton);

    await waitFor(() => {
      expect(quizAdminAPI.autoPickQuestions).toHaveBeenCalledTimes(1);
      expect(quizAdminAPI.autoPickQuestions).toHaveBeenCalledWith(
        expect.objectContaining({ topicIds: [1025], stackIds: [1006] })
      );
    });

    await waitFor(() => {
      expect(quizAdminAPI.getApprovedQuestionsByFilters).toHaveBeenCalledWith({ topicIds: [1025], stackIds: [1006] });
      expect(screen.getByText('Selected: 1/15 | MCQ: 1/10 | MSQ: 0/5')).toBeInTheDocument();
    });
  });

  test('loads topic-wise leaderboard from live quiz dashboard', async () => {
    render(<AdminQuizDashboardPage />);

    await waitFor(() => {
      expect(screen.getByRole('button', { name: /^topic-wise$/i })).toBeInTheDocument();
    });

    fireEvent.click(screen.getByRole('button', { name: /^topic-wise$/i }));
    fireEvent.change(screen.getByRole('combobox'), { target: { value: '1025' } });

    await waitFor(() => {
      expect(quizAdminAPI.getTopicLeaderboard).toHaveBeenCalledWith(1025, 'ALL');
    });

    expect(await screen.findByText('🥇')).toBeInTheDocument();
  });

  test('filters session leaderboard by participant type', async () => {
    quizPublicAPI.getLeaderboard.mockResolvedValueOnce({
      data: {
        leaderboard: [
          {
            rank: 1,
            rankBadge: 'GOLD',
            participantName: 'Emp User',
            participantEnterpriseId: 'EMP001',
            score: 9,
            totalQuestions: 10,
            attemptedCount: 10,
            correctCount: 9,
            wrongCount: 1,
            timeTakenSeconds: 90,
          },
          {
            rank: 2,
            rankBadge: 'SILVER',
            participantName: 'Guest User',
            participantEnterpriseId: null,
            score: 7,
            totalQuestions: 10,
            attemptedCount: 10,
            correctCount: 7,
            wrongCount: 3,
            timeTakenSeconds: 120,
          },
        ],
      },
    });

    render(<AdminQuizDashboardPage />);

    const viewLeaderboardBtn = await screen.findByRole('button', { name: /view leaderboard/i });
    fireEvent.click(viewLeaderboardBtn);

    expect(await screen.findByText('Emp User')).toBeInTheDocument();
    expect(screen.getByText('Guest User')).toBeInTheDocument();

    fireEvent.click(screen.getByRole('button', { name: /^employee$/i }));
    expect(screen.getByText('Emp User')).toBeInTheDocument();
    expect(screen.queryByText('Guest User')).not.toBeInTheDocument();

    fireEvent.click(screen.getByRole('button', { name: /^guest$/i }));
    expect(screen.queryByText('Emp User')).not.toBeInTheDocument();
    expect(screen.getByText('Guest User')).toBeInTheDocument();
  });
});
