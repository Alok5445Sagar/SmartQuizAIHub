import React, { useEffect, useMemo, useRef, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import toast from 'react-hot-toast';
import { quizPublicAPI } from '../services/api';
import ChatAttachment from '../components/ChatAttachment';
import { clearParticipantSession, getParticipantSession, setParticipantSession } from '../services/participantSession';
import { useLanguage } from '../context/LanguageContext';
import useAutoRefresh from '../hooks/useAutoRefresh';

const formatPercent = (score, total) => {
  const safeScore = Number(score) || 0;
  const safeTotal = Number(total) || 0;
  if (safeTotal <= 0) return '0.00%';
  return `${((safeScore * 100) / safeTotal).toFixed(2)}%`;
};

const formatTime = (seconds) => {
  const safe = Number(seconds) || 0;
  const mins = Math.floor(safe / 60);
  const secs = safe % 60;
  return `${mins}:${String(secs).padStart(2, '0')}`;
};

const formatChatTime = (value) => {
  if (!value) return '';
  return new Date(value).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
};

const toSortableChatTime = (value) => {
  if (!value) return 0;
  const date = new Date(value);
  const timestamp = date.getTime();
  return Number.isNaN(timestamp) ? 0 : timestamp;
};

const formatChatDate = (value) => {
  if (!value) return '';
  const date = new Date(value);
  const now = new Date();
  const sameDay = date.toDateString() === now.toDateString();
  if (sameDay) return 'Today';

  const yesterday = new Date(now);
  yesterday.setDate(now.getDate() - 1);
  if (date.toDateString() === yesterday.toDateString()) return 'Yesterday';

  return date.toLocaleDateString();
};

const toNumericMessageId = (value) => {
  const numeric = Number(value);
  return Number.isFinite(numeric) ? numeric : null;
};

const emojiSet = ['😀', '😁', '😂', '😊', '😉', '😍', '🤝', '👍', '🙌', '🔥', '🎯', '✅', '👏', '💡', '🙏', '🚀'];
const CHAT_ACTIVE_SYNC_INTERVAL_MS = 3000;
const CHAT_USERS_SYNC_INTERVAL_MS = 3000;

const panelStyle = {
  padding: 0,
  border: '1px solid var(--border)',
  borderRadius: 14,
  overflow: 'hidden',
  boxShadow: '0 10px 24px rgba(15, 23, 42, 0.04)',
};

const metricTone = {
  live: { bg: '#eff6ff', fg: '#1e40af', border: '#bfdbfe' },
  attempted: { bg: '#ecfeff', fg: '#0f766e', border: '#99f6e4' },
  suggestions: { bg: '#fefce8', fg: '#a16207', border: '#fde68a' },
};

export default function ParticipantPortalPage() {
  const navigate = useNavigate();
  const { language, setLanguage, t, availableLanguages } = useLanguage();
  const [mode, setMode] = useState('employee');
  const [enterpriseId, setEnterpriseId] = useState('');
  const [password, setPassword] = useState('');
  const [guestName, setGuestName] = useState('');
  const [profile, setProfile] = useState(null);
  const [dashboard, setDashboard] = useState(null);
  const [hasLoadedInitialDashboard, setHasLoadedInitialDashboard] = useState(false);
  const [loading, setLoading] = useState(false);
  const [loggingIn, setLoggingIn] = useState(false);
  const [lastUpdatedAt, setLastUpdatedAt] = useState(null);
  const [portalNotice, setPortalNotice] = useState('');
  const [chatUsers, setChatUsers] = useState([]);
  const [chatSearch, setChatSearch] = useState('');
  const [selectedChatUserId, setSelectedChatUserId] = useState('');
  const [chatMessagesByUser, setChatMessagesByUser] = useState({});
  const [chatUnreadByUser, setChatUnreadByUser] = useState({});
  const [chatText, setChatText] = useState('');
  const [chatLoading, setChatLoading] = useState(false);
  const [chatSending, setChatSending] = useState(false);
  const [chatUploading, setChatUploading] = useState(false);
  const [chatStreamConnected, setChatStreamConnected] = useState(false);
  const [typingByUser, setTypingByUser] = useState({});
  const [showEmojiPicker, setShowEmojiPicker] = useState(false);
  const [isMobileChatThreadOpen, setIsMobileChatThreadOpen] = useState(false);
  const [isMobileViewport, setIsMobileViewport] = useState(() => (typeof window !== 'undefined' ? window.innerWidth <= 768 : false));
  const typingStopTimerRef = useRef(null);
  const chatThreadRef = useRef(null);
  const attachmentInputRef = useRef(null);

  const participantIdentity = useMemo(() => {
    if (!profile) return null;
    return {
      participantEnterpriseId: profile.enterpriseId || '',
      participantName: profile.fullName || guestName,
      participantType: profile.participantType,
      authToken: profile.authToken || '',
    };
  }, [profile, guestName]);

  const canUseParticipantChat = useMemo(() => {
    return Boolean(
      profile?.participantType === 'EMPLOYEE' &&
      participantIdentity?.participantEnterpriseId &&
      participantIdentity?.authToken
    );
  }, [profile, participantIdentity]);

  const attemptedQuizCodes = useMemo(() => {
    const codes = (dashboard?.previousAttempts || [])
      .map((attempt) => attempt?.quizCode)
      .filter(Boolean);
    return new Set(codes);
  }, [dashboard]);

  const selectedChatUser = useMemo(
    () => chatUsers.find((item) => item.enterpriseId === selectedChatUserId) || null,
    [chatUsers, selectedChatUserId]
  );

  const activeChatMessages = useMemo(
    () => chatMessagesByUser[selectedChatUserId] || [],
    [chatMessagesByUser, selectedChatUserId]
  );

  const conversationPreviewByUser = useMemo(() => {
    const map = {};
    Object.keys(chatMessagesByUser).forEach((enterpriseId) => {
      const messages = chatMessagesByUser[enterpriseId] || [];
      if (!messages.length) return;
      map[enterpriseId] = messages[messages.length - 1];
    });
    return map;
  }, [chatMessagesByUser]);

  const filteredChatUsers = useMemo(() => {
    const needle = chatSearch.trim().toLowerCase();
    const items = [...chatUsers]
      .sort((a, b) => {
        const aLast = conversationPreviewByUser[a.enterpriseId];
        const bLast = conversationPreviewByUser[b.enterpriseId];
        const aTime = toSortableChatTime(aLast?.createdAt || aLast?.deliveredAt || aLast?.readAt);
        const bTime = toSortableChatTime(bLast?.createdAt || bLast?.deliveredAt || bLast?.readAt);

        if (aTime !== bTime) return bTime - aTime;
        if (a.online !== b.online) return a.online ? -1 : 1;
        return (a.fullName || '').localeCompare(b.fullName || '');
      });

    if (!needle) return items;

    return items.filter((user) => {
      const haystack = `${user.fullName || ''} ${user.enterpriseId || ''} ${user.role || ''}`.toLowerCase();
      return haystack.includes(needle);
    });
  }, [chatUsers, chatSearch, conversationPreviewByUser]);

  const groupedActiveMessages = useMemo(() => {
    const groups = [];
    let previousDate = '';

    activeChatMessages.forEach((message) => {
      const dateLabel = formatChatDate(message.createdAt);
      if (dateLabel !== previousDate) {
        groups.push({ type: 'date', id: `date-${dateLabel}-${message.messageId}`, label: dateLabel });
        previousDate = dateLabel;
      }
      groups.push({ type: 'message', id: `msg-${message.messageId}`, message });
    });

    return groups;
  }, [activeChatMessages]);

  const participantTypeLabel = useMemo(() => {
    const type = profile?.participantType;
    if (type === 'EMPLOYEE') return t('employee');
    if (type === 'GUEST') return t('guest');
    return type || '';
  }, [profile, t]);

  const showDashboardSkeleton = Boolean(profile) && !dashboard && !hasLoadedInitialDashboard;

  const loadDashboard = async (identity, { preserveScroll = false, showGlobalLoader = true } = {}) => {
    if (!identity) return;
    const previousScrollY = preserveScroll ? window.scrollY : null;
    if (showGlobalLoader) {
      setLoading(true);
    }
    try {
      const res = await quizPublicAPI.getParticipantPortalDashboard({
        participantAuthToken: identity.authToken,
        participantName: identity.participantName,
      });
      setDashboard(res.data);
      setPortalNotice('');
      setLastUpdatedAt(new Date());

      // Keep user anchored on current viewport position during background refreshes.
      if (previousScrollY !== null) {
        requestAnimationFrame(() => {
          window.scrollTo({ top: previousScrollY, left: 0, behavior: 'auto' });
        });
      }
    } catch (err) {
      toast.error((err.response?.data?.detail || err.response?.data?.message) || t('failedToLoadParticipantDashboard'));
    } finally {
      setHasLoadedInitialDashboard(true);
      if (showGlobalLoader) {
        setLoading(false);
      }
    }
  };

  useEffect(() => {
    try {
      const saved = getParticipantSession();
      if (!saved) return;
      const parsed = JSON.parse(saved);
      if (!parsed?.fullName) return;
      setProfile(parsed);
      if (parsed.participantType === 'GUEST') {
        setMode('guest');
        setGuestName(parsed.fullName);
      } else {
        setMode('employee');
        setEnterpriseId(parsed.enterpriseId || '');
      }
      if (parsed.preferredLanguage) {
        setLanguage(parsed.preferredLanguage);
      }
    } catch {
      // Ignore malformed session.
    }
  }, []);

  useEffect(() => {
    if (!participantIdentity) return;
    setHasLoadedInitialDashboard(false);
    // Keep portal responsive on login by loading dashboard in background.
    loadDashboard(participantIdentity, { showGlobalLoader: false });
  }, [participantIdentity, language]);

  useAutoRefresh(
    () => loadDashboard(participantIdentity, { preserveScroll: true, showGlobalLoader: false }),
    { enabled: Boolean(participantIdentity) }
  );

  const loginEmployee = async () => {
    if (!enterpriseId || !password) {
      toast.error(t('enterEmployeeIdPassword'));
      return;
    }
    setLoggingIn(true);
    try {
      const res = await quizPublicAPI.participantPortalLogin({ enterpriseId, password });
      setProfile(res.data);
      setDashboard(null);
      setHasLoadedInitialDashboard(false);
      setParticipantSession(JSON.stringify(res.data));
      if (res.data?.preferredLanguage) {
        setLanguage(res.data.preferredLanguage);
      }
      toast.success(`${t('welcomeBack')} ${res.data.fullName}`);
    } catch (err) {
      toast.error((err.response?.data?.detail || err.response?.data?.message) || t('loginFailed'));
    } finally {
      setLoggingIn(false);
    }
  };

  const loginGuest = async () => {
    if (!guestName.trim()) {
      toast.error(t('enterYourName'));
      return;
    }
    setLoggingIn(true);
    try {
      const res = await quizPublicAPI.participantPortalLogin({ participantName: guestName.trim() });
      setProfile(res.data);
      setDashboard(null);
      setHasLoadedInitialDashboard(false);
      setParticipantSession(JSON.stringify(res.data));
      toast.success(`${t('welcomeBack')} ${res.data.fullName}`);
    } catch (err) {
      toast.error((err.response?.data?.detail || err.response?.data?.message) || t('guestLoginFailed'));
    } finally {
      setLoggingIn(false);
    }
  };

  const logout = () => {
    clearParticipantSession();
    setProfile(null);
    setDashboard(null);
    setHasLoadedInitialDashboard(false);
    setPassword('');
  };

  const startQuiz = (quizCode) => {
    if (attemptedQuizCodes.has(quizCode)) {
      const message = t('alreadyAttemptedMessage');
      setPortalNotice(message);
      toast.error(message);
      return;
    }

    if (!participantIdentity) {
      navigate(`/quiz/${quizCode}`);
      return;
    }
    setPortalNotice('');
    navigate(`/quiz/${quizCode}`, { state: { participantProfile: participantIdentity } });
  };

  const viewAttemptLeaderboard = (attemptQuizCode) => {
    if (!attemptQuizCode) return;
    navigate(`/quiz/${attemptQuizCode}`, {
      state: {
        participantProfile: participantIdentity,
      },
    });
  };

  const refreshNow = () => {
    if (!participantIdentity) return;
    loadDashboard(participantIdentity, { preserveScroll: true, showGlobalLoader: false });
  };

  const applyChatUsers = (users) => {
    const safeUsers = Array.isArray(users) ? users : [];
    setChatUsers(safeUsers);

    if (!safeUsers.length) {
      setSelectedChatUserId('');
      return;
    }

    setSelectedChatUserId((prev) => {
      if (prev && safeUsers.some((u) => u.enterpriseId === prev)) {
        return prev;
      }
      return safeUsers[0].enterpriseId;
    });
  };

  const loadChatUsers = async () => {
    if (!canUseParticipantChat || !participantIdentity) return;
    try {
      const res = await quizPublicAPI.getParticipantChatUsers({
        participantEnterpriseId: participantIdentity.participantEnterpriseId,
        participantAuthToken: participantIdentity.authToken,
      });
      applyChatUsers(res.data?.users || []);
    } catch (err) {
      toast.error((err.response?.data?.detail || err.response?.data?.message) || t('failedToLoadChatUsers'));
    }
  };

  const loadChatHistory = async (otherEnterpriseId, { background = false } = {}) => {
    if (!canUseParticipantChat || !participantIdentity || !otherEnterpriseId) return;
    if (chatMessagesByUser[otherEnterpriseId] && !background) {
      setChatLoading(false);
    }
    if (!background) {
      setChatLoading(true);
    }
    try {
      const res = await quizPublicAPI.getParticipantChatHistory({
        participantEnterpriseId: participantIdentity.participantEnterpriseId,
        participantAuthToken: participantIdentity.authToken,
        withEnterpriseId: otherEnterpriseId,
        limit: 80,
      });
      const nextMessages = res.data?.messages || [];
      setChatMessagesByUser((prev) => {
        const existing = prev[otherEnterpriseId] || [];
        const pendingMessages = existing.filter((msg) => msg.pending && String(msg.messageId).startsWith('temp-'));
        const merged = [...nextMessages];

        pendingMessages.forEach((pending) => {
          const alreadyPresent = merged.some((item) => item.messageId === pending.messageId);
          if (!alreadyPresent) {
            merged.push(pending);
          }
        });

        return { ...prev, [otherEnterpriseId]: merged };
      });
    } catch (err) {
      if (!background) {
        toast.error((err.response?.data?.detail || err.response?.data?.message) || t('failedToLoadChatHistory'));
      }
    } finally {
      if (!background) {
        setChatLoading(false);
      }
    }
  };

  const appendMessageForUser = (otherEnterpriseId, incoming) => {
    setChatMessagesByUser((prev) => {
      const existing = prev[otherEnterpriseId] || [];
      if (existing.some((message) => message.messageId === incoming.messageId)) {
        return prev;
      }
      return { ...prev, [otherEnterpriseId]: [...existing, incoming] };
    });
  };

  const sendTypingEvent = async (isTyping) => {
    if (!canUseParticipantChat || !participantIdentity || !selectedChatUserId) return;
    try {
      await quizPublicAPI.publishParticipantTyping({
        participantEnterpriseId: participantIdentity.participantEnterpriseId,
        participantAuthToken: participantIdentity.authToken,
        withEnterpriseId: selectedChatUserId,
        typing: Boolean(isTyping),
      });
    } catch {
      // Typing signals are best-effort.
    }
  };

  const markConversationRead = async () => {
    if (!canUseParticipantChat || !participantIdentity || !selectedChatUserId) return;
    const lastMessage = activeChatMessages[activeChatMessages.length - 1];
    const numericLastSeenId = Number.isFinite(Number(lastMessage?.messageId)) ? Number(lastMessage?.messageId) : null;
    try {
      await quizPublicAPI.markParticipantChatRead({
        participantEnterpriseId: participantIdentity.participantEnterpriseId,
        participantAuthToken: participantIdentity.authToken,
        withEnterpriseId: selectedChatUserId,
        lastSeenMessageId: numericLastSeenId,
      });
    } catch {
      // Read events are best-effort.
    }
  };

  const sendChatMessage = async () => {
    if (!canUseParticipantChat || !participantIdentity || !selectedChatUserId) return;
    const message = chatText.trim();
    if (!message) return;

    const tempId = `temp-${Date.now()}-${Math.floor(Math.random() * 1000)}`;
    const optimisticMessage = {
      messageId: tempId,
      senderEnterpriseId: participantIdentity.participantEnterpriseId,
      senderName: profile?.fullName || participantIdentity.participantEnterpriseId,
      recipientEnterpriseId: selectedChatUserId,
      recipientName: selectedChatUser?.fullName || selectedChatUserId,
      message,
      createdAt: new Date().toISOString(),
      pending: true,
    };

    appendMessageForUser(selectedChatUserId, optimisticMessage);
    setChatText('');
    setChatSending(true);
    try {
      const res = await quizPublicAPI.sendParticipantChatMessage({
        participantEnterpriseId: participantIdentity.participantEnterpriseId,
        participantAuthToken: participantIdentity.authToken,
        recipientEnterpriseId: selectedChatUserId,
        message,
      });

      const savedMessage = res.data;
      if (savedMessage?.messageId) {
        setChatMessagesByUser((prev) => {
          const existing = prev[selectedChatUserId] || [];
          const replaced = existing.map((item) => (
            item.messageId === tempId
              ? { ...savedMessage, deliveryStatus: savedMessage.deliveryStatus || 'SENT' }
              : item
          ));
          const alreadyHasSaved = replaced.some((item) => item.messageId === savedMessage.messageId);
          return {
            ...prev,
            [selectedChatUserId]: alreadyHasSaved ? replaced : [...replaced, { ...savedMessage, deliveryStatus: savedMessage.deliveryStatus || 'SENT' }],
          };
        });
      }
      loadChatHistory(selectedChatUserId, { background: true });
    } catch (err) {
      setChatMessagesByUser((prev) => {
        const existing = prev[selectedChatUserId] || [];
        return { ...prev, [selectedChatUserId]: existing.filter((item) => item.messageId !== tempId) };
      });
      setChatText(message);
      toast.error((err.response?.data?.detail || err.response?.data?.message) || t('failedToSendMessage'));
    } finally {
      setChatSending(false);
      sendTypingEvent(false);
    }
  };

  const sendChatAttachment = async (file) => {
    if (!file || !canUseParticipantChat || !participantIdentity || !selectedChatUserId) return;

    const caption = chatText.trim();
    setChatUploading(true);
    try {
      const formData = new FormData();
      formData.append('participantEnterpriseId', participantIdentity.participantEnterpriseId);
      formData.append('participantAuthToken', participantIdentity.authToken);
      formData.append('recipientEnterpriseId', selectedChatUserId);
      formData.append('message', caption);
      formData.append('file', file);

      const res = await quizPublicAPI.sendParticipantChatAttachment(formData);
      const savedMessage = res.data;

      if (savedMessage?.messageId) {
        appendMessageForUser(selectedChatUserId, { ...savedMessage, deliveryStatus: savedMessage.deliveryStatus || 'SENT' });
      }
      loadChatHistory(selectedChatUserId, { background: true });

      if (caption) {
        setChatText('');
      }
      toast.success('Attachment sent');
    } catch (err) {
      toast.error((err.response?.data?.detail || err.response?.data?.message) || 'Failed to upload attachment');
    } finally {
      setChatUploading(false);
      if (attachmentInputRef.current) {
        attachmentInputRef.current.value = '';
      }
    }
  };

  useEffect(() => {
    if (!canUseParticipantChat) {
      setChatUsers([]);
      setSelectedChatUserId('');
      setChatMessagesByUser({});
      setChatUnreadByUser({});
      setChatText('');
      setChatStreamConnected(false);
      setTypingByUser({});
      setShowEmojiPicker(false);
      return;
    }
    loadChatUsers();
  }, [canUseParticipantChat, participantIdentity?.participantEnterpriseId]);

  useEffect(() => {
    if (!canUseParticipantChat || !selectedChatUserId) {
      return;
    }

    setChatUnreadByUser((prev) => {
      if (!prev[selectedChatUserId]) return prev;
      return { ...prev, [selectedChatUserId]: 0 };
    });

    loadChatHistory(selectedChatUserId);
    if (isMobileViewport) {
      setIsMobileChatThreadOpen(true);
    }
  }, [canUseParticipantChat, selectedChatUserId]);

  // Fallback only: while the live stream is connected, new messages arrive as stream events.
  useEffect(() => {
    if (!canUseParticipantChat || !selectedChatUserId || chatStreamConnected) return;

    const syncConversation = () => {
      if (document.visibilityState === 'hidden') return;
      loadChatHistory(selectedChatUserId, { background: true });
    };

    const intervalId = setInterval(syncConversation, CHAT_ACTIVE_SYNC_INTERVAL_MS);
    return () => clearInterval(intervalId);
  }, [canUseParticipantChat, selectedChatUserId, chatStreamConnected, participantIdentity?.participantEnterpriseId, participantIdentity?.authToken]);

  useEffect(() => {
    const onResize = () => {
      const mobile = window.innerWidth <= 768;
      setIsMobileViewport(mobile);
      if (!mobile) {
        setIsMobileChatThreadOpen(false);
      }
    };
    window.addEventListener('resize', onResize);
    return () => window.removeEventListener('resize', onResize);
  }, []);

  useEffect(() => {
    if (!chatThreadRef.current) return;
    chatThreadRef.current.scrollTo({ top: chatThreadRef.current.scrollHeight, behavior: 'smooth' });
    markConversationRead();
  }, [activeChatMessages.length, selectedChatUserId, chatSending]);

  useEffect(() => {
    if (!canUseParticipantChat || !participantIdentity) return;

    

    const handleStreamEvent = (event) => {
      if (!event?.data) return;
      try {
        const payload = JSON.parse(event.data);
        if (payload?.eventType === 'presence' && Array.isArray(payload?.users)) {
          applyChatUsers(payload.users);
          return;
        }

        if (payload?.eventType === 'message' && payload?.message) {
          const msg = payload.message;
          const selfId = participantIdentity.participantEnterpriseId;
          const otherId = msg.senderEnterpriseId === selfId ? msg.recipientEnterpriseId : msg.senderEnterpriseId;

          appendMessageForUser(otherId, msg);

          if (selectedChatUserId !== otherId && msg.senderEnterpriseId !== selfId) {
            setChatUnreadByUser((prev) => ({
              ...prev,
              [otherId]: (prev[otherId] || 0) + 1,
            }));
          }
        }

        if (payload?.eventType === 'typing' && payload?.typingEnterpriseId) {
          const typingUserId = payload.typingEnterpriseId;
          if (typingUserId !== participantIdentity.participantEnterpriseId) {
            setTypingByUser((prev) => ({ ...prev, [typingUserId]: Boolean(payload.typing) }));
          }
        }

        if (payload?.eventType === 'message-status' && payload?.withEnterpriseId && payload?.messageStatus) {
          const withUser = payload.withEnterpriseId;
          setChatMessagesByUser((prev) => {
            const existing = prev[withUser] || [];
            if (!existing.length) return prev;

            const uptoMessageId = toNumericMessageId(payload.messageId);

            const updated = existing.map((item) => {
              if (item.senderEnterpriseId !== participantIdentity.participantEnterpriseId) return item;
              const itemMessageId = toNumericMessageId(item.messageId);
              if (uptoMessageId !== null && itemMessageId !== null && itemMessageId > uptoMessageId) return item;

              const nextStatus = payload.messageStatus === 'READ'
                ? 'READ'
                : (item.deliveryStatus === 'READ' ? 'READ' : 'DELIVERED');

              return {
                ...item,
                deliveryStatus: nextStatus,
                deliveredAt: payload.deliveredAt || item.deliveredAt,
                readAt: payload.readAt || item.readAt,
              };
            });

            return { ...prev, [withUser]: updated };
          });
        }
      } catch {
        // Ignore malformed stream payloads.
      }
    };

    // Tickets are single-use; the helper fetches a fresh one on every (re)connect.
    const closeStream = quizPublicAPI.openParticipantChatStream({
      participantAuthToken: participantIdentity.authToken,
      onEvent: handleStreamEvent,
      onOpen: () => setChatStreamConnected(true),
      onError: () => setChatStreamConnected(false),
    });

    return () => {
      setChatStreamConnected(false);
      closeStream();
    };
  }, [canUseParticipantChat, participantIdentity?.participantEnterpriseId, participantIdentity?.authToken, selectedChatUserId]);

  useEffect(() => {
    if (!canUseParticipantChat || chatStreamConnected) return;

    const syncUsers = () => {
      if (document.visibilityState === 'hidden') return;
      loadChatUsers();
    };

    syncUsers();
    const intervalId = setInterval(syncUsers, CHAT_USERS_SYNC_INTERVAL_MS);
    return () => clearInterval(intervalId);
  }, [canUseParticipantChat, chatStreamConnected, participantIdentity?.participantEnterpriseId, participantIdentity?.authToken]);

  if (!profile) {
    return (
      <div style={{ maxWidth: 920, margin: '28px auto', padding: '0 16px' }}>
        <div className="card" style={{ marginBottom: 16, background: 'linear-gradient(135deg, #0f172a 0%, #1e3a8a 55%, #0284c7 100%)', color: '#fff', border: 'none' }}>
          <h1 className="page-title" style={{ marginBottom: 8, color: '#fff' }}>{t('participantPortalTitle')}</h1>
          <p className="page-subtitle" style={{ color: 'rgba(255,255,255,0.82)' }}>
            {t('participantPortalSubtitle')}
          </p>
        </div>

        <div className="card" style={{ border: '1px solid #dbeafe' }}>
          <div style={{ marginBottom: 12, maxWidth: 260 }}>
            <label className="form-label">{t('language')}</label>
            <select
              className="form-select"
              value={language}
              onChange={(e) => setLanguage(e.target.value)}
            >
              {availableLanguages.map((lang) => (
                <option key={lang.code} value={lang.code}>{lang.label}</option>
              ))}
            </select>
          </div>

          <div className="mobile-action-row" style={{ display: 'flex', gap: 8, marginBottom: 14, flexWrap: 'wrap' }}>
            <button className={`btn btn-sm ${mode === 'employee' ? 'btn-primary' : 'btn-secondary'}`} onClick={() => setMode('employee')}>{t('employeeLogin')}</button>
            <button className={`btn btn-sm ${mode === 'guest' ? 'btn-primary' : 'btn-secondary'}`} onClick={() => setMode('guest')}>{t('guestLogin')}</button>
          </div>

          {mode === 'employee' ? (
            <div className="grid-2" style={{ alignItems: 'end' }}>
              <div className="form-group">
                <label className="form-label">{t('enterpriseId')}</label>
                <input className="form-input" value={enterpriseId} onChange={(e) => setEnterpriseId(e.target.value)} placeholder={t('enterpriseIdPlaceholder')} />
              </div>
              <div className="form-group">
                <label className="form-label">{t('password')}</label>
                <input type="password" className="form-input" value={password} onChange={(e) => setPassword(e.target.value)} placeholder={t('enterPassword')} />
              </div>
              <div>
                <button className="btn btn-primary" onClick={loginEmployee} disabled={loggingIn}>{loggingIn ? t('signingIn') : t('enterPortal')}</button>
              </div>
            </div>
          ) : (
            <div className="grid-2" style={{ alignItems: 'end' }}>
              <div className="form-group">
                <label className="form-label">{t('yourName')}</label>
                <input className="form-input" value={guestName} onChange={(e) => setGuestName(e.target.value)} placeholder={t('enterYourName')} />
              </div>
              <div>
                <button className="btn btn-primary" onClick={loginGuest} disabled={loggingIn}>{loggingIn ? t('continuing') : t('enterPortal')}</button>
              </div>
            </div>
          )}
        </div>
      </div>
    );
  }

  return (
    <div style={{ maxWidth: 1100, margin: '28px auto', padding: '0 16px 36px' }}>
      <div className="card" style={{ marginBottom: 16, background: 'linear-gradient(135deg, #0f172a 0%, #1e293b 60%, #334155 100%)', color: '#fff', border: 'none', display: 'flex', justifyContent: 'space-between', alignItems: 'center', flexWrap: 'wrap', gap: 10 }}>
        <div>
          <h2 style={{ marginBottom: 4, color: '#fff' }}>{t('welcome')}, {profile.fullName}</h2>
          <p style={{ color: 'rgba(255,255,255,0.75)', margin: 0 }}>
            {t('participantType')}: {participantTypeLabel}
            {lastUpdatedAt ? ` | ${t('lastRefreshed')}: ${lastUpdatedAt.toLocaleTimeString()}` : ''}
          </p>
        </div>
        <div className="mobile-action-row" style={{ display: 'flex', gap: 8 }}>
          <select
            value={language}
            onChange={(e) => setLanguage(e.target.value)}
            style={{ borderRadius: 8, border: '1px solid #475569', background: '#0f172a', color: '#fff', padding: '8px 10px' }}
            aria-label={t('language')}
          >
            {availableLanguages.map((lang) => (
              <option key={lang.code} value={lang.code}>{lang.label}</option>
            ))}
          </select>
          <button className="btn btn-secondary" onClick={refreshNow} disabled={loading}>{loading ? t('refreshing') : t('refreshNow')}</button>
          <button className="btn btn-secondary" onClick={logout}>{t('signOut')}</button>
        </div>
      </div>

      <>
          {loading && (
            <div className="alert alert-info" style={{ marginBottom: 12 }}>
              {t('refreshing')}
            </div>
          )}
          {portalNotice && (
            <div className="alert alert-info" style={{ marginBottom: 16 }}>
              {portalNotice}
            </div>
          )}

          {showDashboardSkeleton ? (
            <>
              <div className="grid-3" style={{ marginBottom: 16 }}>
                {[1, 2, 3].map((index) => (
                  <div key={`metric-skeleton-${index}`} className="card" style={{ padding: 14, border: '1px solid var(--border)', background: '#f8fafc' }}>
                    <div style={{ height: 10, width: '50%', background: '#e2e8f0', borderRadius: 999, marginBottom: 12 }} />
                    <div style={{ height: 28, width: '35%', background: '#cbd5e1', borderRadius: 8, marginBottom: 10 }} />
                    <div style={{ height: 10, width: '65%', background: '#e2e8f0', borderRadius: 999 }} />
                  </div>
                ))}
              </div>

              <div className="card" style={{ ...panelStyle, marginBottom: 16, padding: 16 }}>
                {[1, 2, 3].map((row) => (
                  <div key={`live-skeleton-row-${row}`} style={{ height: 16, background: '#e2e8f0', borderRadius: 8, marginBottom: row === 3 ? 0 : 12 }} />
                ))}
              </div>

              <div className="grid-2">
                <div className="card" style={{ ...panelStyle, padding: 16 }}>
                  {[1, 2, 3, 4].map((row) => (
                    <div key={`attempt-skeleton-row-${row}`} style={{ height: 14, background: '#e2e8f0', borderRadius: 8, marginBottom: row === 4 ? 0 : 10 }} />
                  ))}
                </div>
                <div className="card" style={{ ...panelStyle, padding: 16 }}>
                  {[1, 2, 3].map((row) => (
                    <div key={`suggestion-skeleton-row-${row}`} style={{ height: 34, background: '#e2e8f0', borderRadius: 8, marginBottom: row === 3 ? 0 : 10 }} />
                  ))}
                </div>
              </div>
            </>
          ) : (
            <>

          <div className="grid-3" style={{ marginBottom: 16 }}>
            <div className="card" style={{ padding: 14, border: `1px solid ${metricTone.live.border}`, background: metricTone.live.bg }}>
              <div style={{ fontSize: 12, color: metricTone.live.fg }}>{t('liveQuizzes')}</div>
              <div style={{ fontSize: 24, fontWeight: 800, color: metricTone.live.fg }}>{dashboard?.liveQuizCount || 0}</div>
              <div style={{ fontSize: 12, color: metricTone.live.fg }}>{t('availableRightNow')}</div>
            </div>
            <div className="card" style={{ padding: 14, border: `1px solid ${metricTone.attempted.border}`, background: metricTone.attempted.bg }}>
              <div style={{ fontSize: 12, color: metricTone.attempted.fg }}>{t('attemptedQuizzes')}</div>
              <div style={{ fontSize: 24, fontWeight: 800, color: metricTone.attempted.fg }}>{dashboard?.attemptedQuizCount || 0}</div>
              <div style={{ fontSize: 12, color: metricTone.attempted.fg }}>{t('yourHistoricalAttempts')}</div>
            </div>
            <div className="card" style={{ padding: 14, border: `1px solid ${metricTone.suggestions.border}`, background: metricTone.suggestions.bg }}>
              <div style={{ fontSize: 12, color: metricTone.suggestions.fg }}>{t('suggestions')}</div>
              <div style={{ fontSize: 24, fontWeight: 800, color: metricTone.suggestions.fg }}>{dashboard?.suggestions?.length || 0}</div>
              <div style={{ fontSize: 12, color: metricTone.suggestions.fg }}>{t('personalizedByPerformance')}</div>
            </div>
          </div>

          <div className="card" style={{ ...panelStyle, marginBottom: 16 }}>
            <div style={{ padding: '12px 16px', borderBottom: '1px solid var(--border)', fontWeight: 700, display: 'flex', justifyContent: 'space-between', gap: 8, flexWrap: 'wrap' }}>
              <span>{t('liveQuizzes')}</span>
            </div>
            {!dashboard?.liveQuizzes?.length ? (
              <div className="empty-state"><h3>{t('noLiveQuizzesNow')}</h3><p>{t('newHostedQuizzesAppear')}</p></div>
            ) : (
              <div className="table-wrapper mobile-cards">
                <table>
                  <thead>
                    <tr>
                      <th>{t('title')}</th>
                      <th>{t('topic')}</th>
                      <th>{t('duration')}</th>
                      <th>{t('published')}</th>
                      <th>{t('actions')}</th>
                    </tr>
                  </thead>
                  <tbody>
                    {dashboard.liveQuizzes.map((quiz) => (
                      <tr key={`live-${quiz.quizId}`}>
                        <td data-label={t('title')}>{quiz.title}</td>
                        <td data-label={t('topic')}>{quiz.stackName} / {quiz.topicName}</td>
                        <td data-label={t('duration')}>{quiz.durationMinutes || 15} {t('minutes')}</td>
                        <td data-label={t('published')}>{quiz.publishedAt ? new Date(quiz.publishedAt).toLocaleString() : '-'}</td>
                        <td data-label={t('actions')}>
                          <div style={{ display: 'flex', alignItems: 'center', gap: 8, flexWrap: 'wrap' }}>
                            <button className="btn btn-primary btn-sm" onClick={() => startQuiz(quiz.quizCode)}>
                              {t('enterLiveQuiz')}
                            </button>
                            {attemptedQuizCodes.has(quiz.quizCode) && (
                              <span
                                style={{
                                  background: '#fef3c7',
                                  border: '1px solid #fcd34d',
                                  color: '#92400e',
                                  borderRadius: 999,
                                  padding: '2px 8px',
                                  fontSize: 11,
                                  fontWeight: 700,
                                }}
                              >
                                {t('alreadyAttempted')}
                              </span>
                            )}
                          </div>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
          </div>

          <div className="grid-2">
            <div className="card" style={panelStyle}>
              <div style={{ padding: '12px 16px', borderBottom: '1px solid var(--border)', fontWeight: 700 }}>{t('previousQuizAttempts')}</div>
              {!dashboard?.previousAttempts?.length ? (
                <div className="empty-state"><h3>{t('noAttemptsYet')}</h3><p>{t('historyAppearsAfterAttempt')}</p></div>
              ) : (
                <div className="table-wrapper mobile-cards">
                  <table>
                    <thead>
                      <tr>
                        <th>{t('quiz')}</th>
                        <th>{t('score')}</th>
                        <th>{t('time')}</th>
                        <th>{t('submitted')}</th>
                        <th>{t('actions')}</th>
                      </tr>
                    </thead>
                    <tbody>
                      {dashboard.previousAttempts.map((attempt) => (
                        <tr key={`attempt-${attempt.attemptId}`}>
                          <td data-label={t('quiz')}>{attempt.quizTitle}</td>
                          <td data-label={t('score')}>{attempt.score}/{attempt.totalQuestions} ({formatPercent(attempt.score, attempt.totalQuestions)})</td>
                          <td data-label={t('time')}>{formatTime(attempt.timeTakenSeconds)}</td>
                          <td data-label={t('submitted')}>{attempt.submittedAt ? new Date(attempt.submittedAt).toLocaleString() : '-'}</td>
                          <td data-label={t('actions')}>
                            <button
                              className="btn btn-secondary btn-sm"
                              onClick={() => viewAttemptLeaderboard(attempt.quizCode)}
                            >
                              {t('leaderboard')}
                            </button>
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              )}
            </div>

            <div className="card" style={panelStyle}>
              <div style={{ padding: '12px 16px', borderBottom: '1px solid var(--border)', fontWeight: 700 }}>{t('suggestionsFromAnalytics')}</div>
              {!dashboard?.suggestions?.length ? (
                <div className="empty-state"><h3>{t('noSuggestionsYet')}</h3><p>{t('suggestionsAfterAttempts')}</p></div>
              ) : (
                <div style={{ padding: 12, display: 'flex', flexDirection: 'column', gap: 8 }}>
                  {dashboard.suggestions.map((suggestion, index) => (
                    <div key={`suggestion-${index}`} style={{ background: '#f8fafc', borderLeft: '3px solid #0ea5e9', padding: '10px 12px', borderRadius: 8 }}>
                      <div style={{ fontWeight: 700 }}>{suggestion.stackName} / {suggestion.topicName}</div>
                      <div style={{ fontSize: 12, color: 'var(--text-secondary)' }}>{t('averageScore')}: {suggestion.averageScorePercent.toFixed(2)}%</div>
                      <div style={{ fontSize: 13, marginTop: 4 }}>{suggestion.message}</div>
                    </div>
                  ))}
                </div>
              )}
            </div>
          </div>
            </>
          )}

          {canUseParticipantChat && (
            <div className="card" style={{ ...panelStyle, marginTop: 16 }}>
              <div style={{ padding: '12px 16px', borderBottom: '1px solid var(--border)', fontWeight: 700, display: 'flex', justifyContent: 'space-between', flexWrap: 'wrap', gap: 8 }}>
                <span>{t('employeeChatTitle')}</span>
                <span style={{ fontSize: 12, color: chatStreamConnected ? '#047857' : 'var(--text-muted)' }}>
                  {chatStreamConnected ? t('chatLive') : t('chatConnecting')}
                </span>
              </div>

              {!chatUsers.length ? (
                <div className="empty-state">
                  <h3>{t('noEmployeesAvailable')}</h3>
                  <p>{t('employeesAppearWhenOnline')}</p>
                </div>
              ) : (
                <div className="participant-chat-shell" style={{ display: 'grid', gridTemplateColumns: '280px 1fr', minHeight: 420 }}>
                  {(!isMobileViewport || !isMobileChatThreadOpen) && (
                  <div className="participant-chat-users" style={{ borderRight: '1px solid var(--border)', background: '#f8fafc' }}>
                    <div style={{ padding: 10, borderBottom: '1px solid var(--border)' }}>
                      <input
                        className="form-input"
                        value={chatSearch}
                        onChange={(e) => setChatSearch(e.target.value)}
                        placeholder="Search people"
                      />
                    </div>
                    {filteredChatUsers.map((user) => (
                      <button
                        key={user.enterpriseId}
                        onClick={() => {
                          setSelectedChatUserId(user.enterpriseId);
                          if (isMobileViewport) {
                            setIsMobileChatThreadOpen(true);
                          }
                        }}
                        style={{
                          width: '100%',
                          border: 'none',
                          borderBottom: '1px solid var(--border)',
                          background: selectedChatUserId === user.enterpriseId ? '#e0e7ff' : 'transparent',
                          textAlign: 'left',
                          padding: '10px 12px',
                          cursor: 'pointer',
                          display: 'flex',
                          justifyContent: 'space-between',
                          alignItems: 'center',
                          gap: 8,
                        }}
                      >
                        <div style={{ minWidth: 0 }}>
                          <div style={{ fontWeight: 700, fontSize: 13, display: 'flex', gap: 6, alignItems: 'center' }}>
                            <span className="truncate" style={{ maxWidth: 150 }}>{user.fullName}</span>
                            {chatUnreadByUser[user.enterpriseId] > 0 && (
                              <span style={{ background: '#2563eb', color: '#fff', borderRadius: 999, fontSize: 10, padding: '1px 6px', fontWeight: 700 }}>
                                {chatUnreadByUser[user.enterpriseId]}
                              </span>
                            )}
                          </div>
                          <div style={{ fontSize: 11, color: 'var(--text-muted)' }} className="truncate">{user.enterpriseId} • {user.role}</div>
                          {conversationPreviewByUser[user.enterpriseId] && (
                            <div style={{ fontSize: 11, color: 'var(--text-secondary)' }} className="truncate">
                              {conversationPreviewByUser[user.enterpriseId].senderEnterpriseId === participantIdentity.participantEnterpriseId ? 'You: ' : ''}
                              {conversationPreviewByUser[user.enterpriseId].message}
                            </div>
                          )}
                        </div>
                        <span
                          style={{
                            display: 'inline-flex',
                            alignItems: 'center',
                            gap: 4,
                            fontSize: 11,
                            color: user.online ? '#047857' : 'var(--text-muted)',
                          }}
                        >
                          <span
                            style={{
                              width: 8,
                              height: 8,
                              borderRadius: '50%',
                              background: user.online ? '#10b981' : '#cbd5e1',
                            }}
                          />
                          {user.online ? t('online') : t('offline')}
                        </span>
                      </button>
                    ))}
                  </div>
                  )}

                  {(!isMobileViewport || isMobileChatThreadOpen) && (
                  <div className="participant-chat-thread" style={{ display: 'flex', flexDirection: 'column' }}>
                    <div style={{ padding: '10px 12px', borderBottom: '1px solid var(--border)', fontSize: 13, color: 'var(--text-secondary)', display: 'flex', justifyContent: 'space-between', gap: 8, alignItems: 'center' }}>
                      <div>
                        {selectedChatUser
                          ? `${t('chattingWith')}: ${selectedChatUser.fullName} (${selectedChatUser.enterpriseId})`
                          : t('selectParticipantToStartChat')}
                      </div>
                      {isMobileViewport && (
                        <button className="btn btn-secondary btn-sm" onClick={() => setIsMobileChatThreadOpen(false)}>
                          Back
                        </button>
                      )}
                    </div>
                    {selectedChatUserId && typingByUser[selectedChatUserId] && (
                      <div style={{ padding: '6px 12px', borderBottom: '1px solid var(--border)', background: '#f8fafc', fontSize: 12, color: 'var(--text-secondary)' }}>
                        {selectedChatUser?.fullName || 'Participant'} is typing...
                      </div>
                    )}

                    <div ref={chatThreadRef} style={{ flex: 1, overflowY: 'auto', padding: 12, background: '#fff' }}>
                      {selectedChatUser
                        ? (chatLoading ? (
                        <div className="loading-container"><div className="spinner spinner-dark" /></div>
                      ) : !activeChatMessages.length ? (
                        <div className="empty-state" style={{ padding: '24px 10px' }}>
                          <h3>{t('noMessagesYet')}</h3>
                          <p>{t('startConversationPrompt')}</p>
                        </div>
                      ) : (
                        <div style={{ display: 'flex', flexDirection: 'column', gap: 10 }}>
                          {groupedActiveMessages.map((entry) => {
                            if (entry.type === 'date') {
                              return (
                                <div key={entry.id} style={{ display: 'flex', justifyContent: 'center' }}>
                                  <span style={{ background: '#eef2ff', color: '#4338ca', borderRadius: 999, padding: '2px 10px', fontSize: 11, fontWeight: 600 }}>
                                    {entry.label}
                                  </span>
                                </div>
                              );
                            }

                            const message = entry.message;
                            const mine = message.senderEnterpriseId === participantIdentity.participantEnterpriseId;
                            const messageStatusLabel = (() => {
                              if (message.deliveryStatus === 'READ') {
                                return `Read${message.readAt ? ` ${formatChatTime(message.readAt)}` : ''}`;
                              }
                              if (message.deliveryStatus === 'DELIVERED') {
                                return `Delivered${message.deliveredAt ? ` ${formatChatTime(message.deliveredAt)}` : ''}`;
                              }
                              return 'Sent';
                            })();
                            return (
                              <div key={entry.id} style={{ display: 'flex', justifyContent: mine ? 'flex-end' : 'flex-start' }}>
                                <div
                                  style={{
                                    maxWidth: '78%',
                                    background: mine ? '#4f46e5' : '#f1f5f9',
                                    color: mine ? '#fff' : 'var(--text-primary)',
                                    border: `1px solid ${mine ? '#4338ca' : '#e2e8f0'}`,
                                    borderRadius: 14,
                                    padding: '8px 10px',
                                    boxShadow: '0 3px 10px rgba(15, 23, 42, 0.06)',
                                  }}
                                >
                                  <div style={{ fontSize: 11, color: mine ? 'rgba(255,255,255,0.82)' : 'var(--text-muted)', marginBottom: 4, display: 'flex', justifyContent: 'space-between', gap: 8 }}>
                                    <span>{mine ? t('you') : message.senderName}</span>
                                    <span>{formatChatTime(message.createdAt)}</span>
                                  </div>
                                  <div style={{ fontSize: 13, whiteSpace: 'pre-wrap' }}>{message.message}</div>
                                  {message.hasAttachment && message.messageId && (
                                    <div style={{ marginTop: 8 }}>
                                      <ChatAttachment message={message} authToken={participantIdentity.authToken} mine={mine} />
                                    </div>
                                  )}
                                  {mine && !message.pending && (
                                    <div style={{ marginTop: 4, fontSize: 10, color: mine ? 'rgba(255,255,255,0.75)' : 'var(--text-muted)' }}>
                                      {messageStatusLabel}
                                    </div>
                                  )}
                                  {message.pending && (
                                    <div style={{ marginTop: 4, fontSize: 10, color: mine ? 'rgba(255,255,255,0.75)' : 'var(--text-muted)' }}>
                                      {t('sending')}
                                    </div>
                                  )}
                                </div>
                              </div>
                            );
                          })}
                        </div>
                      )) : (
                        <div className="empty-state" style={{ padding: '24px 10px' }}>
                          <h3>{t('selectParticipantToStartChat')}</h3>
                        </div>
                      )}
                    </div>

                    <div style={{ borderTop: '1px solid var(--border)', padding: 10, display: 'flex', gap: 8, alignItems: 'flex-end' }}>
                      <button
                        type="button"
                        className="btn btn-secondary btn-sm"
                        style={{ minWidth: 40, height: 40, justifyContent: 'center' }}
                        onClick={() => setShowEmojiPicker((prev) => !prev)}
                        disabled={!selectedChatUserId}
                      >
                        😀
                      </button>
                      <button
                        type="button"
                        className="btn btn-secondary btn-sm"
                        style={{ minWidth: 40, height: 40, justifyContent: 'center' }}
                        onClick={() => attachmentInputRef.current?.click()}
                        disabled={!selectedChatUserId || chatUploading}
                      >
                        {chatUploading ? '⏳' : '📎'}
                      </button>
                      <input
                        ref={attachmentInputRef}
                        type="file"
                        style={{ display: 'none' }}
                        onChange={(e) => sendChatAttachment(e.target.files?.[0])}
                      />
                      <textarea
                        className="form-input"
                        rows={2}
                        style={{ resize: 'none', minHeight: 46, maxHeight: 120 }}
                        value={chatText}
                        onChange={(e) => {
                          const value = e.target.value;
                          setChatText(value);
                          if (typingStopTimerRef.current) {
                            clearTimeout(typingStopTimerRef.current);
                          }

                          sendTypingEvent(value.trim().length > 0);

                          typingStopTimerRef.current = setTimeout(() => {
                            sendTypingEvent(false);
                          }, 1200);
                        }}
                        onKeyDown={(e) => {
                          if (e.key === 'Enter' && !e.shiftKey) {
                            e.preventDefault();
                            sendChatMessage();
                          }
                        }}
                        placeholder={t('typeMessagePlaceholder')}
                        disabled={!selectedChatUserId || chatSending}
                      />
                      <button
                        className="btn btn-primary"
                        onClick={sendChatMessage}
                        disabled={!selectedChatUserId || chatSending || !chatText.trim()}
                      >
                        {chatSending ? t('sending') : t('send')}
                      </button>
                    </div>
                    {showEmojiPicker && (
                      <div style={{ borderTop: '1px solid var(--border)', padding: '8px 10px', display: 'flex', gap: 8, flexWrap: 'wrap', background: '#f8fafc' }}>
                        {emojiSet.map((emoji) => (
                          <button
                            type="button"
                            key={emoji}
                            className="btn btn-secondary btn-sm"
                            style={{ minWidth: 36, height: 36, justifyContent: 'center', padding: 0 }}
                            onClick={() => setChatText((prev) => `${prev}${emoji}`)}
                          >
                            {emoji}
                          </button>
                        ))}
                      </div>
                    )}
                  </div>
                  )}
                </div>
              )}
            </div>
          )}
        </>
    </div>
  );
}
