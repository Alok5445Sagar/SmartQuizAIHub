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

  test('startGeneration posts to /api/ai/generate', async () => {
    api.post = jest.fn().mockResolvedValue({ data: { jobId: 'j1', status: 'QUEUED' } });
    const payload = { stackId: 1, topicId: 2, difficulty: 'Easy', numberOfQuestions: 3 };
    await aiAPI.startGeneration(payload);
    expect(api.post).toHaveBeenCalledWith('/api/ai/generate', payload);
  });

  test('getGenerationJob gets /api/ai/generate/{jobId} with the id encoded', async () => {
    api.get = jest.fn().mockResolvedValue({ data: { status: 'RUNNING' } });
    await aiAPI.getGenerationJob('a/b');
    expect(api.get).toHaveBeenCalledWith('/api/ai/generate/a%2Fb');
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

  const mockStream = (chunks) => {
    const { TextEncoder, TextDecoder } = require('util');
    global.TextEncoder = TextEncoder;
    global.TextDecoder = TextDecoder;
    global.fetch = jest.fn().mockResolvedValue({
      ok: true,
      body: {
        getReader: () => {
          let index = 0;
          return {
            read: async () => (index >= chunks.length
              ? { done: true, value: undefined }
              : { done: false, value: new TextEncoder().encode(chunks[index++]) }),
          };
        },
      },
    });
  };

  test('streamChat throws on an error event and marks it partial after deltas', async () => {
    mockStream([
      'event:message\ndata:{"delta":"Part","done":false}\n\n',
      'event:error\ndata:{"message":"The AI response was interrupted."}\n\n',
    ]);
    const onDelta = jest.fn();

    await expect(aiAPI.streamChat({ message: 'Hi' }, { onDelta })).rejects.toMatchObject({
      message: 'The AI response was interrupted.',
      partial: true,
    });
    expect(onDelta).toHaveBeenCalledWith('Part');
  });

  test('streamChat error before any delta is not partial (caller may fall back)', async () => {
    mockStream(['event:error\ndata:{"message":"Too many chat streams"}\n\n']);
    await expect(aiAPI.streamChat({ message: 'Hi' }, {})).rejects.toMatchObject({ partial: false });
  });
});

describe('quizPublicAPI credentials', () => {
  // axios.create is mocked to return the same instance, so the public client is `api` here.
  const { quizPublicAPI } = require('../../services/api');

  afterEach(() => jest.clearAllMocks());

  test('chat users sends the token as a Bearer header, never in the URL', async () => {
    api.get = jest.fn().mockResolvedValue({ data: {} });
    await quizPublicAPI.getParticipantChatUsers({ participantEnterpriseId: 'p1', participantAuthToken: 'tok' });
    const [url, config] = api.get.mock.calls[0];
    expect(url).toBe('/api/public/quiz/participant/chat/users');
    expect(url).not.toContain('tok');
    expect(config.headers.Authorization).toBe('Bearer tok');
  });

  test('send message strips id and token from the body', async () => {
    api.post = jest.fn().mockResolvedValue({ data: {} });
    await quizPublicAPI.sendParticipantChatMessage({
      participantEnterpriseId: 'p1', participantAuthToken: 'tok', recipientEnterpriseId: 'p2', message: 'hi',
    });
    const [, body, config] = api.post.mock.calls[0];
    expect(body).toEqual({ recipientEnterpriseId: 'p2', message: 'hi' });
    expect(config.headers.Authorization).toBe('Bearer tok');
  });

  test('submit attempt sends the token as a header and not in the body', async () => {
    api.post = jest.fn().mockResolvedValue({ data: {} });
    await quizPublicAPI.submitAttempt('Q1', { participantName: 'A', participantAuthToken: 'tok', answers: [] });
    const [url, body, config] = api.post.mock.calls[0];
    expect(url).toBe('/api/public/quiz/Q1/attempt');
    expect(body).not.toHaveProperty('participantAuthToken');
    expect(config.headers.Authorization).toBe('Bearer tok');
  });

  test('guest attempts send no Authorization header', async () => {
    api.post = jest.fn().mockResolvedValue({ data: {} });
    await quizPublicAPI.submitAttempt('Q1', { participantName: 'Guest', participantAuthToken: null, answers: [] });
    expect(api.post.mock.calls[0][2].headers).toEqual({});
  });

  test('stream URL carries a one-time ticket instead of the token', async () => {
    api.post = jest.fn().mockResolvedValue({ data: { ticket: 'tkt-1' } });
    const url = await quizPublicAPI.createParticipantChatStreamUrl({ participantAuthToken: 'tok' });
    expect(api.post).toHaveBeenCalledWith(
      '/api/public/quiz/participant/chat/stream-ticket', null, { headers: { Authorization: 'Bearer tok' } }
    );
    expect(url).toContain('ticket=tkt-1');
    expect(url).not.toContain('tok&');
    expect(url).not.toContain('participantAuthToken');
  });

  test('chat stream reconnects with a fresh ticket after an error', async () => {
    jest.useFakeTimers();
    const sources = [];
    const originalEventSource = global.EventSource;
    global.EventSource = jest.fn().mockImplementation((url) => {
      const source = { url, addEventListener: jest.fn(), close: jest.fn() };
      sources.push(source);
      return source;
    });
    let n = 0;
    api.post = jest.fn().mockImplementation(() => Promise.resolve({ data: { ticket: `t${++n}` } }));

    const close = quizPublicAPI.openParticipantChatStream({ participantAuthToken: 'tok', onEvent: jest.fn() });
    await Promise.resolve(); await Promise.resolve();
    expect(sources).toHaveLength(1);
    expect(sources[0].url).toContain('ticket=t1');

    sources[0].onerror();
    jest.advanceTimersByTime(3000);
    await Promise.resolve(); await Promise.resolve();
    expect(sources).toHaveLength(2);
    expect(sources[1].url).toContain('ticket=t2');

    close();
    expect(sources[1].close).toHaveBeenCalled();
    global.EventSource = originalEventSource;
    jest.useRealTimers();
  });
});
