import React from 'react';
import { render, screen, waitFor, within } from '@testing-library/react';
import QuestionBankPage from '../../pages/QuestionBankPage';
import { adminAPI, referenceAPI } from '../../services/api';

jest.mock('../../services/api', () => ({
  adminAPI: {
    getQuestionBank: jest.fn(),
    getReviewers: jest.fn(),
    assignReviewer: jest.fn(),
    editQuestion: jest.fn(),
  },
  aiAPI: {
    checkSimilarity: jest.fn(),
  },
  referenceAPI: {
    getStacks: jest.fn(),
    getTopics: jest.fn(),
  },
}));

jest.mock('../../context/AuthContext', () => ({
  useAuth: () => ({ user: { role: 'ADMIN', enterpriseId: 'admin.user' } }),
}));

jest.mock('react-hot-toast', () => ({
  __esModule: true,
  default: { success: jest.fn(), error: jest.fn() },
  Toaster: () => null,
}));

describe('QuestionBankPage admin draft actions', () => {
  beforeEach(() => {
    jest.clearAllMocks();
    referenceAPI.getStacks.mockResolvedValue({ data: [] });
    referenceAPI.getTopics.mockResolvedValue({ data: [] });
  });

  test('shows Assign and Edit for own DRAFT, but not for another user DRAFT', async () => {
    adminAPI.getQuestionBank.mockResolvedValue({
      data: {
        content: [
          {
            questionId: 1,
            questionType: 'MCQ',
            aiGenerated: false,
            questionStem: 'Own draft question',
            stackName: 'Java',
            topicName: 'Basics',
            difficulty: 'Easy',
            status: 'DRAFT',
            creatorEnterpriseId: 'admin.user',
            reviewerEnterpriseId: null,
            stackId: 10,
            topicId: 20,
          },
          {
            questionId: 2,
            questionType: 'MCQ',
            aiGenerated: false,
            questionStem: 'Other user draft question',
            stackName: 'Java',
            topicName: 'Basics',
            difficulty: 'Medium',
            status: 'DRAFT',
            creatorEnterpriseId: 'sme.user',
            reviewerEnterpriseId: null,
            stackId: 10,
            topicId: 20,
          },
        ],
        totalElements: 2,
        totalPages: 1,
      },
    });

    render(<QuestionBankPage />);

    await waitFor(() => {
      expect(screen.getByText('Own draft question')).toBeInTheDocument();
      expect(screen.getByText('Other user draft question')).toBeInTheDocument();
    });

    // Own DRAFT row should have both actions
    const ownRow = screen.getByText('Own draft question').closest('tr');
    expect(ownRow).not.toBeNull();
    expect(within(ownRow).queryByRole('button', { name: /assign/i })).toBeInTheDocument();
    expect(within(ownRow).queryByRole('button', { name: /edit/i })).toBeInTheDocument();

    // Other user's DRAFT row should not have assign/edit
    const otherRow = screen.getByText('Other user draft question').closest('tr');
    expect(otherRow).not.toBeNull();
    expect(within(otherRow).queryByRole('button', { name: /assign/i })).not.toBeInTheDocument();
    expect(within(otherRow).queryByRole('button', { name: /edit/i })).not.toBeInTheDocument();
  });
});
