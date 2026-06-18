import React from 'react';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import EditQuestionPage from '../../pages/EditQuestionPage';
import { mcqAPI, aiAPI } from '../../services/api';

const mockNavigate = jest.fn();

jest.mock('react-router-dom', () => ({
  ...jest.requireActual('react-router-dom'),
  useNavigate: () => mockNavigate,
  useParams: () => ({ id: '101' }),
  useLocation: () => ({ state: { returnToPage: 0 } }),
}));

jest.mock('../../services/api', () => ({
  mcqAPI: {
    getById: jest.fn(),
    update: jest.fn(),
    delete: jest.fn(),
  },
  aiAPI: {
    checkSimilarity: jest.fn(),
  },
}));

jest.mock('../../context/AuthContext', () => ({
  useAuth: () => ({ user: { role: 'SME', enterpriseId: 'sme.user' } }),
}));

jest.mock('../../components/MCQForm', () => {
  return function MockMCQForm({ onSaveAndSend, showDuplicateCheck }) {
    const data = {
      questionStem: 'What is Java?',
      questionType: 'MCQ',
      stackId: '1',
      topicId: '2',
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
        <button onClick={() => onSaveAndSend(data)}>mock-send</button>
        <div data-testid="show-duplicate-check">{String(showDuplicateCheck)}</div>
      </div>
    );
  };
});

jest.mock('react-hot-toast', () => ({
  __esModule: true,
  default: { success: jest.fn(), error: jest.fn() },
  Toaster: () => null,
}));

describe('EditQuestionPage duplicate check behavior', () => {
  beforeEach(() => {
    jest.clearAllMocks();
  });

  test('skips similarity check for AI-generated question on Save and Send', async () => {
    mcqAPI.getById.mockResolvedValue({
      data: {
        questionId: 101,
        questionStem: 'What is Java?',
        questionType: 'MCQ',
        stackId: 1,
        topicId: 2,
        difficulty: 'Easy',
        optionA: 'A',
        optionB: 'B',
        optionC: 'C',
        optionD: 'D',
        optionE: null,
        optionF: null,
        correctAnswer: 'A',
        stackName: 'Java',
        status: 'DRAFT',
        aiGenerated: true,
      },
    });
    mcqAPI.update.mockResolvedValue({ data: {} });

    render(<EditQuestionPage />);

    await waitFor(() => expect(screen.getByText('mock-send')).toBeInTheDocument());
    expect(screen.getByTestId('show-duplicate-check')).toHaveTextContent('false');

    fireEvent.click(screen.getByText('mock-send'));

    await waitFor(() => {
      expect(aiAPI.checkSimilarity).not.toHaveBeenCalled();
      expect(mcqAPI.update).toHaveBeenCalledWith(
        '101',
        expect.objectContaining({ sendForReview: true })
      );
    });
  });

  test('runs similarity check for manual question before Save and Send', async () => {
    mcqAPI.getById.mockResolvedValue({
      data: {
        questionId: 101,
        questionStem: 'What is Java?',
        questionType: 'MCQ',
        stackId: 1,
        topicId: 2,
        difficulty: 'Easy',
        optionA: 'A',
        optionB: 'B',
        optionC: 'C',
        optionD: 'D',
        optionE: null,
        optionF: null,
        correctAnswer: 'A',
        stackName: 'Java',
        status: 'DRAFT',
        aiGenerated: false,
      },
    });
    aiAPI.checkSimilarity.mockResolvedValue({ data: { hasSimilar: true, similarQuestions: [] } });

    render(<EditQuestionPage />);

    await waitFor(() => expect(screen.getByText('mock-send')).toBeInTheDocument());
    expect(screen.getByTestId('show-duplicate-check')).toHaveTextContent('true');

    fireEvent.click(screen.getByText('mock-send'));

    await waitFor(() => {
      expect(aiAPI.checkSimilarity).toHaveBeenCalledTimes(1);
      expect(mcqAPI.update).not.toHaveBeenCalled();
    });
  });
});
