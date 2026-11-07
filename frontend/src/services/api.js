import axios from 'axios';
import { clearAuthSession, getAuthToken } from './authSession';

const api = axios.create({
  baseURL: process.env.REACT_APP_API_URL || 'http://localhost:8080',
  headers: { 'Content-Type': 'application/json' }
});

const publicApi = axios.create({
  baseURL: process.env.REACT_APP_API_URL || 'http://localhost:8080',
  headers: { 'Content-Type': 'application/json' }
});

const getCurrentLanguageCode = () => {
  const code = (localStorage.getItem('smartquiz-language') || 'en').toLowerCase();
  return ['en', 'es', 'fr', 'ro'].includes(code) ? code : 'en';
};

api.interceptors.response.use(
  res => res,
  err => {
    if (err.response?.status === 401) {
      clearAuthSession();
      window.location.href = '/login';
    }
    return Promise.reject(err);
  }
);

api.interceptors.request.use((config) => {
  const token = getAuthToken();
  if (token) {
    config.headers = config.headers || {};
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

export const mcqAPI = {
  create: (data) => api.post('/api/mcq', data),
  update: (id, data) => api.put(`/api/mcq/${id}`, data),
  delete: (id) => api.delete(`/api/mcq/${id}`),
  getMyQuestions: (page = 0, size = 10, filters = {}) => {
    const params = new URLSearchParams({ page, size, languageCode: getCurrentLanguageCode() });
    if (filters.stackId)    params.append('stackId', filters.stackId);
    if (filters.topicId)    params.append('topicId', filters.topicId);
    if (filters.difficulty) params.append('difficulty', filters.difficulty);
    if (filters.status)     params.append('status', filters.status);
    return api.get(`/api/mcq/my-questions?${params.toString()}`);
  },
  getMyPendingReviews: (page = 0, size = 10) => api.get(`/api/mcq/my-pending-reviews?page=${page}&size=${size}&languageCode=${encodeURIComponent(getCurrentLanguageCode())}`),
  getById: (id, { languageCode } = {}) => {
    const code = languageCode || getCurrentLanguageCode();
    return api.get(`/api/mcq/${id}?languageCode=${encodeURIComponent(code)}`);
  },
  review: (id, data) => api.post(`/api/mcq/${id}/review`, data),
  bulkUpload: (formData) => api.post('/api/mcq/bulk-upload', formData, { headers: { 'Content-Type': 'multipart/form-data' } }),
  downloadTemplate: () => api.get('/api/mcq/template', { responseType: 'blob' }),
  getTranslations: (questionId) => api.get(`/api/mcq/${questionId}/translations`),
  suggestTranslation: (questionId, languageCode) => api.post(`/api/mcq/${questionId}/translations/suggest?languageCode=${encodeURIComponent(languageCode)}`),
  saveTranslation: (questionId, payload) => api.put(`/api/mcq/${questionId}/translations`, payload),
};

export const adminAPI = {
  getQuestionBank: (page = 0, size = 10, filters = {}) => {
    const params = new URLSearchParams({ page, size, languageCode: getCurrentLanguageCode() });
    if (filters.stackId)    params.append('stackId', filters.stackId);
    if (filters.topicId)    params.append('topicId', filters.topicId);
    if (filters.difficulty) params.append('difficulty', filters.difficulty);
    if (filters.status)     params.append('status', filters.status);
    if (filters.search)     params.append('search', filters.search);
    return api.get(`/api/admin/question-bank?${params.toString()}`);
  },
  getQuestionBankStats: (filters = {}) => {
    const params = new URLSearchParams();
    if (filters.stackId)    params.append('stackId', filters.stackId);
    if (filters.topicId)    params.append('topicId', filters.topicId);
    if (filters.difficulty) params.append('difficulty', filters.difficulty);
    if (filters.search)     params.append('search', filters.search);
    return api.get(`/api/admin/question-bank/stats?${params.toString()}`);
  },
  assignReviewer: (questionId, reviewerEnterpriseId) => api.post(`/api/admin/assign-reviewer/${questionId}`, { reviewerEnterpriseId }),
  editQuestion: (id, data) => api.put(`/api/admin/edit-question/${id}`, data),
  getReviewers: (stackId, excludeEnterpriseId) => api.get(`/api/admin/reviewers/${stackId}?excludeEnterpriseId=${excludeEnterpriseId}`),
};

export const quizAdminAPI = {
  getApprovedQuestionsByTopic: (topicId) => api.get(`/api/admin/quizzes/approved-questions?topicId=${topicId}`),
  getApprovedQuestionsByFilters: ({ stackIds = [], topicIds = [] } = {}) => {
    const params = new URLSearchParams();
    stackIds.filter(Boolean).forEach((id) => params.append('stackIds', id));
    topicIds.filter(Boolean).forEach((id) => params.append('topicIds', id));
    return api.get(`/api/admin/quizzes/approved-questions?${params.toString()}`);
  },
  autoPickQuestions: (data) => api.post('/api/admin/quizzes/auto-pick', data),
  publishQuiz: (data) => api.post('/api/admin/quizzes/publish', data),
  getQuizzes: (page = 0, size = 10) => api.get(`/api/admin/quizzes?page=${page}&size=${size}`),
  closeQuiz: (quizId) => api.post(`/api/admin/quizzes/${quizId}/close`),
  reopenQuiz: (quizId) => api.post(`/api/admin/quizzes/${quizId}/reopen`),
  rehostQuiz: (quizId) => api.post(`/api/admin/quizzes/${quizId}/rehost`),
  getParticipantAnalytics: (participantType = 'EMPLOYEE') => api.get(`/api/admin/quizzes/participant-analytics?participantType=${participantType}`),
  getHostedTopicsForLeaderboard: () => api.get('/api/admin/quizzes/leaderboard/topics'),
  getTopicLeaderboard: (topicId, participantType = 'ALL') => api.get(`/api/admin/quizzes/leaderboard/topic/${topicId}?participantType=${participantType}`),
  getOverallLeaderboard: (participantType = 'ALL') => api.get(`/api/admin/quizzes/leaderboard/overall?participantType=${participantType}`),
  downloadLeaderboardExcel: (quizId) => api.get(`/api/admin/quizzes/${quizId}/leaderboard/export`, { responseType: 'blob' }),
};

const bearer = (token) => (token ? { Authorization: `Bearer ${token}` } : {});

// Identity travels only in the Authorization header; strip legacy id/token fields from request bodies.
const withoutCredentials = ({ participantAuthToken, participantEnterpriseId, ...rest } = {}) => rest;

const CHAT_STREAM_RETRY_MS = 3000;

export const quizPublicAPI = {
  getQuiz: (quizCode, { participantName = '', languageCode = 'en', participantAuthToken = '' } = {}) => {
    const params = new URLSearchParams();
    if (participantName) params.append('participantName', participantName);
    if (languageCode) params.append('languageCode', languageCode);
    const query = params.toString();
    return publicApi.get(`/api/public/quiz/${quizCode}${query ? `?${query}` : ''}`, { headers: bearer(participantAuthToken) });
  },
  participantLogin: (quizCode, data) => publicApi.post(`/api/public/quiz/${quizCode}/participant-login`, data),
  participantPortalLogin: (data) => publicApi.post('/api/public/quiz/participant/login', data),
  getParticipantPortalDashboard: ({ participantName = '', participantAuthToken = '' } = {}) => {
    const params = new URLSearchParams();
    // Employees are identified by their token; guests by name.
    if (!participantAuthToken && participantName) params.append('participantName', participantName);
    params.append('languageCode', getCurrentLanguageCode());
    return publicApi.get(`/api/public/quiz/participant/dashboard?${params.toString()}`, { headers: bearer(participantAuthToken) });
  },
  getParticipantChatUsers: ({ participantAuthToken }) =>
    publicApi.get('/api/public/quiz/participant/chat/users', { headers: bearer(participantAuthToken) }),
  getParticipantChatHistory: ({ participantAuthToken, withEnterpriseId, limit = 60 }) => {
    const params = new URLSearchParams({ withEnterpriseId, limit });
    return publicApi.get(`/api/public/quiz/participant/chat/history?${params.toString()}`, { headers: bearer(participantAuthToken) });
  },
  sendParticipantChatMessage: (data) => publicApi.post('/api/public/quiz/participant/chat/send', withoutCredentials(data), {
    headers: bearer(data?.participantAuthToken),
  }),
  sendParticipantChatAttachment: (formData) => {
    const token = formData.get('participantAuthToken');
    formData.delete('participantAuthToken');
    formData.delete('participantEnterpriseId');
    return publicApi.post('/api/public/quiz/participant/chat/send-attachment', formData, {
      headers: { 'Content-Type': 'multipart/form-data', ...bearer(token) },
    });
  },
  publishParticipantTyping: (data) => publicApi.post('/api/public/quiz/participant/chat/typing', withoutCredentials(data), {
    headers: bearer(data?.participantAuthToken),
  }),
  markParticipantChatRead: (data) => publicApi.post('/api/public/quiz/participant/chat/read', withoutCredentials(data), {
    headers: bearer(data?.participantAuthToken),
  }),
  /** EventSource cannot send headers, so exchange the JWT for a one-time ticket first. */
  createParticipantChatStreamUrl: async ({ participantAuthToken }) => {
    const res = await publicApi.post('/api/public/quiz/participant/chat/stream-ticket', null, {
      headers: bearer(participantAuthToken),
    });
    const baseURL = process.env.REACT_APP_API_URL || 'http://localhost:8080';
    const params = new URLSearchParams({ ticket: res.data.ticket });
    return `${baseURL}/api/public/quiz/participant/chat/stream?${params.toString()}`;
  },
  /**
   * Opens the chat SSE stream and keeps it open: tickets are single-use, so every reconnect
   * fetches a fresh one. Returns a function that closes the stream for good.
   */
  openParticipantChatStream: ({ participantAuthToken, onEvent, onOpen, onError }) => {
    let source = null;
    let retryTimer = null;
    let closed = false;

    const connect = async () => {
      if (closed) return;
      try {
        const url = await quizPublicAPI.createParticipantChatStreamUrl({ participantAuthToken });
        if (closed) return;
        source = new EventSource(url);
        source.onopen = () => onOpen?.();
        source.addEventListener('participant-chat', (event) => onEvent?.(event));
        source.onmessage = (event) => onEvent?.(event);
        source.onerror = () => {
          onError?.();
          source?.close();
          source = null;
          if (!closed) retryTimer = setTimeout(connect, CHAT_STREAM_RETRY_MS);
        };
      } catch {
        onError?.();
        if (!closed) retryTimer = setTimeout(connect, CHAT_STREAM_RETRY_MS);
      }
    };

    connect();
    return () => {
      closed = true;
      clearTimeout(retryTimer);
      source?.close();
    };
  },
  fetchParticipantChatAttachment: async ({ messageId, participantAuthToken }) => {
    const res = await publicApi.get(`/api/public/quiz/participant/chat/attachment/${messageId}`, {
      headers: bearer(participantAuthToken),
      responseType: 'blob',
    });
    return res.data;
  },
  submitAttempt: (quizCode, data) => {
    const { participantAuthToken, ...body } = data || {};
    return publicApi.post(`/api/public/quiz/${quizCode}/attempt`, body, { headers: bearer(participantAuthToken) });
  },
  getLeaderboard: (quizCode) => publicApi.get(`/api/public/quiz/${quizCode}/leaderboard`),
};

export const referenceAPI = {
  getStacks: () => api.get('/api/reference/stacks'),
  getTopics: (stackId) => api.get(`/api/reference/topics/${stackId}`),
  getUsers: () => api.get('/api/reference/users'),
};

export const aiAPI = {
  // Returns 202 with a job; poll getGenerationJob until status is SUCCEEDED or FAILED.
  startGeneration: (data) => api.post('/api/ai/generate', data),
  getGenerationJob: (jobId) => api.get(`/api/ai/generate/${encodeURIComponent(jobId)}`),
  checkSimilarity: (data) => api.post('/api/ai/similarity-check', data),
  chat: (data) => api.post('/api/ai/chat', data),
  getChatHistory: (limit = 40, sessionId = '') => {
    const params = new URLSearchParams({ limit });
    if (sessionId) params.append('sessionId', sessionId);
    return api.get(`/api/ai/chat/history?${params.toString()}`);
  },
  getChatSessions: (limit = 5) => api.get(`/api/ai/chat/sessions?limit=${limit}`),
  getChatSuggestions: () => api.get('/api/ai/chat/suggestions'),
  streamChat: async (data, handlers = {}) => {
    const token = getAuthToken();
    const baseURL = process.env.REACT_APP_API_URL || 'http://localhost:8080';
    const response = await fetch(`${baseURL}/api/ai/chat/stream`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        ...(token ? { Authorization: `Bearer ${token}` } : {}),
      },
      body: JSON.stringify(data),
    });

    if (!response.ok || !response.body) {
      const errorText = await response.text().catch(() => '');
      throw new Error(
        errorText ||
        response.statusText ||
        `Streaming failed with status ${response.status}`
      );
    }

    const decoder = new TextDecoder();
    const reader = response.body.getReader();
    let buffer = '';
    let receivedDelta = false;

    while (true) {
      const { done, value } = await reader.read();
      if (done) {
        break;
      }

      buffer += decoder.decode(value, { stream: true });
      const events = buffer.split('\n\n');
      buffer = events.pop() || '';

      for (const rawEvent of events) {
        const lines = rawEvent.split('\n');
        const eventLine = lines.find((line) => line.startsWith('event:'));
        const eventName = eventLine ? eventLine.slice(6).trim() : 'message';
        const dataLine = lines.find((line) => line.startsWith('data:'));
        if (!dataLine) continue;

        const payloadText = dataLine.slice(5).trim();
        if (!payloadText) continue;

        let payload;
        try {
          payload = JSON.parse(payloadText);
        } catch {
          payload = { message: payloadText };
        }

        if (eventName === 'error') {
          // `partial` tells the caller whether some of the answer was already shown (and saved server-side).
          const streamError = new Error(payload?.message || 'The AI response was interrupted.');
          streamError.partial = receivedDelta;
          throw streamError;
        }
        if (payload?.done) {
          handlers.onDone?.();
        } else if (payload?.delta) {
          receivedDelta = true;
          handlers.onDelta?.(payload.delta);
        }
      }
    }
  },
};

export default api;
