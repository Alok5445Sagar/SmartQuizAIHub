import { useEffect, useRef } from 'react';

const parseRefreshMs = (value) => {
  const ms = Number(value);
  return Number.isFinite(ms) && ms >= 1000 ? ms : 15000;
};

// Background refresh interval for list pages. Set REACT_APP_REFRESH_MS at build time to change it.
export const REFRESH_MS = parseRefreshMs(process.env.REACT_APP_REFRESH_MS);

// Returning to the tab fires both 'focus' and 'visibilitychange'; refresh only once for both.
const MIN_GAP_MS = 1000;

/**
 * Calls `refresh` every `intervalMs` while the page is visible. The timer stops while the tab is hidden,
 * and returning to the tab (or focusing the window) refreshes once right away. `refresh` may change on
 * every render; the latest one is always used.
 */
export default function useAutoRefresh(refresh, { enabled = true, intervalMs = REFRESH_MS } = {}) {
  const refreshRef = useRef(refresh);
  refreshRef.current = refresh;

  useEffect(() => {
    if (!enabled) return undefined;

    let timer = null;
    let lastRunAt = 0;

    const run = () => {
      if (document.visibilityState === 'hidden') return;
      const now = Date.now();
      if (now - lastRunAt < MIN_GAP_MS) return;
      lastRunAt = now;
      refreshRef.current();
    };
    const stop = () => {
      if (timer !== null) {
        clearInterval(timer);
        timer = null;
      }
    };
    const start = () => {
      stop();
      timer = setInterval(run, intervalMs);
    };
    const onVisibility = () => {
      if (document.visibilityState === 'hidden') {
        stop();
      } else {
        run();
        start();
      }
    };

    if (document.visibilityState !== 'hidden') {
      start();
    }
    document.addEventListener('visibilitychange', onVisibility);
    window.addEventListener('focus', run);
    return () => {
      stop();
      document.removeEventListener('visibilitychange', onVisibility);
      window.removeEventListener('focus', run);
    };
  }, [enabled, intervalMs]);
}
