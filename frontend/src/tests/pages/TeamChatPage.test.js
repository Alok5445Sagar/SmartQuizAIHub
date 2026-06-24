import React from 'react';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import TeamChatPage from '../../pages/TeamChatPage';
import { quizPublicAPI } from '../../services/api';

jest.mock('../../context/AuthContext', () => ({
  useAuth: () => ({
    user: {
      enterpriseId: 'admin',
      role: 'ADMIN',
      fullName: 'Admin User',
    },
  }),
}));

jest.mock('../../context/LanguageContext', () => ({
  useLanguage: () => ({
    t: (key) => key,
  }),
}));

jest.mock('../../services/authSession', () => ({
  getAuthToken: () => 'token-123',
}));

jest.mock('../../services/api', () => ({
  quizPublicAPI: {
    getParticipantChatUsers: jest.fn(),
    getParticipantChatHistory: jest.fn(),
    getParticipantChatStreamUrl: jest.fn(),
    publishParticipantTyping: jest.fn(),
    markParticipantChatRead: jest.fn(),
    sendParticipantChatMessage: jest.fn(),
  },
}));

describe('TeamChatPage mobile conversation switch', () => {
  const originalInnerWidth = window.innerWidth;
  const originalEventSource = global.EventSource;
  const originalScrollTo = window.HTMLElement.prototype.scrollTo;

  beforeEach(() => {
    jest.clearAllMocks();

    Object.defineProperty(window, 'innerWidth', {
      configurable: true,
      writable: true,
      value: 800,
    });

    global.EventSource = jest.fn().mockImplementation(() => ({
      addEventListener: jest.fn(),
      close: jest.fn(),
    }));

    window.HTMLElement.prototype.scrollTo = jest.fn();

    quizPublicAPI.getParticipantChatStreamUrl.mockReturnValue('http://localhost:8080/sse');
    quizPublicAPI.getParticipantChatUsers.mockResolvedValue({
      data: {
        users: [
          {
            enterpriseId: 'sme.one',
            fullName: 'SME One',
            role: 'SME',
            online: true,
          },
          {
            enterpriseId: 'sme.two',
            fullName: 'SME Two',
            role: 'SME',
            online: true,
          },
        ],
      },
    });

    quizPublicAPI.getParticipantChatHistory.mockImplementation(({ withEnterpriseId }) =>
      Promise.resolve({
        data: {
          messages: [
            {
              messageId: `${withEnterpriseId}-1`,
              senderEnterpriseId: withEnterpriseId,
              recipientEnterpriseId: 'admin',
              message: `hello from ${withEnterpriseId}`,
              createdAt: '2026-06-23T10:00:00Z',
            },
          ],
        },
      })
    );

    quizPublicAPI.publishParticipantTyping.mockResolvedValue({});
    quizPublicAPI.markParticipantChatRead.mockResolvedValue({});
    quizPublicAPI.sendParticipantChatMessage.mockResolvedValue({ data: {} });
  });

  afterEach(() => {
    Object.defineProperty(window, 'innerWidth', {
      configurable: true,
      writable: true,
      value: originalInnerWidth,
    });

    global.EventSource = originalEventSource;
    window.HTMLElement.prototype.scrollTo = originalScrollTo;
  });

  test('opens selected user thread on mobile and keeps deterministic selectors available', async () => {
    render(<TeamChatPage />);

    const backButton = await screen.findByTestId('team-chat-mobile-back');
    fireEvent.click(backButton);

    const secondUserButton = await screen.findByTestId('team-chat-user-sme.two');
    fireEvent.click(secondUserButton);

    await waitFor(() => {
      expect(screen.getByTestId('team-chat-input')).toBeInTheDocument();
    });

    await waitFor(() => {
      const hasSecondConversationFetch = quizPublicAPI.getParticipantChatHistory.mock.calls.some(
        ([args]) => args?.withEnterpriseId === 'sme.two'
      );
      expect(hasSecondConversationFetch).toBe(true);
    });
  });
});
