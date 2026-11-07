import React, { useEffect, useMemo, useRef, useState } from 'react';
import toast from 'react-hot-toast';
import { useAuth } from '../context/AuthContext';
import { useLanguage } from '../context/LanguageContext';
import { quizPublicAPI } from '../services/api';
import ChatAttachment from '../components/ChatAttachment';
import { getAuthToken } from '../services/authSession';

const CHAT_SYNC_INTERVAL_MS = 1000;
const USERS_SYNC_INTERVAL_MS = 1500;
const MOBILE_BREAKPOINT = 900;
const emojiSet = ['😀', '😁', '😂', '😊', '😉', '😍', '🤝', '👍', '🙌', '🔥', '🎯', '✅', '👏', '💡', '🙏', '🚀'];

const isNumericMessageId = (value) => Number.isFinite(Number(value));

const formatTime = (value) => {
  if (!value) return '';
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return '';
  return date.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
};

const toSortableTime = (value) => {
  if (!value) return 0;
  const date = new Date(value);
  const timestamp = date.getTime();
  return Number.isNaN(timestamp) ? 0 : timestamp;
};

const roleBadgeColor = (role) => {
  if (role === 'ADMIN') return '#ef4444';
  if (role === 'SME') return '#2563eb';
  return '#059669';
};

const roleLabelKey = (role) => {
  if (role === 'ADMIN') return 'roleAdmin';
  if (role === 'SME') return 'roleSme';
  if (role === 'PARTICIPANT') return 'roleParticipant';
  return '';
};

export default function TeamChatPage() {
  const { user } = useAuth();
  const { t } = useLanguage();

  const [users, setUsers] = useState([]);
  const [searchText, setSearchText] = useState('');
  const [selectedUserId, setSelectedUserId] = useState('');
  const [messagesByUser, setMessagesByUser] = useState({});
  const [unreadByUser, setUnreadByUser] = useState({});
  const [typingByUser, setTypingByUser] = useState({});
  const [text, setText] = useState('');
  const [loadingHistory, setLoadingHistory] = useState(false);
  const [sendingByUser, setSendingByUser] = useState({});
  const [uploadingByUser, setUploadingByUser] = useState({});
  const [showEmojiPicker, setShowEmojiPicker] = useState(false);
  const [streamConnected, setStreamConnected] = useState(false);
  const [isMobileViewport, setIsMobileViewport] = useState(() => window.innerWidth <= MOBILE_BREAKPOINT);
  const [isMobileThreadOpen, setIsMobileThreadOpen] = useState(false);

  const typingStopTimerRef = useRef(null);
  const threadRef = useRef(null);
  const attachmentInputRef = useRef(null);

  const authToken = getAuthToken();
  const selfEnterpriseId = user?.enterpriseId || '';
  const canUseChat = Boolean(authToken && selfEnterpriseId && (user?.role === 'ADMIN' || user?.role === 'SME'));

  const selectedUser = useMemo(
    () => users.find((item) => item.enterpriseId === selectedUserId) || null,
    [users, selectedUserId]
  );

  const latestMessageByUser = useMemo(() => {
    const map = {};
    Object.keys(messagesByUser).forEach((enterpriseId) => {
      const messages = messagesByUser[enterpriseId] || [];
      if (!messages.length) return;
      map[enterpriseId] = messages[messages.length - 1];
    });
    return map;
  }, [messagesByUser]);

  const orderedUsers = useMemo(() => {
    const next = [...users];
    next.sort((a, b) => {
      const aLast = latestMessageByUser[a.enterpriseId];
      const bLast = latestMessageByUser[b.enterpriseId];
      const aTime = toSortableTime(aLast?.createdAt || aLast?.deliveredAt || aLast?.readAt);
      const bTime = toSortableTime(bLast?.createdAt || bLast?.deliveredAt || bLast?.readAt);

      if (aTime !== bTime) return bTime - aTime;
      if (a.online !== b.online) return a.online ? -1 : 1;
      return String(a.fullName || '').localeCompare(String(b.fullName || ''));
    });
    return next;
  }, [users, latestMessageByUser]);

  const filteredUsers = useMemo(() => {
    if (!searchText.trim()) return orderedUsers;
    const keyword = searchText.trim().toLowerCase();
    return orderedUsers.filter((item) => (
      item.fullName?.toLowerCase().includes(keyword)
      || item.enterpriseId?.toLowerCase().includes(keyword)
      || item.role?.toLowerCase().includes(keyword)
    ));
  }, [orderedUsers, searchText]);

  const activeMessages = useMemo(
    () => messagesByUser[selectedUserId] || [],
    [messagesByUser, selectedUserId]
  );

  const sendingForSelectedUser = Boolean(selectedUserId && sendingByUser[selectedUserId]);
  const uploadingForSelectedUser = Boolean(selectedUserId && uploadingByUser[selectedUserId]);

  const applyUsers = (nextUsers) => {
    const safeUsers = Array.isArray(nextUsers) ? nextUsers : [];
    setUsers(safeUsers);
    setSelectedUserId((prev) => {
      if (!safeUsers.length) return '';
      if (prev && safeUsers.some((item) => item.enterpriseId === prev)) return prev;
      return safeUsers[0].enterpriseId;
    });
  };

  const loadUsers = async () => {
    if (!canUseChat) return;
    try {
      const res = await quizPublicAPI.getParticipantChatUsers({
        participantEnterpriseId: selfEnterpriseId,
        participantAuthToken: authToken,
      });
      applyUsers(res.data?.users || []);
    } catch (err) {
      toast.error((err?.response?.data?.detail || err?.response?.data?.message) || t('teamChatLoadUsersFailed'));
    }
  };

  const appendMessage = (otherEnterpriseId, incoming) => {
    setMessagesByUser((prev) => {
      const current = prev[otherEnterpriseId] || [];
      if (current.some((item) => item.messageId === incoming.messageId)) {
        return prev;
      }
      return { ...prev, [otherEnterpriseId]: [...current, incoming] };
    });
  };

  const loadHistory = async (otherEnterpriseId, { background = false } = {}) => {
    if (!canUseChat || !otherEnterpriseId) return;
    const hasCachedMessages = Boolean((messagesByUser[otherEnterpriseId] || []).length);
    const shouldRunInBackground = background || hasCachedMessages;
    const shouldShowBlockingLoader = !shouldRunInBackground;

    if (shouldShowBlockingLoader) {
      setLoadingHistory(true);
    }
    try {
      const res = await quizPublicAPI.getParticipantChatHistory({
        participantEnterpriseId: selfEnterpriseId,
        participantAuthToken: authToken,
        withEnterpriseId: otherEnterpriseId,
        limit: 100,
      });
      const nextMessages = res.data?.messages || [];
      setMessagesByUser((prev) => {
        const existing = prev[otherEnterpriseId] || [];
        const pending = existing.filter((msg) => msg.pending && String(msg.messageId).startsWith('temp-'));
        const merged = [...nextMessages];

        pending.forEach((item) => {
          if (!merged.some((message) => message.messageId === item.messageId)) {
            merged.push(item);
          }
        });

        return {
          ...prev,
          [otherEnterpriseId]: merged,
        };
      });
    } catch (err) {
      if (shouldShowBlockingLoader) {
        toast.error((err?.response?.data?.detail || err?.response?.data?.message) || t('teamChatLoadHistoryFailed'));
      }
    } finally {
      if (shouldShowBlockingLoader) {
        setLoadingHistory(false);
      }
    }
  };

  const publishTyping = async (isTyping, withEnterpriseId = selectedUserId) => {
    if (!canUseChat || !withEnterpriseId) return;
    try {
      await quizPublicAPI.publishParticipantTyping({
        participantEnterpriseId: selfEnterpriseId,
        participantAuthToken: authToken,
        withEnterpriseId,
        typing: Boolean(isTyping),
      });
    } catch {
      // Typing updates are best effort.
    }
  };

  const markConversationRead = async () => {
    if (!canUseChat || !selectedUserId) return;
    const lastMessage = activeMessages[activeMessages.length - 1];
    const numericLastSeenId = isNumericMessageId(lastMessage?.messageId)
      ? Number(lastMessage?.messageId)
      : null;

    try {
      await quizPublicAPI.markParticipantChatRead({
        participantEnterpriseId: selfEnterpriseId,
        participantAuthToken: authToken,
        withEnterpriseId: selectedUserId,
        lastSeenMessageId: numericLastSeenId,
      });
    } catch {
      // Read receipts are best effort.
    }
  };

  const sendMessage = async () => {
    const message = text.trim();
    if (!canUseChat || !selectedUserId || !message) return;

    const targetEnterpriseId = selectedUserId;
    const targetRecipientName = selectedUser?.fullName || targetEnterpriseId;
    const targetAlreadySending = Boolean(sendingByUser[targetEnterpriseId]);
    if (targetAlreadySending) return;

    const tempId = `temp-${Date.now()}-${Math.floor(Math.random() * 1000)}`;
    appendMessage(targetEnterpriseId, {
      messageId: tempId,
      senderEnterpriseId: selfEnterpriseId,
      senderName: user?.fullName || selfEnterpriseId,
      recipientEnterpriseId: targetEnterpriseId,
      recipientName: targetRecipientName,
      message,
      createdAt: new Date().toISOString(),
      pending: true,
      deliveryStatus: 'SENT',
    });

    setText('');
    setSendingByUser((prev) => ({ ...prev, [targetEnterpriseId]: true }));

    try {
      const res = await quizPublicAPI.sendParticipantChatMessage({
        participantEnterpriseId: selfEnterpriseId,
        participantAuthToken: authToken,
        recipientEnterpriseId: targetEnterpriseId,
        message,
      });

      const saved = res.data;
      setMessagesByUser((prev) => {
        const current = prev[targetEnterpriseId] || [];
        const replaced = current.map((item) => (
          item.messageId === tempId ? { ...saved, deliveryStatus: saved?.deliveryStatus || 'SENT' } : item
        ));
        const hasSaved = replaced.some((item) => item.messageId === saved?.messageId);
        return {
          ...prev,
          [targetEnterpriseId]: hasSaved ? replaced : [...replaced, { ...saved, deliveryStatus: saved?.deliveryStatus || 'SENT' }],
        };
      });
      loadHistory(targetEnterpriseId, { background: true });
    } catch (err) {
      setMessagesByUser((prev) => {
        const current = prev[targetEnterpriseId] || [];
        return {
          ...prev,
          [targetEnterpriseId]: current.filter((item) => item.messageId !== tempId),
        };
      });

      // Restore unsent text only when user is still on the same conversation.
      if (selectedUserId === targetEnterpriseId) {
        setText(message);
      }

      toast.error((err?.response?.data?.detail || err?.response?.data?.message) || t('teamChatSendFailed'));
    } finally {
      setSendingByUser((prev) => ({ ...prev, [targetEnterpriseId]: false }));
      publishTyping(false, targetEnterpriseId);
    }
  };

  const sendAttachment = async (file) => {
    if (!file || !canUseChat || !selectedUserId) return;

    const targetEnterpriseId = selectedUserId;
    const caption = text.trim();

    setUploadingByUser((prev) => ({ ...prev, [targetEnterpriseId]: true }));
    try {
      const formData = new FormData();
      formData.append('participantEnterpriseId', selfEnterpriseId);
      formData.append('participantAuthToken', authToken);
      formData.append('recipientEnterpriseId', targetEnterpriseId);
      formData.append('message', caption);
      formData.append('file', file);

      const res = await quizPublicAPI.sendParticipantChatAttachment(formData);
      const savedMessage = res.data;

      if (savedMessage?.messageId) {
        appendMessage(targetEnterpriseId, {
          ...savedMessage,
          deliveryStatus: savedMessage.deliveryStatus || 'SENT',
        });
      }

      loadHistory(targetEnterpriseId, { background: true });

      if (selectedUserId === targetEnterpriseId && caption) {
        setText('');
      }
    } catch (err) {
      toast.error((err?.response?.data?.detail || err?.response?.data?.message) || 'Failed to upload attachment');
    } finally {
      setUploadingByUser((prev) => ({ ...prev, [targetEnterpriseId]: false }));
      if (attachmentInputRef.current) {
        attachmentInputRef.current.value = '';
      }
    }
  };

  useEffect(() => {
    if (!canUseChat) return;
    loadUsers();
  }, [canUseChat]);

  useEffect(() => {
    if (!canUseChat || !selectedUserId) return;
    setUnreadByUser((prev) => ({ ...prev, [selectedUserId]: 0 }));
    setShowEmojiPicker(false);
    loadHistory(selectedUserId);
    if (isMobileViewport) {
      setIsMobileThreadOpen(true);
    }
  }, [canUseChat, selectedUserId]);

  useEffect(() => {
    if (!canUseChat) return;

    const refreshNow = () => {
      if (document.visibilityState === 'hidden') return;
      loadUsers();
      if (selectedUserId) {
        loadHistory(selectedUserId, { background: true });
      }
    };

    const handleVisibility = () => {
      if (document.visibilityState === 'visible') {
        refreshNow();
      }
    };

    window.addEventListener('focus', refreshNow);
    document.addEventListener('visibilitychange', handleVisibility);

    return () => {
      window.removeEventListener('focus', refreshNow);
      document.removeEventListener('visibilitychange', handleVisibility);
    };
  }, [canUseChat, selectedUserId]);

  useEffect(() => {
    if (!canUseChat || !selectedUserId) return;
    const syncConversation = () => {
      if (document.visibilityState === 'hidden') return;
      loadHistory(selectedUserId, { background: true });
    };

    syncConversation();
    const intervalId = setInterval(() => {
      syncConversation();
    }, CHAT_SYNC_INTERVAL_MS);
    return () => clearInterval(intervalId);
  }, [canUseChat, selectedUserId]);

  useEffect(() => {
    const onResize = () => {
      const mobile = window.innerWidth <= MOBILE_BREAKPOINT;
      setIsMobileViewport(mobile);
      if (!mobile) {
        setIsMobileThreadOpen(false);
      }
    };

    window.addEventListener('resize', onResize);
    return () => window.removeEventListener('resize', onResize);
  }, []);

  useEffect(() => {
    if (!canUseChat) return;

    

    const handleStreamEvent = (event) => {
      if (!event?.data) return;
      try {
        const payload = JSON.parse(event.data);

        if (payload?.eventType === 'presence' && Array.isArray(payload?.users)) {
          applyUsers(payload.users);
          return;
        }

        if (payload?.eventType === 'typing' && payload?.typingEnterpriseId) {
          if (payload.typingEnterpriseId !== selfEnterpriseId) {
            setTypingByUser((prev) => ({
              ...prev,
              [payload.typingEnterpriseId]: Boolean(payload.typing),
            }));
          }
          return;
        }

        if (payload?.eventType === 'message-status' && payload?.withEnterpriseId && payload?.messageStatus) {
          const withUser = payload.withEnterpriseId;
          setMessagesByUser((prev) => {
            const current = prev[withUser] || [];
            if (!current.length) return prev;

            const upto = isNumericMessageId(payload.messageId) ? Number(payload.messageId) : null;
            const updated = current.map((item) => {
              if (item.senderEnterpriseId !== selfEnterpriseId) return item;
              if (upto !== null && isNumericMessageId(item.messageId) && Number(item.messageId) > upto) return item;

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
          return;
        }

        if (payload?.eventType === 'message' && payload?.message) {
          const incoming = payload.message;
          const otherId = incoming.senderEnterpriseId === selfEnterpriseId
            ? incoming.recipientEnterpriseId
            : incoming.senderEnterpriseId;

          appendMessage(otherId, incoming);

          if (incoming.senderEnterpriseId !== selfEnterpriseId && otherId !== selectedUserId) {
            setUnreadByUser((prev) => ({
              ...prev,
              [otherId]: (prev[otherId] || 0) + 1,
            }));
          }
        }
      } catch {
        // Ignore malformed events.
      }
    };

    // Tickets are single-use; the helper fetches a fresh one on every (re)connect.
    const closeStream = quizPublicAPI.openParticipantChatStream({
      participantAuthToken: authToken,
      onEvent: handleStreamEvent,
      onOpen: () => setStreamConnected(true),
      onError: () => setStreamConnected(false),
    });

    return () => {
      setStreamConnected(false);
      closeStream();
    };
  }, [canUseChat, selfEnterpriseId, authToken, selectedUserId]);

  useEffect(() => {
    if (!canUseChat || streamConnected) return;
    const intervalId = setInterval(() => {
      if (document.visibilityState === 'hidden') return;
      loadUsers();
    }, USERS_SYNC_INTERVAL_MS);
    return () => clearInterval(intervalId);
  }, [canUseChat, streamConnected]);

  useEffect(() => {
    if (!threadRef.current) return;
    threadRef.current.scrollTo({ top: threadRef.current.scrollHeight, behavior: 'smooth' });
    markConversationRead();
  }, [activeMessages.length, selectedUserId]);

  useEffect(() => {
    return () => {
      if (typingStopTimerRef.current) {
        clearTimeout(typingStopTimerRef.current);
      }
    };
  }, []);

  if (!canUseChat) {
    return (
      <div className="card">
        <h1 className="page-title">{t('teamChat')}</h1>
        <div className="alert alert-error" style={{ marginTop: 16 }}>
          {t('teamChatAccessRestricted')}
        </div>
      </div>
    );
  }

  const showUserList = !isMobileViewport || !isMobileThreadOpen;
  const showThread = !isMobileViewport || isMobileThreadOpen;

  return (
    <div style={{ display: 'grid', gap: 16, height: 'calc(100vh - 150px)', overflow: 'hidden' }}>
      <div className="page-header" style={{ marginBottom: 0 }}>
        <div>
          <h1 className="page-title">{t('teamChatTitle')}</h1>
          <p className="page-subtitle">
            {t('teamChatSubtitle')}
            {user?.role === 'SME' ? ` ${t('teamChatSmeScope')}` : ` ${t('teamChatAdminScope')}`}
          </p>
        </div>
        <span
          className="badge"
          style={{
            background: streamConnected ? '#dcfce7' : '#fef3c7',
            color: streamConnected ? '#166534' : '#92400e',
          }}
        >
          {streamConnected ? t('teamChatLiveConnected') : t('teamChatReconnecting')}
        </span>
      </div>

      <div className="card" style={{ padding: 0, display: 'grid', gridTemplateColumns: isMobileViewport ? '1fr' : '320px 1fr', height: '100%', overflow: 'hidden' }}>
        {showUserList && <aside style={{ borderRight: isMobileViewport ? 'none' : '1px solid var(--border)', borderBottom: isMobileViewport ? '1px solid var(--border)' : 'none', background: '#f8fafc', display: 'flex', flexDirection: 'column', minHeight: 0 }}>
          <div style={{ padding: 12, borderBottom: '1px solid var(--border)' }}>
            <input
              className="form-input"
              placeholder={t('teamChatSearchUsers')}
              value={searchText}
              onChange={(event) => setSearchText(event.target.value)}
            />
          </div>

          <div style={{ overflowY: 'auto', minHeight: 0, padding: 8, display: 'grid', gap: 6, flex: 1, alignContent: 'start' }}>
            {filteredUsers.map((item) => {
              const active = item.enterpriseId === selectedUserId;
              const unread = unreadByUser[item.enterpriseId] || 0;
              const typing = Boolean(typingByUser[item.enterpriseId]);
              const latestMessage = latestMessageByUser[item.enterpriseId];
              const previewText = latestMessage?.message || '';
              const previewAttachmentText = latestMessage?.hasAttachment
                ? `📎 ${latestMessage.attachmentName || 'Attachment'}`
                : '';

              return (
                <button
                  type="button"
                  key={item.enterpriseId}
                  data-testid={`team-chat-user-${item.enterpriseId}`}
                  onClick={() => setSelectedUserId(item.enterpriseId)}
                  style={{
                    width: '100%',
                    textAlign: 'left',
                    border: active ? '1px solid #2563eb' : '1px solid var(--border)',
                    borderRadius: 10,
                    background: active ? 'rgba(37,99,235,0.08)' : '#fff',
                    padding: '8px 10px',
                    cursor: 'pointer',
                  }}
                >
                  <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: 8 }}>
                    <div style={{ fontWeight: 600, color: 'var(--text)' }}>{item.fullName}</div>
                    <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                      {latestMessage?.createdAt && (
                        <span style={{ fontSize: 10, color: '#64748b' }}>{formatTime(latestMessage.createdAt)}</span>
                      )}
                    {unread > 0 && (
                      <span style={{ background: '#dc2626', color: '#fff', borderRadius: 999, minWidth: 20, height: 20, display: 'inline-flex', alignItems: 'center', justifyContent: 'center', fontSize: 11, padding: '0 6px' }}>
                        {unread}
                      </span>
                    )}
                    </div>
                  </div>
                  <div style={{ marginTop: 4, display: 'flex', alignItems: 'center', gap: 8 }}>
                    <span style={{ fontSize: 11, padding: '2px 6px', borderRadius: 999, color: '#fff', background: roleBadgeColor(item.role) }}>
                      {t(roleLabelKey(item.role)) || item.role}
                    </span>
                    <span style={{ fontSize: 11, color: item.online ? '#16a34a' : '#64748b' }}>{item.online ? t('teamChatOnline') : t('teamChatOffline')}</span>
                    {typing && <span style={{ fontSize: 11, color: '#0f766e' }}>{t('teamChatTyping')}</span>}
                  </div>
                  {(previewText || previewAttachmentText) && (
                    <div style={{ marginTop: 4, fontSize: 12, color: '#64748b', overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
                      {previewText || previewAttachmentText}
                    </div>
                  )}
                </button>
              );
            })}
            {!filteredUsers.length && <div style={{ padding: 8, color: 'var(--text-secondary)', fontSize: 13 }}>{t('teamChatNoUsersMatch')}</div>}
          </div>
        </aside>}

        {showThread && <section data-testid="team-chat-thread" style={{ display: 'grid', gridTemplateRows: 'auto 1fr auto', minHeight: 0 }}>
          <div style={{ borderBottom: '1px solid var(--border)', padding: '10px 12px' }}>
            <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
              {isMobileViewport && (
                <button
                  type="button"
                  className="btn btn-ghost btn-sm"
                  data-testid="team-chat-mobile-back"
                  onClick={() => setIsMobileThreadOpen(false)}
                >
                  ←
                </button>
              )}
              <div style={{ fontWeight: 700, color: 'var(--text)' }}>{selectedUser?.fullName || t('teamChatSelectUser')}</div>
            </div>
            <div style={{ marginTop: 2, fontSize: 12, color: 'var(--text-secondary)' }}>{selectedUser?.enterpriseId || ''}</div>
          </div>

          <div ref={threadRef} style={{ overflowY: 'auto', padding: 12, background: '#ffffff', minHeight: 0 }}>
            {loadingHistory && <div style={{ fontSize: 13, color: 'var(--text-secondary)' }}>{t('teamChatLoadingConversation')}</div>}

            {!loadingHistory && activeMessages.map((message) => {
              const mine = message.senderEnterpriseId === selfEnterpriseId;
              const status = mine ? (message.deliveryStatus || (message.pending ? 'SENDING' : 'SENT')) : '';
              return (
                <div key={message.messageId} style={{ display: 'flex', justifyContent: mine ? 'flex-end' : 'flex-start', marginBottom: 10 }}>
                  <div style={{ maxWidth: '78%', background: mine ? '#2563eb' : '#f1f5f9', color: mine ? '#fff' : '#0f172a', borderRadius: 12, padding: '9px 11px' }}>
                    <div style={{ whiteSpace: 'pre-wrap', lineHeight: 1.35 }}>{message.message}</div>
                                  {message.hasAttachment && message.messageId && (
                                    <div style={{ marginTop: 8 }}>
                                      <ChatAttachment message={message} authToken={authToken} mine={mine} />
                                    </div>
                                  )}
                    <div style={{ marginTop: 5, fontSize: 11, opacity: 0.85, display: 'flex', gap: 8, justifyContent: 'flex-end' }}>
                      <span>{formatTime(message.createdAt)}</span>
                      {mine && <span>{status}</span>}
                    </div>
                  </div>
                </div>
              );
            })}

            {!loadingHistory && !activeMessages.length && (
              <div style={{ fontSize: 13, color: 'var(--text-secondary)' }}>{t('teamChatNoMessages')}</div>
            )}
          </div>

          <div style={{ borderTop: '1px solid var(--border)', padding: 10, display: 'grid', gap: 8 }}>
            <div style={{ display: 'flex', gap: 8, alignItems: 'center' }}>
              <button
                type="button"
                className="btn btn-secondary btn-sm"
                data-testid="team-chat-emoji-toggle"
                style={{ minWidth: 40, height: 40, justifyContent: 'center' }}
                onClick={() => setShowEmojiPicker((prev) => !prev)}
                disabled={!selectedUserId}
              >
                😀
              </button>
              <button
                type="button"
                className="btn btn-secondary btn-sm"
                data-testid="team-chat-attachment-toggle"
                style={{ minWidth: 40, height: 40, justifyContent: 'center' }}
                onClick={() => attachmentInputRef.current?.click()}
                disabled={!selectedUserId || uploadingForSelectedUser}
              >
                {uploadingForSelectedUser ? '⏳' : '📎'}
              </button>
              <input
                ref={attachmentInputRef}
                data-testid="team-chat-attachment-input"
                type="file"
                style={{
                  position: 'absolute',
                  width: 1,
                  height: 1,
                  opacity: 0,
                  pointerEvents: 'none',
                }}
                onChange={(event) => sendAttachment(event.target.files?.[0])}
              />
            </div>
            <textarea
              data-testid="team-chat-input"
              value={text}
              onChange={(event) => {
                const value = event.target.value;
                setText(value);

                if (selectedUserId) {
                  publishTyping(value.trim().length > 0);
                  if (typingStopTimerRef.current) clearTimeout(typingStopTimerRef.current);
                  typingStopTimerRef.current = setTimeout(() => {
                    publishTyping(false);
                  }, 1200);
                }
              }}
              onKeyDown={(event) => {
                if (event.key === 'Enter' && !event.shiftKey) {
                  event.preventDefault();
                  sendMessage();
                }
              }}
              rows={2}
              placeholder={selectedUserId ? t('teamChatTypeMessage') : t('teamChatSelectUserToBegin')}
              disabled={!selectedUserId}
              style={{ resize: 'none' }}
            />
            <div style={{ display: 'flex', justifyContent: 'flex-end' }}>
              <button
                type="button"
                className="btn btn-primary"
                onClick={sendMessage}
                disabled={!selectedUserId || sendingForSelectedUser || !text.trim()}
              >
                {sendingForSelectedUser ? t('teamChatSending') : t('teamChatSend')}
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
                    onClick={() => setText((prev) => `${prev}${emoji}`)}
                  >
                    {emoji}
                  </button>
                ))}
              </div>
            )}
          </div>
        </section>}
      </div>
    </div>
  );
}
