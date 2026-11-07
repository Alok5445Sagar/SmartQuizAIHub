import React from 'react';
import { render, screen, waitFor } from '@testing-library/react';
import MyQuestionsPage from '../../pages/MyQuestionsPage';
import { mcqAPI, referenceAPI } from '../../services/api';

const mockNavigate = jest.fn();

jest.mock('react-router-dom', () => ({
  ...jest.requireActual('react-router-dom'),
  useNavigate: () => mockNavigate,
  useLocation: () => ({ state: { returnToPage: 1, returnFilters: {} } }),
}));

jest.mock('../../context/AuthContext', () => ({
  useAuth: () => ({ user: { role: 'SME', enterpriseId: 'sme.user' } }),
}));

jest.mock('../../services/api', () => ({
  mcqAPI: {
    getMyQuestions: jest.fn(),
    getMyQuestionStats: jest.fn(),
  },
  referenceAPI: {
    getStacks: jest.fn(),
    getTopics: jest.fn(),
  },
}));

describe('MyQuestionsPage pagination retention', () => {
  beforeEach(() => {
    jest.clearAllMocks();
    referenceAPI.getStacks.mockResolvedValue({ data: [] });
    referenceAPI.getTopics.mockResolvedValue({ data: [] });
    mcqAPI.getMyQuestionStats.mockResolvedValue({
      data: { total: 11, DRAFT: 7, READY_FOR_REVIEW: 1, UNDER_REVIEW: 0, APPROVED: 3, REJECTED: 0 },
    });
  });

  test('keeps current page when returned page has no items', async () => {
    mcqAPI.getMyQuestions.mockResolvedValue({
      data: {
        content: [],
        totalElements: 11,
        totalPages: 2,
      },
    });

    render(<MyQuestionsPage />);

    await waitFor(() => {
      expect(mcqAPI.getMyQuestions).toHaveBeenCalledWith(1, 10, {
        stackId: undefined,
        topicId: undefined,
        difficulty: undefined,
        status: undefined,
      });
    });

    // Regression check: should not auto-decrement page and refetch previous page.
    expect(mcqAPI.getMyQuestions).toHaveBeenCalledTimes(1);
    expect(screen.getByText('No questions yet')).toBeInTheDocument();
  });

  test('tab badges show per-status totals from the stats endpoint, not just the current page', async () => {
    mcqAPI.getMyQuestions.mockResolvedValue({
      data: {
        content: [{ questionId: 1, questionStem: 'Only row', status: 'DRAFT', difficulty: 'Easy', questionType: 'MCQ', stackName: 'Java', topicName: 'OOP' }],
        totalElements: 11,
        totalPages: 2,
      },
    });

    render(<MyQuestionsPage />);

    await waitFor(() => expect(mcqAPI.getMyQuestionStats).toHaveBeenCalledWith({
      stackId: undefined, topicId: undefined, difficulty: undefined,
    }));
    const draftTab = await screen.findByRole('button', { name: /Draft\s*7/ });
    expect(draftTab).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /Approved\s*3/ })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /All\s*11/ })).toBeInTheDocument();
  });
});