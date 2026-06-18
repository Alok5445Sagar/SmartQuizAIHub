import axios from 'axios';

const api = axios.create({
  baseURL: process.env.REACT_APP_API_URL || 'http://localhost:8080',
  headers: { 'Content-Type': 'application/json' }
});

api.interceptors.response.use(
  res => res,
  err => {
    if (err.response?.status === 401) {
      localStorage.removeItem('token');
      localStorage.removeItem('user');
      window.location.href = '/login';
    }
    return Promise.reject(err);
  }
);

export const mcqAPI = {
  create: (data) => api.post('/api/mcq', data),
  update: (id, data) => api.put(`/api/mcq/${id}`, data),
  delete: (id) => api.delete(`/api/mcq/${id}`),
  getMyQuestions: (page = 0, size = 10, filters = {}) => {
    const params = new URLSearchParams({ page, size });
    if (filters.stackId)    params.append('stackId', filters.stackId);
    if (filters.topicId)    params.append('topicId', filters.topicId);
    if (filters.difficulty) params.append('difficulty', filters.difficulty);
    if (filters.status)     params.append('status', filters.status);
    return api.get(`/api/mcq/my-questions?${params.toString()}`);
  },
  getMyPendingReviews: (page = 0, size = 10) => api.get(`/api/mcq/my-pending-reviews?page=${page}&size=${size}`),
  getById: (id) => api.get(`/api/mcq/${id}`),
  review: (id, data) => api.post(`/api/mcq/${id}/review`, data),
  bulkUpload: (formData) => api.post('/api/mcq/bulk-upload', formData, { headers: { 'Content-Type': 'multipart/form-data' } }),
  downloadTemplate: () => api.get('/api/mcq/template', { responseType: 'blob' }),
};

export const adminAPI = {
  getQuestionBank: (page = 0, size = 10, filters = {}) => {
    const params = new URLSearchParams({ page, size });
    if (filters.stackId)    params.append('stackId', filters.stackId);
    if (filters.topicId)    params.append('topicId', filters.topicId);
    if (filters.difficulty) params.append('difficulty', filters.difficulty);
    if (filters.status)     params.append('status', filters.status);
    if (filters.search)     params.append('search', filters.search);
    return api.get(`/api/admin/question-bank?${params.toString()}`);
  },
  assignReviewer: (questionId, reviewerEnterpriseId) => api.post(`/api/admin/assign-reviewer/${questionId}`, { reviewerEnterpriseId }),
  editQuestion: (id, data) => api.put(`/api/admin/edit-question/${id}`, data),
  getReviewers: (stackId, excludeEnterpriseId) => api.get(`/api/admin/reviewers/${stackId}?excludeEnterpriseId=${excludeEnterpriseId}`),
};

export const referenceAPI = {
  getStacks: () => api.get('/api/reference/stacks'),
  getTopics: (stackId) => api.get(`/api/reference/topics/${stackId}`),
  getUsers: () => api.get('/api/reference/users'),
};

export const aiAPI = {
  generateQuestions: (data) => api.post('/api/ai/generate', data),
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
    const token = localStorage.getItem('token');
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
        const dataLine = lines.find((line) => line.startsWith('data:'));
        if (!dataLine) continue;

        const payloadText = dataLine.slice(5).trim();
        if (!payloadText) continue;

        try {
          const payload = JSON.parse(payloadText);
          if (payload.done) {
            handlers.onDone?.();
          } else if (payload.delta) {
            handlers.onDelta?.(payload.delta);
          }
        } catch {
          // Ignore malformed event payloads.
        }
      }
    }
  },
};

export default api;
