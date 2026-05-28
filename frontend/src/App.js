import React from 'react';
import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom';
import { Toaster } from 'react-hot-toast';
import { AuthProvider, useAuth } from './context/AuthContext';
import Layout from './components/Layout';

import LoginPage from './pages/LoginPage';
import MyQuestionsPage from './pages/MyQuestionsPage';
import AddQuestionPage from './pages/AddQuestionPage';
import EditQuestionPage from './pages/EditQuestionPage';
import MyPendingReviewsPage from './pages/MyPendingReviewsPage';
import QuestionBankPage from './pages/QuestionBankPage';

function PrivateRoute({ children, adminOnly = false }) {
  const { user } = useAuth();
  if (!user) return <Navigate to="/login" replace />;
  if (adminOnly && user.role !== 'ADMIN') return <Navigate to="/my-questions" replace />;
  return <Layout>{children}</Layout>;
}

function RootRedirect() {
  const { user } = useAuth();
  if (!user) return <Navigate to="/login" replace />;
  return <Navigate to={user.role === 'ADMIN' ? '/admin/question-bank' : '/my-questions'} replace />;
}

export default function App() {
  return (
    <AuthProvider>
      <BrowserRouter>
        <Toaster
          position="top-right"
          toastOptions={{
            style: { fontFamily: 'var(--font)', fontSize: 14, borderRadius: 10, boxShadow: '0 8px 24px rgba(0,0,0,0.12)' },
            success: { iconTheme: { primary: '#10b981', secondary: '#fff' } },
            error: { iconTheme: { primary: '#ef4444', secondary: '#fff' } },
          }}
        />
        <Routes>
          <Route path="/login" element={<LoginPage />} />
          <Route path="/" element={<RootRedirect />} />

          {/* SME + Admin routes */}
          <Route path="/my-questions" element={<PrivateRoute><MyQuestionsPage /></PrivateRoute>} />
          <Route path="/add-question" element={<PrivateRoute><AddQuestionPage /></PrivateRoute>} />
          <Route path="/edit-question/:id" element={<PrivateRoute><EditQuestionPage /></PrivateRoute>} />
          <Route path="/my-reviews" element={<PrivateRoute><MyPendingReviewsPage /></PrivateRoute>} />

          {/* Admin only */}
          <Route path="/admin/question-bank" element={<PrivateRoute adminOnly><QuestionBankPage /></PrivateRoute>} />

          {/* Fallback */}
          <Route path="*" element={<Navigate to="/" replace />} />
        </Routes>
      </BrowserRouter>
    </AuthProvider>
  );
}
