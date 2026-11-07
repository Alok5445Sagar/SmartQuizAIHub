import { act, renderHook } from '@testing-library/react';
import useAutoRefresh, { REFRESH_MS } from '../../hooks/useAutoRefresh';

const setVisibility = (state) => {
  Object.defineProperty(document, 'visibilityState', { configurable: true, get: () => state });
  document.dispatchEvent(new Event('visibilitychange'));
};

describe('useAutoRefresh', () => {
  beforeEach(() => {
    jest.useFakeTimers();
    Object.defineProperty(document, 'visibilityState', { configurable: true, get: () => 'visible' });
  });

  afterEach(() => {
    jest.useRealTimers();
  });

  test('defaults to a 15 second interval', () => {
    expect(REFRESH_MS).toBe(15000);
  });

  test('refreshes on each interval while visible', () => {
    const refresh = jest.fn();
    renderHook(() => useAutoRefresh(refresh, { intervalMs: 5000 }));

    act(() => { jest.advanceTimersByTime(4999); });
    expect(refresh).not.toHaveBeenCalled();
    act(() => { jest.advanceTimersByTime(1); });
    expect(refresh).toHaveBeenCalledTimes(1);
    act(() => { jest.advanceTimersByTime(10000); });
    expect(refresh).toHaveBeenCalledTimes(3);
  });

  test('stops while the tab is hidden and refreshes once on return', () => {
    const refresh = jest.fn();
    renderHook(() => useAutoRefresh(refresh, { intervalMs: 5000 }));

    act(() => { setVisibility('hidden'); });
    act(() => { jest.advanceTimersByTime(60000); });
    expect(refresh).not.toHaveBeenCalled();

    act(() => {
      setVisibility('visible');
      window.dispatchEvent(new Event('focus'));
    });
    expect(refresh).toHaveBeenCalledTimes(1);

    act(() => { jest.advanceTimersByTime(5000); });
    expect(refresh).toHaveBeenCalledTimes(2);
  });

  test('uses the latest callback and does nothing when disabled', () => {
    const first = jest.fn();
    const second = jest.fn();
    const { rerender } = renderHook(({ cb }) => useAutoRefresh(cb, { intervalMs: 5000 }), { initialProps: { cb: first } });
    rerender({ cb: second });
    act(() => { jest.advanceTimersByTime(5000); });
    expect(first).not.toHaveBeenCalled();
    expect(second).toHaveBeenCalledTimes(1);

    const disabled = jest.fn();
    renderHook(() => useAutoRefresh(disabled, { enabled: false, intervalMs: 5000 }));
    act(() => { jest.advanceTimersByTime(20000); });
    expect(disabled).not.toHaveBeenCalled();
  });

  test('stops after unmount', () => {
    const refresh = jest.fn();
    const { unmount } = renderHook(() => useAutoRefresh(refresh, { intervalMs: 5000 }));
    unmount();
    act(() => { jest.advanceTimersByTime(20000); });
    expect(refresh).not.toHaveBeenCalled();
  });
});
