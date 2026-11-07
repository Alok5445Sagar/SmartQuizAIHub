import React from 'react';
import { act, fireEvent, render, screen, waitFor } from '@testing-library/react';
import toast from 'react-hot-toast';
import AIGeneratePage, { GENERATION_JOB_KEY, GENERATION_POLL_MS } from '../../pages/AIGeneratePage';
import { aiAPI, referenceAPI } from '../../services/api';

jest.mock('react-router-dom', () => ({
  ...jest.requireActual('react-router-dom'),
  useNavigate: () => jest.fn(),
}));

jest.mock('react-hot-toast', () => ({
  __esModule: true,
  default: { success: jest.fn(), error: jest.fn() },
}));

jest.mock('../../services/api', () => ({
  aiAPI: {
    startGeneration: jest.fn(),
    getGenerationJob: jest.fn(),
  },
  referenceAPI: {
    getStacks: jest.fn(),
    getTopics: jest.fn(),
  },
}));

const question = (id, type) => ({
  questionId: id,
  questionStem: `Generated stem ${id}`,
  questionType: type,
  optionA: 'A1', optionB: 'B1', optionC: 'C1', optionD: 'D1',
  correctAnswer: 'A',
});

const job = (status, extra = {}) => ({
  jobId: 'job-1',
  status,
  requested: 2,
  generatedSoFar: 0,
  generated: 0,
  questions: [],
  ...extra,
});

async function fillAndSubmit() {
  await waitFor(() => expect(screen.getByRole('option', { name: 'Java' })).toBeInTheDocument());
  const [stackSelect, topicSelect] = screen.getAllByRole('combobox');
  fireEvent.change(stackSelect, { target: { value: '1' } });
  await waitFor(() => expect(screen.getByRole('option', { name: 'Streams' })).toBeInTheDocument());
  fireEvent.change(topicSelect, { target: { value: '7' } });
  fireEvent.click(screen.getByRole('button', { name: /Generate Questions/ }));
}

describe('AIGeneratePage background generation', () => {
  beforeEach(() => {
    jest.useFakeTimers();
    jest.clearAllMocks();
    sessionStorage.clear();
    referenceAPI.getStacks.mockResolvedValue({ data: [{ stackId: 1, stackName: 'Java' }] });
    referenceAPI.getTopics.mockResolvedValue({ data: [{ topicId: 7, topicName: 'Streams' }] });
  });

  afterEach(() => {
    jest.useRealTimers();
  });

  test('submits a job, shows progress while polling, then renders the saved drafts', async () => {
    aiAPI.startGeneration.mockResolvedValue({ data: job('QUEUED', { queuePosition: 1 }) });
    aiAPI.getGenerationJob
      .mockResolvedValueOnce({ data: job('RUNNING', { generatedSoFar: 1 }) })
      .mockResolvedValueOnce({
        data: job('SUCCEEDED', {
          generated: 2,
          questions: [question(11, 'MCQ'), question(12, 'MSQ')],
          message: '2 question(s) generated and saved as Draft.',
        }),
      });

    render(<AIGeneratePage onBack={jest.fn()} />);
    await fillAndSubmit();

    await waitFor(() => expect(aiAPI.startGeneration).toHaveBeenCalledWith({
      stackId: 1, topicId: 7, difficulty: 'Medium', numberOfQuestions: 3, generateMCQ: true, generateMSQ: false,
    }));
    expect(sessionStorage.getItem(GENERATION_JOB_KEY)).toBe('job-1');
    expect(await screen.findByTestId('ai-generation-progress')).toHaveTextContent('1 / 2');

    await act(async () => {
      jest.advanceTimersByTime(GENERATION_POLL_MS);
    });

    expect(await screen.findByText('Generated stem 12')).toBeInTheDocument();
    expect(screen.getByText('Generated stem 11')).toBeInTheDocument();
    expect(toast.success).toHaveBeenCalledWith('2 question(s) generated and saved as Draft.');
    expect(sessionStorage.getItem(GENERATION_JOB_KEY)).toBeNull();
    expect(aiAPI.getGenerationJob).toHaveBeenCalledTimes(2);
  });

  test('a failed job shows its error and re-enables the form', async () => {
    aiAPI.startGeneration.mockResolvedValue({ data: job('QUEUED') });
    aiAPI.getGenerationJob.mockResolvedValue({
      data: job('FAILED', { error: 'Local AI service (Ollama) is unavailable.' }),
    });

    render(<AIGeneratePage onBack={jest.fn()} />);
    await fillAndSubmit();

    await waitFor(() => expect(toast.error).toHaveBeenCalledWith('Local AI service (Ollama) is unavailable.'));
    expect(screen.getByRole('button', { name: /Generate Questions/ })).not.toBeDisabled();
    expect(sessionStorage.getItem(GENERATION_JOB_KEY)).toBeNull();
  });

  test('resumes polling a job stored from an earlier visit', async () => {
    sessionStorage.setItem(GENERATION_JOB_KEY, 'job-1');
    aiAPI.getGenerationJob.mockResolvedValue({
      data: job('SUCCEEDED', { generated: 1, questions: [question(21, 'MCQ')], message: 'done' }),
    });

    render(<AIGeneratePage onBack={jest.fn()} />);

    expect(await screen.findByText('Generated stem 21')).toBeInTheDocument();
    expect(aiAPI.getGenerationJob).toHaveBeenCalledWith('job-1');
    expect(aiAPI.startGeneration).not.toHaveBeenCalled();
  });

  test('forgets a stored job the server no longer knows', async () => {
    sessionStorage.setItem(GENERATION_JOB_KEY, 'gone');
    aiAPI.getGenerationJob.mockRejectedValue({ response: { status: 404 } });

    render(<AIGeneratePage onBack={jest.fn()} />);

    await waitFor(() => expect(sessionStorage.getItem(GENERATION_JOB_KEY)).toBeNull());
    expect(screen.getByRole('button', { name: /Generate Questions/ })).not.toBeDisabled();
    expect(toast.error).not.toHaveBeenCalled();
  });

  test('keeps polling through a transient network error', async () => {
    sessionStorage.setItem(GENERATION_JOB_KEY, 'job-1');
    aiAPI.getGenerationJob
      .mockRejectedValueOnce(new Error('Network Error'))
      .mockResolvedValueOnce({ data: job('SUCCEEDED', { generated: 1, questions: [question(31, 'MCQ')], message: 'ok' }) });

    render(<AIGeneratePage onBack={jest.fn()} />);
    await waitFor(() => expect(aiAPI.getGenerationJob).toHaveBeenCalledTimes(1));

    await act(async () => {
      jest.advanceTimersByTime(GENERATION_POLL_MS);
    });

    expect(await screen.findByText('Generated stem 31')).toBeInTheDocument();
  });
});
