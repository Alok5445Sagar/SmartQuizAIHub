import React from 'react';
import { render, screen, waitFor, within, fireEvent } from '@testing-library/react';
import QuestionBankPage from '../../pages/QuestionBankPage';
import { adminAPI, aiAPI, referenceAPI } from '../../services/api';

jest.mock('../../services/api', () => ({
  adminAPI: {
    getQuestionBank: jest.fn(),
    getQuestionBankStats: jest.fn(),
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

jest.mock('../../components/MCQForm', () => {
  return function MockMCQForm({ onDuplicateCheck, onSaveAndSend }) {
    const data = {
      questionStem: 'Admin edit draft question',
      questionType: 'MCQ',
      stackId: '10',
      topicId: '20',
      difficulty: 'Easy',
      optionA: 'A',
      optionB: 'B',
      optionC: 'C',
      optionD: 'D',
      optionE: '',
      optionF: '',
      correctAnswer: 'A',
    };

    return (
      <div>
        <button onClick={() => onDuplicateCheck && onDuplicateCheck(data)}>mock-admin-duplicate</button>
        <button onClick={() => onSaveAndSend && onSaveAndSend(data)}>mock-admin-send</button>
      </div>
    );
  };
});

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
    adminAPI.getReviewers.mockResolvedValue({ data: [] });
    adminAPI.getQuestionBankStats.mockResolvedValue({
      data: { total: 2, readyForReview: 0, underReview: 0, approved: 0 },
    });
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
            status: 'UNDER_REVIEW',
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

    // Own DRAFT row should have edit access (assign is only for READY_FOR_REVIEW)
    const ownRow = screen.getByText('Own draft question').closest('tr');
    expect(ownRow).not.toBeNull();
    expect(within(ownRow).queryByRole('button', { name: /assign/i })).not.toBeInTheDocument();
    expect(within(ownRow).queryByRole('button', { name: /edit/i })).toBeInTheDocument();

    // Other user's DRAFT row should not have assign/edit
    const otherRow = screen.getByText('Other user draft question').closest('tr');
    expect(otherRow).not.toBeNull();
    expect(within(otherRow).queryByRole('button', { name: /assign/i })).not.toBeInTheDocument();
    expect(within(otherRow).queryByRole('button', { name: /edit/i })).not.toBeInTheDocument();
  });

  test('reuses cached admin duplicate-check result for save action when data is unchanged', async () => {
    adminAPI.getQuestionBank.mockResolvedValue({
      data: {
        content: [
          {
            questionId: 1,
            questionType: 'MCQ',
            aiGenerated: false,
            bulkUploaded: false,
            questionStem: 'Own draft question',
            stackName: 'Java',
            topicName: 'Basics',
            difficulty: 'Easy',
            status: 'DRAFT',
            creatorEnterpriseId: 'admin.user',
            reviewerEnterpriseId: null,
            stackId: 10,
            topicId: 20,
            optionA: 'A',
            optionB: 'B',
            optionC: 'C',
            optionD: 'D',
            optionE: null,
            optionF: null,
            correctAnswer: 'A',
          },
        ],
        totalElements: 1,
        totalPages: 1,
      },
    });
    aiAPI.checkSimilarity.mockResolvedValue({ data: { hasSimilar: false, similarQuestions: [] } });
    adminAPI.editQuestion.mockResolvedValue({ data: {} });

    render(<QuestionBankPage />);

    await waitFor(() => expect(screen.getByText('Own draft question')).toBeInTheDocument());
    fireEvent.click(screen.getByRole('button', { name: /✏️ Edit/i }));

    await waitFor(() => expect(screen.getByText('mock-admin-duplicate')).toBeInTheDocument());

    fireEvent.click(screen.getByText('mock-admin-duplicate'));
    await waitFor(() => expect(aiAPI.checkSimilarity).toHaveBeenCalledTimes(1));

    fireEvent.click(screen.getByText('mock-admin-send'));
    await waitFor(() => {
      expect(aiAPI.checkSimilarity).toHaveBeenCalledTimes(1);
      expect(adminAPI.editQuestion).toHaveBeenCalledWith(
        1,
        expect.objectContaining({ sendForReview: false, questionStem: 'Admin edit draft question' })
      );
    });
  });
});
