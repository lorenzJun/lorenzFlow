package com.lorenzflow.app.ui;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.fragment.app.Fragment;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.lorenzflow.app.MainActivity;
import com.lorenzflow.app.R;
import com.lorenzflow.app.databinding.FragmentPomodoroBinding;
import com.lorenzflow.app.util.PomodoroTimer;

import java.util.Locale;

/**
 * Pomodoro screen. All countdown state lives in {@link PomodoroTimer}, so the
 * session keeps running when this fragment is replaced on a tab switch; the
 * fragment only renders state and re-attaches its listener in onViewCreated().
 */
public class PomodoroFragment extends Fragment implements PomodoroTimer.Listener {

    private static final String PREFS_NAME = "lorenz_flow_prefs";
    private static final String KEY_FOCUS = "pomodoro_focus_mins";
    private static final String KEY_SHORT = "pomodoro_short_mins";
    private static final String KEY_LONG = "pomodoro_long_mins";
    private static final String CHANNEL_ID = "Pomodoro_Timer";

    private FragmentPomodoroBinding binding;
    private PomodoroTimer timer;

    /** Guards the checked-state restore so it doesn't look like a user tap. */
    private boolean suppressModeCallback;

    private Ringtone activeRingtone;
    private Vibrator vibrator;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentPomodoroBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        vibrator = (Vibrator) requireContext().getSystemService(Context.VIBRATOR_SERVICE);
        timer = PomodoroTimer.get();

        loadDurations();
        setupModeSelection();
        setupControls();
        syncModeButtons();

        timer.setListener(this);
        render();
    }

    /** Pushes the durations saved in SharedPreferences into the session. */
    private void loadDurations() {
        if (getContext() == null) return;
        SharedPreferences prefs = requireContext()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        timer.setDurations(
                prefs.getInt(KEY_FOCUS, 25) * 60 * 1000L,
                prefs.getInt(KEY_SHORT, 5) * 60 * 1000L,
                prefs.getInt(KEY_LONG, 15) * 60 * 1000L);
    }

    private void setupModeSelection() {
        binding.toggleGroupModes.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (!isChecked || suppressModeCallback) return;

            if (checkedId == R.id.btn_mode_focus) {
                timer.setMode(PomodoroTimer.MODE_FOCUS);
            } else if (checkedId == R.id.btn_mode_short) {
                timer.setMode(PomodoroTimer.MODE_SHORT);
            } else if (checkedId == R.id.btn_mode_long) {
                timer.setMode(PomodoroTimer.MODE_LONG);
            }
            render();
        });
    }

    private void setupControls() {
        binding.btnStartPause.setOnClickListener(v -> {
            if (timer.isRunning()) {
                timer.pause();
            } else {
                timer.start();
            }
            render();
        });

        binding.btnReset.setOnClickListener(v -> {
            timer.reset();
            render();
        });

        binding.btnChangeDurations.setOnClickListener(v -> showEditDurationsDialog());
    }

    /** Mirrors the session's mode into the segmented control. */
    private void syncModeButtons() {
        suppressModeCallback = true;
        String mode = timer.getMode();
        if (PomodoroTimer.MODE_SHORT.equals(mode)) {
            binding.toggleGroupModes.check(R.id.btn_mode_short);
        } else if (PomodoroTimer.MODE_LONG.equals(mode)) {
            binding.toggleGroupModes.check(R.id.btn_mode_long);
        } else {
            binding.toggleGroupModes.check(R.id.btn_mode_focus);
        }
        suppressModeCallback = false;
    }

    private void showEditDurationsDialog() {
        SharedPreferences prefs = requireContext()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        int currentFocus = prefs.getInt(KEY_FOCUS, 25);
        int currentShort = prefs.getInt(KEY_SHORT, 5);
        int currentLong = prefs.getInt(KEY_LONG, 15);

        View dialogView = getLayoutInflater().inflate(R.layout.dialog_edit_durations, null);
        TextInputEditText editFocus = dialogView.findViewById(R.id.edit_focus_mins);
        TextInputEditText editShort = dialogView.findViewById(R.id.edit_short_mins);
        TextInputEditText editLong = dialogView.findViewById(R.id.edit_long_mins);

        editFocus.setText(String.valueOf(currentFocus));
        editShort.setText(String.valueOf(currentShort));
        editLong.setText(String.valueOf(currentLong));

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(getString(R.string.adjust_durations))
                .setView(dialogView)
                .setPositiveButton(R.string.save, (dialog, which) -> {
                    String focusStr = textOf(editFocus);
                    String shortStr = textOf(editShort);
                    String longStr = textOf(editLong);

                    if (focusStr.isEmpty() || shortStr.isEmpty() || longStr.isEmpty()) {
                        Toast.makeText(requireContext(), R.string.durations_empty, Toast.LENGTH_SHORT).show();
                        return;
                    }

                    try {
                        int newFocus = Integer.parseInt(focusStr);
                        int newShort = Integer.parseInt(shortStr);
                        int newLong = Integer.parseInt(longStr);

                        if (newFocus <= 0 || newShort <= 0 || newLong <= 0) {
                            Toast.makeText(requireContext(), R.string.durations_positive, Toast.LENGTH_SHORT).show();
                            return;
                        }

                        prefs.edit()
                                .putInt(KEY_FOCUS, newFocus)
                                .putInt(KEY_SHORT, newShort)
                                .putInt(KEY_LONG, newLong)
                                .apply();

                        timer.pause();
                        loadDurations();
                        timer.setMode(timer.getMode());
                        syncModeButtons();
                        render();
                        Toast.makeText(requireContext(), R.string.durations_updated, Toast.LENGTH_SHORT).show();

                    } catch (NumberFormatException e) {
                        Toast.makeText(requireContext(), R.string.invalid_number_format, Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    @Override
    public void onTick(long remainingMillis, long totalMillis) {
        render();
    }

    @Override
    public void onFinished() {
        render();
        triggerAlarm();
    }

    private void render() {
        if (binding == null || !isAdded()) return;

        long total = timer.getTotalMillis();
        long remaining = timer.getRemainingMillis();

        binding.textTimer.setText(formatTime(remaining));
        binding.pomodoroProgress.setProgress(total > 0 ? (int) ((remaining * 100) / total) : 0);
        binding.btnStartPause.setText(
                getString(timer.isRunning() ? R.string.pause : R.string.start));

        applyModeStyling();
    }

    private void applyModeStyling() {
        String mode = timer.getMode();
        if (PomodoroTimer.MODE_SHORT.equals(mode)) {
            binding.textStatus.setText(getString(R.string.pomodoro_status_short));
            binding.pomodoroProgress.setIndicatorColor(requireContext().getColor(R.color.lf_pastel_mint_deep));
        } else if (PomodoroTimer.MODE_LONG.equals(mode)) {
            binding.textStatus.setText(getString(R.string.pomodoro_status_long));
            binding.pomodoroProgress.setIndicatorColor(requireContext().getColor(R.color.lf_pastel_lilac_deep));
        } else {
            binding.textStatus.setText(getString(R.string.pomodoro_status_focus));
            binding.pomodoroProgress.setIndicatorColor(requireContext().getColor(R.color.lf_accent));
        }
    }

    private String formatTime(long millis) {
        int minutes = (int) (millis / 1000) / 60;
        int seconds = (int) (millis / 1000) % 60;
        return String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds);
    }

    private void triggerAlarm() {
        // 1. Play sound
        try {
            Uri alert = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
            if (alert == null) {
                alert = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
            }
            if (alert != null) {
                activeRingtone = RingtoneManager.getRingtone(requireContext(), alert);
                if (activeRingtone != null) {
                    activeRingtone.play();
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        // 2. Vibrate
        if (vibrator != null && vibrator.hasVibrator()) {
            long[] pattern = {0, 500, 500, 500, 500};
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createWaveform(pattern, 0));
            } else {
                vibrator.vibrate(pattern, 0);
            }
        }

        // 3. Show notification
        sendTimerNotification();

        // 4. Show alert dialog to turn off the alarm
        boolean isFocus = PomodoroTimer.MODE_FOCUS.equals(timer.getMode());
        if (getActivity() != null) {
            new MaterialAlertDialogBuilder(requireActivity())
                    .setTitle(getString(R.string.timer_finished))
                    .setMessage(getString(isFocus ? R.string.take_a_break : R.string.time_to_focus))
                    .setCancelable(false)
                    .setPositiveButton(R.string.ok, (dialog, which) -> stopAlarm())
                    .show();
        }

        // Automatically stop the alarm after 15 seconds to prevent continuous ringing
        if (binding != null) {
            binding.getRoot().postDelayed(this::stopAlarm, 15000);
        }
    }

    private void stopAlarm() {
        if (activeRingtone != null && activeRingtone.isPlaying()) {
            activeRingtone.stop();
        }
        if (vibrator != null) {
            vibrator.cancel();
        }
    }

    private void sendTimerNotification() {
        Context context = requireContext().getApplicationContext();
        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager == null) return;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    getString(R.string.pomodoro_notification_title),
                    NotificationManager.IMPORTANCE_HIGH
            );
            channel.enableVibration(true);
            channel.enableLights(true);
            manager.createNotificationChannel(channel);
        }

        Intent intent = new Intent(context, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                context, 999, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        boolean isFocus = PomodoroTimer.MODE_FOCUS.equals(timer.getMode());

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_timer)
                .setContentTitle(context.getString(R.string.pomodoro_notification_title))
                .setContentText(context.getString(
                        isFocus ? R.string.pomodoro_done_focus : R.string.pomodoro_done_break))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .setDefaults(NotificationCompat.DEFAULT_ALL);

        manager.notify(999, builder.build());
    }

    private static String textOf(@Nullable TextInputEditText editText) {
        return editText == null || editText.getText() == null
                ? ""
                : editText.getText().toString().trim();
    }

    @Override
    public void onDestroyView() {
        // Detach only: the session keeps running so switching tabs preserves it.
        timer.setListener(null);
        stopAlarm();
        super.onDestroyView();
        binding = null;
    }
}
