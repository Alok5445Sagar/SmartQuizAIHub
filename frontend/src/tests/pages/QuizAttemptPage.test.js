import React from 'react';
import { act, fireEvent, render, screen, waitFor } from '@testing-library/react';
import QuizAttemptPage from '../../pages/QuizAttemptPage';
import { quizPublicAPI } from '../../services/api';

const mockNavigate = jest.fn();

jest.mock('react-router-dom', () => ({
  ...jest.requireActual('react-router-dom'),
  useParams: () => ({ quizCode: 'QUIZ123' }),
  useNavigate: () => mockNavigate,
  useLocation: () => ({ state: null }),
}));

jest.mock('../../services/api', () => ({
  quizPublicAPI: {
    getQuiz: jest.fn(),
    getLeaderboard: jest.fn(),
    participantLogin: jest.fn(),
    submitAttempt: jest.fn(),
  },
}));

jest.mock('react-hot-toast', () => ({
  __esModule: true,
  default: { success: jest.fn(), error: jest.fn() },
  Toaster: () => null,
}));

const activeQuizPayload = {
  quizId: 1,
  quizCode: 'QUIZ123',
  title: 'Live Quiz Test',
  status: 'PUBLISHED',
  canAttempt: true,
  questionCount: 2,
  durationMinutes: 15,
  questions: [
    {
      questionId: 11,
      questionStem: 'MCQ Question',
      questionType: 'MCQ',
      optionA: 'Option A',
      optionB: 'Option B',
      optionC: 'Option C',
      optionD: 'Option D',
    },
    {
      questionId: 12,
      questionStem: 'MSQ Question',
      questionType: 'MSQ',
      msqRequiredSelections: 2,
      optionA: 'Option A',
      optionB: 'Option B',
      optionC: 'Option C',
      optionD: 'Option D',
    },
  ],
};

const getScopedParticipantSession = () => {
  const scopedKey = Object.keys(sessionStorage).find((key) => key.startsWith('participant-portal-session:'));
  return scopedKey ? sessionStorage.getItem(scopedKey) : null;
};

describe('QuizAttemptPage new live quiz flow', () => {
  beforeEach(() => {
    jest.clearAllMocks();
    localStorage.clear();
    sessionStorage.clear();
    mockNavigate.mockClear();
    window.confirm = jest.fn();
    window.alert = jest.fn();

    quizPublicAPI.getQuiz.mockResolvedValue({ data: activeQuizPayload });
    quizPublicAPI.getLeaderboard.mockResolvedValue({
      data: {
        quizCode: 'QUIZ123',
        leaderboard: [
          {
            rank: 1,
            participantName: 'Existing User',
            participantEnterpriseId: 'EMP001',
            score: 2,
            totalQuestions: 2,
            submittedAt: '2026-06-20T06:00:00Z',
          },
        ],
      },
    });
  });

  test('moves from login step to question step for guest', async () => {
    render(<QuizAttemptPage />);

    await waitFor(() => {
      expect(screen.getByText('Choose Login Method')).toBeInTheDocument();
    });

    fireEvent.change(screen.getByPlaceholderText('Enter your full name'), {
      target: { value: 'Guest User' },
    });
    fireEvent.click(screen.getByRole('button', { name: /continue to questions/i }));

    await waitFor(() => {
      expect(screen.getByText('Attempt Questions')).toBeInTheDocument();
    });
  });

  test('filters visible questions by MCQ/MSQ type', async () => {
    render(<QuizAttemptPage />);

    await waitFor(() => {
      expect(screen.getByText('Choose Login Method')).toBeInTheDocument();
    });

    fireEvent.change(screen.getByPlaceholderText('Enter your full name'), {
      target: { value: 'Guest User' },
    });
    fireEvent.click(screen.getByRole('button', { name: /continue to questions/i }));

    await waitFor(() => {
      expect(screen.getByText(/MCQ Question/i)).toBeInTheDocument();
      expect(screen.getByText(/MSQ Question/i)).toBeInTheDocument();
    });

    fireEvent.click(screen.getByRole('button', { name: 'MCQ' }));

    await waitFor(() => {
      expect(screen.getByText(/MCQ Question/i)).toBeInTheDocument();
      expect(screen.queryByText(/MSQ Question/i)).not.toBeInTheDocument();
    });

    fireEvent.click(screen.getByRole('button', { name: 'MSQ' }));

    await waitFor(() => {
      expect(screen.getByText(/MSQ Question/i)).toBeInTheDocument();
      expect(screen.queryByText(/MCQ Question/i)).not.toBeInTheDocument();
    });
  });

  test('hides leaderboard before submit and reveals it only after clicking View Leaderboard', async () => {
    quizPublicAPI.submitAttempt.mockResolvedValue({
      data: {
        participantName: 'Guest User',
        score: 2,
        totalQuestions: 2,
      },
    });
    quizPublicAPI.getLeaderboard
      .mockResolvedValueOnce({
        data: {
          quizCode: 'QUIZ123',
          leaderboard: [
            {
              rank: 1,
              participantName: 'Existing User',
              participantEnterpriseId: 'EMP001',
              score: 2,
              totalQuestions: 2,
              submittedAt: '2026-06-20T06:00:00Z',
            },
          ],
        },
      })
      .mockResolvedValueOnce({
        data: {
          quizCode: 'QUIZ123',
          leaderboard: [
            {
              rank: 1,
              participantName: 'Guest User',
              participantEnterpriseId: null,
              score: 2,
              totalQuestions: 2,
              submittedAt: '2026-06-20T06:10:00Z',
            },
          ],
        },
      });

    window.confirm.mockReturnValue(true);

    render(<QuizAttemptPage />);

    await waitFor(() => {
      expect(screen.getByText('Choose Login Method')).toBeInTheDocument();
    });

    // Leaderboard should stay hidden before submission for active quiz.
    expect(screen.queryByText('Leaderboard')).not.toBeInTheDocument();

    fireEvent.change(screen.getByPlaceholderText('Enter your full name'), {
      target: { value: 'Guest User' },
    });
    fireEvent.click(screen.getByRole('button', { name: /continue to questions/i }));

    await waitFor(() => {
      expect(screen.getByText('Attempt Questions')).toBeInTheDocument();
    });

    // Answer MCQ question.
    fireEvent.click(screen.getAllByLabelText('A. Option A')[0]);
    // Answer MSQ question (select at least one option for attempted state).
    fireEvent.click(screen.getAllByLabelText('A. Option A')[1]);

    fireEvent.click(screen.getByRole('button', { name: /submit quiz/i }));

    await waitFor(() => {
      expect(window.confirm).toHaveBeenCalledWith('Are you sure to submit?');
      expect(quizPublicAPI.submitAttempt).toHaveBeenCalledTimes(1);
    });

    await waitFor(() => {
      expect(screen.getByText('Submission Complete')).toBeInTheDocument();
      expect(screen.getByRole('button', { name: /(view\s+)?leaderboard/i })).toBeInTheDocument();
    });

    expect(screen.queryByText('Leaderboard')).not.toBeInTheDocument();

    fireEvent.click(screen.getByRole('button', { name: /(view\s+)?leaderboard/i }));

    await waitFor(() => {
      expect(screen.getByText('Leaderboard')).toBeInTheDocument();
      expect(screen.getByText('Guest User')).toBeInTheDocument();
      expect(screen.getByText('🥇')).toBeInTheDocument();
    });
  });

  test('shows Go to Home after submit and navigates to participant portal', async () => {
    quizPublicAPI.submitAttempt.mockResolvedValue({
      data: {
        participantName: 'Guest User',
        score: 2,
        totalQuestions: 2,
      },
    });
    window.confirm.mockReturnValue(true);

    render(<QuizAttemptPage />);

    await waitFor(() => {
      expect(screen.getByText('Choose Login Method')).toBeInTheDocument();
    });

    fireEvent.change(screen.getByPlaceholderText('Enter your full name'), {
      target: { value: 'Guest User' },
    });
    fireEvent.click(screen.getByRole('button', { name: /continue to questions/i }));

    await waitFor(() => {
      expect(screen.getByText('Attempt Questions')).toBeInTheDocument();
    });

    fireEvent.click(screen.getAllByLabelText('A. Option A')[0]);
    fireEvent.click(screen.getAllByLabelText('A. Option A')[1]);
    fireEvent.click(screen.getByRole('button', { name: /submit quiz/i }));

    await waitFor(() => {
      expect(screen.getByText('Submission Complete')).toBeInTheDocument();
      expect(screen.getByRole('button', { name: /go to home/i })).toBeInTheDocument();
      expect(screen.getByRole('button', { name: /(view\s+)?leaderboard/i })).toBeInTheDocument();
    });

    fireEvent.click(screen.getByRole('button', { name: /go to home/i }));

    expect(mockNavigate).toHaveBeenCalledWith('/participant');
    expect(getScopedParticipantSession()).toContain('Guest User');
    expect(getScopedParticipantSession()).toContain('GUEST');
  });

  test('auto-submits on timeout after login step starts timer', async () => {
    jest.useFakeTimers();

    quizPublicAPI.getQuiz.mockResolvedValueOnce({
      data: {
        ...activeQuizPayload,
        durationMinutes: 1,
      },
    });
    quizPublicAPI.submitAttempt.mockResolvedValueOnce({
      data: {
        participantName: 'Guest User',
        score: 1,
        totalQuestions: 2,
        timeTakenSeconds: 60,
        autoSubmitted: true,
      },
    });
    quizPublicAPI.getLeaderboard
      .mockResolvedValueOnce({
        data: {
          quizCode: 'QUIZ123',
          leaderboard: [],
        },
      })
      .mockResolvedValueOnce({
        data: {
          quizCode: 'QUIZ123',
          leaderboard: [
            {
              rank: 1,
              participantName: 'Guest User',
              participantEnterpriseId: null,
              score: 1,
              totalQuestions: 2,
              timeTakenSeconds: 60,
              submittedAt: '2026-06-20T06:10:00Z',
            },
          ],
        },
      });

    render(<QuizAttemptPage />);

    await waitFor(() => {
      expect(screen.getByText('Choose Login Method')).toBeInTheDocument();
    });

    fireEvent.change(screen.getByPlaceholderText('Enter your full name'), {
      target: { value: 'Guest User' },
    });
    fireEvent.click(screen.getByRole('button', { name: /continue to questions/i }));

    await waitFor(() => {
      expect(screen.getByText('Attempt Questions')).toBeInTheDocument();
    });

    await act(async () => {
      jest.advanceTimersByTime(61000);
    });

    await waitFor(() => {
      expect(quizPublicAPI.submitAttempt).toHaveBeenCalledTimes(1);
    });

    expect(quizPublicAPI.submitAttempt.mock.calls[0][1]).toMatchObject({
      autoSubmitted: true,
      participantName: 'Guest User',
    });

    jest.useRealTimers();
  });

  test('shows already-attempted message and leaderboard on entry when participant cannot reattempt', async () => {
    quizPublicAPI.getQuiz.mockResolvedValueOnce({
      data: {
        ...activeQuizPayload,
        canAttempt: false,
        alreadyAttempted: true,
        participantNotice: 'You have already attempted this quiz. You can view the leaderboard.',
        questions: [],
      },
    });

    render(<QuizAttemptPage />);

    await waitFor(() => {
      expect(screen.getByText('You have already attempted this quiz. You can view the leaderboard.')).toBeInTheDocument();
      expect(screen.getByText('Leaderboard')).toBeInTheDocument();
      expect(screen.getByRole('button', { name: /go to home/i })).toBeInTheDocument();
    });

    fireEvent.click(screen.getByRole('button', { name: /go to home/i }));
    expect(mockNavigate).toHaveBeenCalledWith('/participant');
    expect(screen.queryByText('Choose Login Method')).not.toBeInTheDocument();
  });
});
