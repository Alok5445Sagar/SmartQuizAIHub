import React, { useEffect, useMemo, useRef, useState } from 'react';
import toast from 'react-hot-toast';
import ReactMarkdown from 'react-markdown';
import remarkGfm from 'remark-gfm';
import { PrismLight as SyntaxHighlighter } from 'react-syntax-highlighter';
import { oneDark } from 'react-syntax-highlighter/dist/esm/styles/prism';
import java from 'react-syntax-highlighter/dist/esm/languages/prism/java';
import javascript from 'react-syntax-highlighter/dist/esm/languages/prism/javascript';
import jsx from 'react-syntax-highlighter/dist/esm/languages/prism/jsx';
import typescript from 'react-syntax-highlighter/dist/esm/languages/prism/typescript';
import tsx from 'react-syntax-highlighter/dist/esm/languages/prism/tsx';
import json from 'react-syntax-highlighter/dist/esm/languages/prism/json';
import sql from 'react-syntax-highlighter/dist/esm/languages/prism/sql';
import bash from 'react-syntax-highlighter/dist/esm/languages/prism/bash';
import python from 'react-syntax-highlighter/dist/esm/languages/prism/python';
import { aiAPI } from '../services/api';
import { useAuth } from '../context/AuthContext';
import { useLanguage } from '../context/LanguageContext';

SyntaxHighlighter.registerLanguage('java', java);
SyntaxHighlighter.registerLanguage('javascript', javascript);
SyntaxHighlighter.registerLanguage('js', javascript);
SyntaxHighlighter.registerLanguage('jsx', jsx);
SyntaxHighlighter.registerLanguage('typescript', typescript);
SyntaxHighlighter.registerLanguage('ts', typescript);
SyntaxHighlighter.registerLanguage('tsx', tsx);
SyntaxHighlighter.registerLanguage('json', json);
SyntaxHighlighter.registerLanguage('sql', sql);
SyntaxHighlighter.registerLanguage('bash', bash);
SyntaxHighlighter.registerLanguage('sh', bash);
SyntaxHighlighter.registerLanguage('python', python);
SyntaxHighlighter.registerLanguage('py', python);

function toApiHistory(messages) {
  return messages
    .filter((m) => m.role === 'user' || m.role === 'assistant')
    .slice(-8)
    .map((m) => ({ role: m.role, content: m.content }));
}

function generateSessionId() {
  return `session-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`;
}

export default function ChatAssistantWidget() {
  const { user } = useAuth();
  const { t } = useLanguage();
  const [isOpen, setIsOpen] = useState(false);
  const [bootstrapped, setBootstrapped] = useState(false);
  const [panelSize, setPanelSize] = useState({ width: 370, height: 560 });

  const [messages, setMessages] = useState([]);
  const [sessions, setSessions] = useState([]);
  const [currentSessionId, setCurrentSessionId] = useState('');
  const [suggestions, setSuggestions] = useState([]);
  const [assistantRole, setAssistantRole] = useState(user?.role || 'SME');
  const [loadingHistory, setLoadingHistory] = useState(false);

  const [input, setInput] = useState('');
  const [loading, setLoading] = useState(false);

  const scrollRef = useRef(null);
  const resizeStateRef = useRef(null);

  const canSend = useMemo(
    () => input.trim().length > 0 && !loading && !loadingHistory,
    [input, loading, loadingHistory]
  );

  const scrollToBottom = () => {
    setTimeout(() => {
      if (scrollRef.current) {
        scrollRef.current.scrollTop = scrollRef.current.scrollHeight;
      }
    }, 0);
  };

  const getWelcomeMessage = () => ({
    id: `welcome-${Date.now()}`,
    role: 'assistant',
    content: `Hi ${user?.fullName || 'there'}, ${t('chatWelcomeSuffix')}`,
    time: Date.now(),
  });

  const refreshSessions = async (preferredSessionId = '') => {
    const sessionsRes = await aiAPI.getChatSessions(5);
    const recentSessions = sessionsRes?.data?.sessions || [];
    setSessions(recentSessions);

    if (preferredSessionId) {
      const found = recentSessions.find((s) => s.sessionId === preferredSessionId);
      if (found) {
        setCurrentSessionId(preferredSessionId);
      }
    }

    return recentSessions;
  };

  const loadSessionHistory = async (sessionId) => {
    if (!sessionId) return;

    setLoadingHistory(true);
    try {
      const historyRes = await aiAPI.getChatHistory(40, sessionId);
      const resolvedSessionId = historyRes?.data?.sessionId || sessionId;
      const historyMessages = (historyRes?.data?.messages || []).map((m, idx) => ({
        id: `history-${resolvedSessionId}-${idx}-${m.timestamp || Date.now()}`,
        role: m.role,
        content: m.content,
        time: m.timestamp || Date.now(),
      }));

      setCurrentSessionId(resolvedSessionId);
      if (historyMessages.length > 0) {
        setMessages(historyMessages);
      } else {
        setMessages([getWelcomeMessage()]);
      }
    } catch {
      toast.error(t('couldNotLoadChatStream'));
    } finally {
      setLoadingHistory(false);
      scrollToBottom();
    }
  };

  const startNewChat = (silent = false) => {
    const newSessionId = generateSessionId();
    setCurrentSessionId(newSessionId);
    setMessages([getWelcomeMessage()]);
    if (!silent) {
      toast.success(t('newChatStarted'));
    }
    scrollToBottom();
  };

  const loadInitialData = async () => {
    setLoadingHistory(true);
    try {
      const [sessionsRes, suggestionsRes] = await Promise.all([
        aiAPI.getChatSessions(5),
        aiAPI.getChatSuggestions(),
      ]);

      const recentSessions = sessionsRes?.data?.sessions || [];
      setSessions(recentSessions);

      setSuggestions(suggestionsRes?.data?.suggestions || []);
      setAssistantRole(suggestionsRes?.data?.role || user?.role || 'SME');

      if (recentSessions.length > 0) {
        await loadSessionHistory(recentSessions[0].sessionId);
      } else {
        startNewChat(true);
      }
    } catch {
      startNewChat(true);
    } finally {
      setLoadingHistory(false);
      setBootstrapped(true);
      scrollToBottom();
    }
  };

  useEffect(() => {
    if (isOpen && !bootstrapped) {
      loadInitialData();
    }
  }, [isOpen, bootstrapped]);

  useEffect(() => {
    const handleMouseMove = (event) => {
      if (!resizeStateRef.current) return;

      const { startX, startY, startWidth, startHeight } = resizeStateRef.current;
      const viewportMaxWidth = Math.max(320, window.innerWidth - 24);
      const viewportMaxHeight = Math.max(360, window.innerHeight - 24);

      const nextWidth = Math.min(
        viewportMaxWidth,
        Math.max(320, startWidth - (event.clientX - startX))
      );
      const nextHeight = Math.min(
        viewportMaxHeight,
        Math.max(360, startHeight - (event.clientY - startY))
      );

      setPanelSize({ width: nextWidth, height: nextHeight });
    };

    const handleMouseUp = () => {
      if (!resizeStateRef.current) return;
      resizeStateRef.current = null;
      document.body.style.userSelect = '';
      document.body.style.cursor = '';
    };

    window.addEventListener('mousemove', handleMouseMove);
    window.addEventListener('mouseup', handleMouseUp);
    return () => {
      window.removeEventListener('mousemove', handleMouseMove);
      window.removeEventListener('mouseup', handleMouseUp);
      document.body.style.userSelect = '';
      document.body.style.cursor = '';
    };
  }, []);

  const startResizeFromTopLeft = (event) => {
    if (window.innerWidth <= 768) return;

    event.preventDefault();
    resizeStateRef.current = {
      startX: event.clientX,
      startY: event.clientY,
      startWidth: panelSize.width,
      startHeight: panelSize.height,
    };
    document.body.style.userSelect = 'none';
    document.body.style.cursor = 'nwse-resize';
  };

  const panelStyle =
    window.innerWidth <= 768
      ? undefined
      : {
          width: `${panelSize.width}px`,
          height: `${panelSize.height}px`,
        };

  const sendMessage = async () => {
    const content = input.trim();
    if (!content || loading) return;

    const activeSessionId = currentSessionId || generateSessionId();
    if (!currentSessionId) {
      setCurrentSessionId(activeSessionId);
    }

    if (content.length > 2000) {
      toast.error(t('messageTooLongMax2000'));
      return;
    }

    const userMessage = {
      id: `user-${Date.now()}`,
      role: 'user',
      content,
      time: Date.now(),
    };

    const updatedMessages = [...messages, userMessage];
    setMessages(updatedMessages);
    setInput('');
    setLoading(true);
    scrollToBottom();

    const assistantMessageId = `assistant-stream-${Date.now()}`;
    setMessages((prev) => [
      ...prev,
      {
        id: assistantMessageId,
        role: 'assistant',
        content: '',
        time: Date.now(),
      },
    ]);

    try {
      let streamed = false;
      try {
        await aiAPI.streamChat(
          {
            message: content,
            history: toApiHistory(updatedMessages),
            sessionId: activeSessionId,
          },
          {
            onDelta: (delta) => {
              streamed = true;
              setMessages((prev) =>
                prev.map((msg) =>
                  msg.id === assistantMessageId
                    ? { ...msg, content: `${msg.content || ''}${delta}` }
                    : msg
                )
              );
              scrollToBottom();
            },
            onDone: () => {
              scrollToBottom();
            },
          }
        );
      } catch (streamError) {
        if (streamed || streamError?.partial) {
          // Part of the answer already arrived (and was saved server-side): keep it, flag the interruption.
          streamed = true;
          setMessages((prev) =>
            prev.map((msg) =>
              msg.id === assistantMessageId
                ? { ...msg, content: `${msg.content || ''}\n\n_(${t('chatResponseInterrupted')})_` }
                : msg
            )
          );
          toast.error(streamError?.message || t('chatResponseInterrupted'));
        }
        // Otherwise nothing was streamed (proxy/network problem): fall back to the non-stream API below.
      }

      if (!streamed) {
        const res = await aiAPI.chat({
          message: content,
          history: toApiHistory(updatedMessages),
          sessionId: activeSessionId,
        });
        const resolvedSessionId = res?.data?.sessionId || activeSessionId;
        setCurrentSessionId(resolvedSessionId);
        const assistantText =
          res?.data?.answer?.trim() ||
          t('chatNoResponseNow');
        setMessages((prev) =>
          prev.map((msg) =>
            msg.id === assistantMessageId ? { ...msg, content: assistantText } : msg
          )
        );
        scrollToBottom();
      }

      await refreshSessions(activeSessionId);
    } catch (err) {
      const msg =
        (err?.response?.data?.detail || err?.response?.data?.message) ||
        err?.message ||
        t('chatRequestFailedTryAgain');
      toast.error(msg);
      setMessages((prev) => [
        ...prev.filter((m) => m.id !== assistantMessageId),
        {
          id: `assistant-error-${Date.now()}`,
          role: 'assistant',
          content: t('chatTroubleRespondingRetry'),
          time: Date.now(),
        },
      ]);
      scrollToBottom();
    } finally {
      setLoading(false);
    }
  };

  const onKeyDown = (e) => {
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault();
      sendMessage();
    }
  };

  return (
    <>
      {!isOpen && (
        <button
          type="button"
          className="chat-widget-launcher ai-assistant-widget-launcher"
          onClick={() => setIsOpen(true)}
          aria-label={t('openAiAssistant')}
          title={t('openAiAssistant')}
        >
          <span className="chat-widget-launcher-icon">💬</span>
          <span className="chat-widget-launcher-pulse" />
        </button>
      )}

      {isOpen && (
        <div className="chat-widget-panel ai-assistant-widget-panel" style={panelStyle}>
          <button
            type="button"
            className="chat-widget-top-left-resize"
            onMouseDown={startResizeFromTopLeft}
            aria-label={t('resizeAssistantFromTopLeft')}
            title={t('dragToResize')}
          />
          <div className="chat-widget-header">
            <div className="chat-widget-header-left">
              <div className="chat-widget-title">{t('aiAssistantTitle')}</div>
              <div className="chat-widget-subtitle">{t('mode')}: {assistantRole}</div>
            </div>
            <div className="chat-widget-header-actions">
              <select
                className="chat-widget-session-select"
                value={currentSessionId || ''}
                onChange={(e) => loadSessionHistory(e.target.value)}
                disabled={loadingHistory || loading}
                title={t('openOlderChatStream')}
              >
                {!currentSessionId && <option value="">{t('recentChatStreams')}</option>}
                {sessions.map((session) => (
                  <option key={session.sessionId} value={session.sessionId}>
                    {session.title}
                  </option>
                ))}
              </select>
              <button
                type="button"
                className="chat-widget-new-chat"
                onClick={() => startNewChat(false)}
                disabled={loadingHistory || loading}
                title={t('createNewChat')}
              >
                {t('new')}
              </button>
              <button
                type="button"
                className="chat-widget-minimize"
                onClick={() => setIsOpen(false)}
                aria-label={t('minimizeAssistant')}
                title={t('minimize')}
              >
                —
              </button>
            </div>
          </div>

          <div ref={scrollRef} className="chat-widget-body">
            {loadingHistory && (
              <div className="chat-widget-loading-history">{t('loadingConversationHistory')}</div>
            )}

            {messages.map((m) => {
              const isUser = m.role === 'user';
              return (
                <div
                  key={m.id}
                  className={`chat-widget-row ${isUser ? 'chat-widget-row-user' : 'chat-widget-row-assistant'}`}
                >
                  <div className={`chat-widget-bubble ${isUser ? 'chat-widget-bubble-user' : 'chat-widget-bubble-assistant'}`}>
                    {isUser
                      ? (m.content || (loading ? '...' : ''))
                      : (
                        <div className="chat-widget-markdown">
                          <ReactMarkdown
                            remarkPlugins={[remarkGfm]}
                            components={{
                              code({ inline, className, children, ...props }) {
                                const match = /language-(\w+)/.exec(className || '');
                                if (!inline && match) {
                                  return (
                                    <SyntaxHighlighter
                                      {...props}
                                      style={oneDark}
                                      language={match[1]}
                                      PreTag="div"
                                      customStyle={{
                                        margin: '0.45rem 0',
                                        borderRadius: '8px',
                                        padding: '0.62rem 0.68rem',
                                        border: '1px solid rgba(255, 255, 255, 0.08)',
                                        background: '#0f172a',
                                        fontSize: '12px',
                                      }}
                                    >
                                      {String(children).replace(/\n$/, '')}
                                    </SyntaxHighlighter>
                                  );
                                }

                                return (
                                  <code className={className} {...props}>
                                    {children}
                                  </code>
                                );
                              },
                            }}
                          >
                            {m.content || (loading ? '...' : '')}
                          </ReactMarkdown>
                        </div>
                      )}
                  </div>
                </div>
              );
            })}

            {loading && (
              <div className="chat-widget-row chat-widget-row-assistant">
                <div className="chat-widget-bubble chat-widget-bubble-assistant">{t('assistantThinking')}</div>
              </div>
            )}
          </div>

          <div className="chat-widget-footer">
            {suggestions.length > 0 && (
              <div className="chat-widget-suggestions">
                {suggestions.slice(0, 3).map((s, idx) => (
                  <button
                    key={`suggestion-${idx}`}
                    type="button"
                    className="chat-widget-chip"
                    onClick={() => setInput(s)}
                    disabled={loading || loadingHistory}
                  >
                    {s}
                  </button>
                ))}
              </div>
            )}

            <div className="chat-widget-input-row">
              <textarea
                rows={2}
                className="chat-widget-input"
                placeholder={t('askAnythingPlaceholder')}
                value={input}
                onChange={(e) => setInput(e.target.value)}
                onKeyDown={onKeyDown}
                disabled={loading || loadingHistory}
              />
              <button
                type="button"
                className="chat-widget-send"
                onClick={sendMessage}
                disabled={!canSend}
              >
                {t('send')}
              </button>
            </div>
          </div>
        </div>
      )}
    </>
  );
}
