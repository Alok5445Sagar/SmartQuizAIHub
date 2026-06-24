const AUTH_TOKEN_KEY = 'token';
const AUTH_USER_KEY = 'user';
const AUTH_TAB_ID_KEY = 'auth-tab-id';

const createTabId = () => `auth-tab-${Date.now()}-${Math.random().toString(36).slice(2, 10)}`;

const resolveTabId = () => {
  let tabId = sessionStorage.getItem(AUTH_TAB_ID_KEY);
  if (!tabId) {
    tabId = createTabId();
    sessionStorage.setItem(AUTH_TAB_ID_KEY, tabId);
  }
  return tabId;
};

const scopedKey = (baseKey) => `${baseKey}:${resolveTabId()}`;

export const getAuthToken = () => {
  const value = sessionStorage.getItem(scopedKey(AUTH_TOKEN_KEY));
  if (value) return value;

  const legacy = localStorage.getItem(AUTH_TOKEN_KEY);
  if (!legacy) return null;

  sessionStorage.setItem(scopedKey(AUTH_TOKEN_KEY), legacy);
  localStorage.removeItem(AUTH_TOKEN_KEY);
  return legacy;
};

export const getAuthUserRaw = () => {
  const value = sessionStorage.getItem(scopedKey(AUTH_USER_KEY));
  if (value) return value;

  const legacy = localStorage.getItem(AUTH_USER_KEY);
  if (!legacy) return null;

  sessionStorage.setItem(scopedKey(AUTH_USER_KEY), legacy);
  localStorage.removeItem(AUTH_USER_KEY);
  return legacy;
};

export const getAuthUser = () => {
  const raw = getAuthUserRaw();
  if (!raw) return null;
  try {
    return JSON.parse(raw);
  } catch {
    return null;
  }
};

export const setAuthSession = ({ token, user }) => {
  if (token) sessionStorage.setItem(scopedKey(AUTH_TOKEN_KEY), token);
  if (user) sessionStorage.setItem(scopedKey(AUTH_USER_KEY), JSON.stringify(user));

  // Remove legacy shared keys so tabs remain isolated.
  localStorage.removeItem(AUTH_TOKEN_KEY);
  localStorage.removeItem(AUTH_USER_KEY);
};

export const clearAuthSession = () => {
  sessionStorage.removeItem(scopedKey(AUTH_TOKEN_KEY));
  sessionStorage.removeItem(scopedKey(AUTH_USER_KEY));
  localStorage.removeItem(AUTH_TOKEN_KEY);
  localStorage.removeItem(AUTH_USER_KEY);
};
