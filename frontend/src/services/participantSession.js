const PARTICIPANT_SESSION_KEY = 'participant-portal-session';
const PARTICIPANT_TAB_ID_KEY = 'participant-portal-tab-id';

const createTabId = () => `tab-${Date.now()}-${Math.random().toString(36).slice(2, 10)}`;

const resolveTabId = () => {
  let tabId = sessionStorage.getItem(PARTICIPANT_TAB_ID_KEY);
  if (!tabId) {
    tabId = createTabId();
    sessionStorage.setItem(PARTICIPANT_TAB_ID_KEY, tabId);
  }
  return tabId;
};

export const getParticipantSessionStorageKey = () => `${PARTICIPANT_SESSION_KEY}:${resolveTabId()}`;

export const getParticipantSession = () => {
  const scopedKey = getParticipantSessionStorageKey();
  const scopedValue = sessionStorage.getItem(scopedKey);
  if (scopedValue) {
    return scopedValue;
  }

  // Migration path from legacy key usage.
  const legacySession = sessionStorage.getItem(PARTICIPANT_SESSION_KEY) || localStorage.getItem(PARTICIPANT_SESSION_KEY);
  if (!legacySession) {
    return null;
  }

  sessionStorage.setItem(scopedKey, legacySession);
  sessionStorage.removeItem(PARTICIPANT_SESSION_KEY);
  localStorage.removeItem(PARTICIPANT_SESSION_KEY);
  return legacySession;
};

export const setParticipantSession = (value) => {
  const scopedKey = getParticipantSessionStorageKey();
  sessionStorage.setItem(scopedKey, value);
  sessionStorage.removeItem(PARTICIPANT_SESSION_KEY);
  localStorage.removeItem(PARTICIPANT_SESSION_KEY);
};

export const clearParticipantSession = () => {
  const scopedKey = getParticipantSessionStorageKey();
  sessionStorage.removeItem(scopedKey);
  sessionStorage.removeItem(PARTICIPANT_SESSION_KEY);
  localStorage.removeItem(PARTICIPANT_SESSION_KEY);
};
