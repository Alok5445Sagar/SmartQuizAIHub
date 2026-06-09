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
};

export default api;
