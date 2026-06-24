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
});
