// Mock axios BEFORE importing api.js
jest.mock('axios', () => {
  const mockAxios = {
    create: jest.fn(() => mockAxios),
    interceptors: {
      request:  { use: jest.fn(), eject: jest.fn() },
      response: { use: jest.fn(), eject: jest.fn() },
    },
    get:    jest.fn(),
    post:   jest.fn(),
    put:    jest.fn(),
    delete: jest.fn(),
    defaults: { headers: { common: {} } },
  };
  return { default: mockAxios, ...mockAxios };
});

import { mcqAPI, adminAPI, referenceAPI, aiAPI } from '../../services/api';
import api from '../../services/api';

describe('mcqAPI', () => {
  afterEach(() => jest.clearAllMocks());

  test('getMyQuestions builds correct URL with no filters', async () => {
    api.get = jest.fn().mockResolvedValue({ data: {} });
    await mcqAPI.getMyQuestions(0, 10, {});
    expect(api.get).toHaveBeenCalledWith(
      expect.stringContaining('/api/mcq/my-questions?page=0&size=10')
    );
  });

  test('getMyQuestions builds URL with status filter', async () => {
    api.get = jest.fn().mockResolvedValue({ data: {} });
    await mcqAPI.getMyQuestions(0, 10, { status: 'DRAFT' });
    expect(api.get).toHaveBeenCalledWith(
      expect.stringContaining('status=DRAFT')
    );
  });

  test('getMyQuestions builds URL with difficulty filter', async () => {
    api.get = jest.fn().mockResolvedValue({ data: {} });
    await mcqAPI.getMyQuestions(0, 10, { difficulty: 'Hard' });
    expect(api.get).toHaveBeenCalledWith(
      expect.stringContaining('difficulty=Hard')
    );
  });

  test('create posts to /api/mcq', async () => {
    api.post = jest.fn().mockResolvedValue({ data: {} });
    const payload = { questionStem: 'Test?', stackId: 1, topicId: 1 };
    await mcqAPI.create(payload);
    expect(api.post).toHaveBeenCalledWith('/api/mcq', payload);
  });

  test('update puts to /api/mcq/{id}', async () => {
    api.put = jest.fn().mockResolvedValue({ data: {} });
    await mcqAPI.update(42, { questionStem: 'Updated?' });
    expect(api.put).toHaveBeenCalledWith('/api/mcq/42', { questionStem: 'Updated?' });
  });

  test('review posts to /api/mcq/{id}/review', async () => {
    api.post = jest.fn().mockResolvedValue({ data: {} });
    await mcqAPI.review(5, { action: 'APPROVE' });
    expect(api.post).toHaveBeenCalledWith('/api/mcq/5/review', { action: 'APPROVE' });
  });

  test('downloadTemplate calls with blob responseType', async () => {
    api.get = jest.fn().mockResolvedValue({ data: new Blob() });
    await mcqAPI.downloadTemplate();
    expect(api.get).toHaveBeenCalledWith(
      '/api/mcq/template', { responseType: 'blob' }
    );
  });
});

describe('adminAPI', () => {
  afterEach(() => {
    jest.clearAllMocks();
    localStorage.removeItem('smartquiz-language');
  });

  test('getQuestionBank appends languageCode from localStorage', async () => {
    localStorage.setItem('smartquiz-language', 'es');
    api.get = jest.fn().mockResolvedValue({ data: {} });

    await adminAPI.getQuestionBank(0, 10, {});

    const calledUrl = api.get.mock.calls[0][0];
    expect(calledUrl).toContain('page=0');
    expect(calledUrl).toContain('size=10');
    expect(calledUrl).toContain('languageCode=es');
  });

  test('getQuestionBank falls back to en for unsupported localStorage language', async () => {
    localStorage.setItem('smartquiz-language', 'de');
    api.get = jest.fn().mockResolvedValue({ data: {} });

    await adminAPI.getQuestionBank(0, 10, {});

    const calledUrl = api.get.mock.calls[0][0];
    expect(calledUrl).toContain('languageCode=en');
  });

  test('getQuestionBank builds correct URL with all filters', async () => {
    api.get = jest.fn().mockResolvedValue({ data: {} });
    await adminAPI.getQuestionBank(1, 10, {
      stackId: 2, topicId: 3, difficulty: 'Easy', status: 'DRAFT', search: 'java'
    });
    const calledUrl = api.get.mock.calls[0][0];
    expect(calledUrl).toContain('page=1');
    expect(calledUrl).toContain('stackId=2');
    expect(calledUrl).toContain('topicId=3');
    expect(calledUrl).toContain('difficulty=Easy');
    expect(calledUrl).toContain('status=DRAFT');
    expect(calledUrl).toContain('search=java');
  });

  test('assignReviewer posts to correct endpoint', async () => {
    api.post = jest.fn().mockResolvedValue({ data: {} });
    await adminAPI.assignReviewer(10, 'reviewer.id');
    expect(api.post).toHaveBeenCalledWith(
      '/api/admin/assign-reviewer/10',
      { reviewerEnterpriseId: 'reviewer.id' }
    );
  });
});

describe('referenceAPI', () => {
  afterEach(() => jest.clearAllMocks());

  test('getStacks calls /api/reference/stacks', async () => {
    api.get = jest.fn().mockResolvedValue({ data: [] });
    await referenceAPI.getStacks();
    expect(api.get).toHaveBeenCalledWith('/api/reference/stacks');
  });

  test('getTopics calls /api/reference/topics/{stackId}', async () => {
    api.get = jest.fn().mockResolvedValue({ data: [] });
    await referenceAPI.getTopics(3);
    expect(api.get).toHaveBeenCalledWith('/api/reference/topics/3');
  });
});

describe('aiAPI', () => {
  afterEach(() => jest.clearAllMocks());

  test('chat posts to /api/ai/chat', async () => {
    api.post = jest.fn().mockResolvedValue({ data: { answer: 'hello' } });
    const payload = { message: 'hi', history: [{ role: 'user', content: 'hi' }] };
    await aiAPI.chat(payload);
    expect(api.post).toHaveBeenCalledWith('/api/ai/chat', payload);
  });

  test('getChatHistory gets /api/ai/chat/history', async () => {
    api.get = jest.fn().mockResolvedValue({ data: { messages: [] } });
    await aiAPI.getChatHistory(25);
    expect(api.get).toHaveBeenCalledWith('/api/ai/chat/history?limit=25');
  });

  test('getChatHistory includes sessionId when provided', async () => {
    api.get = jest.fn().mockResolvedValue({ data: { messages: [] } });
    await aiAPI.getChatHistory(25, 'session-abc');
    expect(api.get).toHaveBeenCalledWith('/api/ai/chat/history?limit=25&sessionId=session-abc');
  });

  test('getChatSessions gets /api/ai/chat/sessions', async () => {
    api.get = jest.fn().mockResolvedValue({ data: { sessions: [] } });
    await aiAPI.getChatSessions(5);
    expect(api.get).toHaveBeenCalledWith('/api/ai/chat/sessions?limit=5');
  });

  test('getChatSuggestions gets /api/ai/chat/suggestions', async () => {
    api.get = jest.fn().mockResolvedValue({ data: { suggestions: [] } });
    await aiAPI.getChatSuggestions();
    expect(api.get).toHaveBeenCalledWith('/api/ai/chat/suggestions');
  });

  test('streamChat parses delta and done events', async () => {
    const { TextEncoder, TextDecoder } = require('util');
    global.TextEncoder = TextEncoder;
    global.TextDecoder = TextDecoder;

    const chunks = [
      'event: message\n',
      'data: {"delta":"Hello","done":false}\n\n',
      'event: done\n',
      'data: {"delta":"","done":true}\n\n',
    ];

    global.fetch = jest.fn().mockResolvedValue({
      ok: true,
      body: {
        getReader: () => {
          let index = 0;
          return {
            read: async () => {
              if (index >= chunks.length) {
                return { done: true, value: undefined };
              }
              const value = new TextEncoder().encode(chunks[index]);
              index += 1;
              return { done: false, value };
            },
          };
        },
      },
    });

    const onDelta = jest.fn();
    const onDone = jest.fn();

    await aiAPI.streamChat(
      { message: 'Hi', history: [] },
      { onDelta, onDone }
    );

    expect(onDelta).toHaveBeenCalledWith('Hello');
    expect(onDone).toHaveBeenCalled();
  });
});