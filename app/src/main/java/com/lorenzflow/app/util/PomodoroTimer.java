package com.lorenzflow.app.util;

import android.os.CountDownTimer;

/**
 * Process-scoped Pomodoro session state.
 *
 * MainActivity swaps tabs with replace(), so a fragment instance — and any
 * CountDownTimer it owned — is destroyed on every tab switch. Holding the
 * session here keeps the countdown running across tabs: the fragment detaches
 * its Listener in onDestroyView() and re-binds to the same state when the tab
 * is opened again.
 */
public final class PomodoroTimer {

    public static final String MODE_FOCUS = "Focus";
    public static final String MODE_SHORT = "Short";
    public static final String MODE_LONG = "Long";

    private static final long TICK_MS = 100L;

    /** Callbacks for whichever fragment is currently showing the timer. */
    public interface Listener {
        void onTick(long remainingMillis, long totalMillis);

        void onFinished();
    }

    private static PomodoroTimer instance;

    private String mode = MODE_FOCUS;
    private long focusMillis = 25 * 60 * 1000L;
    private long shortBreakMillis = 5 * 60 * 1000L;
    private long longBreakMillis = 15 * 60 * 1000L;

    private long totalMillis = focusMillis;
    private long remainingMillis = focusMillis;

    private boolean running = false;
    private CountDownTimer countDownTimer;
    private Listener listener;

    private PomodoroTimer() {
    }

    public static synchronized PomodoroTimer get() {
        if (instance == null) {
            instance = new PomodoroTimer();
        }
        return instance;
    }

    /** Applies durations from SharedPreferences; a running session is left alone. */
    public void setDurations(long focus, long shortBreak, long longBreak) {
        focusMillis = focus;
        shortBreakMillis = shortBreak;
        longBreakMillis = longBreak;
        if (!running && remainingMillis == totalMillis) {
            totalMillis = totalFor(mode);
            remainingMillis = totalMillis;
            notifyTick();
        }
    }

    public long totalFor(String newMode) {
        if (MODE_SHORT.equals(newMode)) {
            return shortBreakMillis;
        }
        if (MODE_LONG.equals(newMode)) {
            return longBreakMillis;
        }
        return focusMillis;
    }

    public String getMode() {
        return mode;
    }

    /** Switches mode and resets the countdown for it. */
    public void setMode(String newMode) {
        stopCountdown();
        running = false;
        mode = newMode;
        totalMillis = totalFor(newMode);
        remainingMillis = totalMillis;
        notifyTick();
    }

    public boolean isRunning() {
        return running;
    }

    public long getTotalMillis() {
        return totalMillis;
    }

    public long getRemainingMillis() {
        return remainingMillis;
    }

    public void setListener(Listener newListener) {
        listener = newListener;
    }

    public void start() {
        if (running) return;
        if (remainingMillis <= 0) {
            remainingMillis = totalMillis;
        }
        running = true;

        countDownTimer = new CountDownTimer(remainingMillis, TICK_MS) {
            @Override
            public void onTick(long millisUntilFinished) {
                remainingMillis = millisUntilFinished;
                notifyTick();
            }

            @Override
            public void onFinish() {
                running = false;
                remainingMillis = 0;
                notifyTick();
                if (listener != null) {
                    listener.onFinished();
                }
            }
        }.start();

        notifyTick();
    }

    public void pause() {
        stopCountdown();
        running = false;
        notifyTick();
    }

    public void reset() {
        stopCountdown();
        running = false;
        remainingMillis = totalMillis;
        notifyTick();
    }

    private void stopCountdown() {
        if (countDownTimer != null) {
            countDownTimer.cancel();
            countDownTimer = null;
        }
    }

    private void notifyTick() {
        if (listener != null) {
            listener.onTick(remainingMillis, totalMillis);
        }
    }
}
