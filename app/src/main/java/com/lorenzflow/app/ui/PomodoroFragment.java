package com.lorenzflow.app.ui;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.CountDownTimer;
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
import com.lorenzflow.app.MainActivity;
import com.lorenzflow.app.R;
import com.lorenzflow.app.databinding.FragmentPomodoroBinding;

import java.util.Locale;

public class PomodoroFragment extends Fragment {

    private FragmentPomodoroBinding binding;

    private long focusTimeMillis;
    private long shortBreakTimeMillis;
    private long longBreakTimeMillis;

    private long totalTimeMillis;
    private long timeLeftMillis;

    private CountDownTimer countDownTimer;
    private boolean isTimerRunning = false;
    private String currentMode = "Focus"; // "Focus", "Short", "Long"

    private Ringtone activeRingtone;
    private Vibrator vibrator;

    private static final String CHANNEL_ID = "Pomodoro_Timer";
    private static final String CHANNEL_NAME = "Pomodoro Timer";

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

        loadDurations();
        timeLeftMillis = totalTimeMillis;

        setupModeSelection();
        setupControls();
        updateTimerText();
        updateProgressBar();
    }

    private void loadDurations() {
        if (getContext() == null) return;
        android.content.SharedPreferences prefs = requireContext().getSharedPreferences("lorenz_flow_prefs", Context.MODE_PRIVATE);
        focusTimeMillis = prefs.getInt("pomodoro_focus_mins", 25) * 60 * 1000L;
        shortBreakTimeMillis = prefs.getInt("pomodoro_short_mins", 5) * 60 * 1000L;
        longBreakTimeMillis = prefs.getInt("pomodoro_long_mins", 15) * 60 * 1000L;

        if (currentMode.equals("Focus")) {
            totalTimeMillis = focusTimeMillis;
        } else if (currentMode.equals("Short")) {
            totalTimeMillis = shortBreakTimeMillis;
        } else {
            totalTimeMillis = longBreakTimeMillis;
        }
    }

    private void setupModeSelection() {
        binding.toggleGroupModes.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (!isChecked) return;

            // Stop existing timer
            if (isTimerRunning) {
                pauseTimer();
            }

            if (checkedId == R.id.btn_mode_focus) {
                currentMode = "Focus";
                totalTimeMillis = focusTimeMillis;
                binding.textStatus.setText("Focus Session");
                binding.pomodoroProgress.setIndicatorColor(requireContext().getColor(R.color.ios_blue));
            } else if (checkedId == R.id.btn_mode_short) {
                currentMode = "Short";
                totalTimeMillis = shortBreakTimeMillis;
                binding.textStatus.setText("Short Break");
                binding.pomodoroProgress.setIndicatorColor(requireContext().getColor(R.color.ios_green));
            } else if (checkedId == R.id.btn_mode_long) {
                currentMode = "Long";
                totalTimeMillis = longBreakTimeMillis;
                binding.textStatus.setText("Long Break");
                binding.pomodoroProgress.setIndicatorColor(requireContext().getColor(R.color.ios_purple));
            }

            timeLeftMillis = totalTimeMillis;
            updateTimerText();
            updateProgressBar();
            binding.btnStartPause.setText("Start");
        });
    }

    private void setupControls() {
        binding.btnStartPause.setOnClickListener(v -> {
            if (isTimerRunning) {
                pauseTimer();
            } else {
                startTimer();
            }
        });

        binding.btnReset.setOnClickListener(v -> resetTimer());

        binding.btnChangeDurations.setOnClickListener(v -> showEditDurationsDialog());
    }

    private void showEditDurationsDialog() {
        android.content.SharedPreferences prefs = requireContext().getSharedPreferences("lorenz_flow_prefs", Context.MODE_PRIVATE);
        int currentFocus = prefs.getInt("pomodoro_focus_mins", 25);
        int currentShort = prefs.getInt("pomodoro_short_mins", 5);
        int currentLong = prefs.getInt("pomodoro_long_mins", 15);

        android.view.View dialogView = getLayoutInflater().inflate(R.layout.dialog_edit_durations, null);
        com.google.android.material.textfield.TextInputEditText editFocus = dialogView.findViewById(R.id.edit_focus_mins);
        com.google.android.material.textfield.TextInputEditText editShort = dialogView.findViewById(R.id.edit_short_mins);
        com.google.android.material.textfield.TextInputEditText editLong = dialogView.findViewById(R.id.edit_long_mins);

        editFocus.setText(String.valueOf(currentFocus));
        editShort.setText(String.valueOf(currentShort));
        editLong.setText(String.valueOf(currentLong));

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Adjust Durations")
                .setView(dialogView)
                .setPositiveButton("Save", (dialog, which) -> {
                    String focusStr = editFocus.getText() != null ? editFocus.getText().toString().trim() : "";
                    String shortStr = editShort.getText() != null ? editShort.getText().toString().trim() : "";
                    String longStr = editLong.getText() != null ? editLong.getText().toString().trim() : "";

                    if (focusStr.isEmpty() || shortStr.isEmpty() || longStr.isEmpty()) {
                        Toast.makeText(requireContext(), "Durations cannot be empty", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    try {
                        int newFocus = Integer.parseInt(focusStr);
                        int newShort = Integer.parseInt(shortStr);
                        int newLong = Integer.parseInt(longStr);

                        if (newFocus <= 0 || newShort <= 0 || newLong <= 0) {
                            Toast.makeText(requireContext(), "Durations must be greater than 0", Toast.LENGTH_SHORT).show();
                            return;
                        }

                        prefs.edit()
                                .putInt("pomodoro_focus_mins", newFocus)
                                .putInt("pomodoro_short_mins", newShort)
                                .putInt("pomodoro_long_mins", newLong)
                                .apply();

                        // Pause and reload
                        pauseTimer();
                        loadDurations();
                        resetTimer();
                        Toast.makeText(requireContext(), "Durations updated", Toast.LENGTH_SHORT).show();

                    } catch (NumberFormatException e) {
                        Toast.makeText(requireContext(), "Invalid number format", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void startTimer() {
        countDownTimer = new CountDownTimer(timeLeftMillis, 100) {
            @Override
            public void onTick(long millisUntilFinished) {
                timeLeftMillis = millisUntilFinished;
                updateTimerText();
                updateProgressBar();
            }

            @Override
            public void onFinish() {
                isTimerRunning = false;
                timeLeftMillis = 0;
                updateTimerText();
                updateProgressBar();
                binding.btnStartPause.setText("Start");
                triggerAlarm();
            }
        }.start();

        isTimerRunning = true;
        binding.btnStartPause.setText("Pause");
    }

    private void pauseTimer() {
        if (countDownTimer != null) {
            countDownTimer.cancel();
        }
        isTimerRunning = false;
        binding.btnStartPause.setText("Start");
    }

    private void resetTimer() {
        pauseTimer();
        timeLeftMillis = totalTimeMillis;
        updateTimerText();
        updateProgressBar();
    }

    private void updateTimerText() {
        int minutes = (int) (timeLeftMillis / 1000) / 60;
        int seconds = (int) (timeLeftMillis / 1000) % 60;
        String timeStr = String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds);
        binding.textTimer.setText(timeStr);
    }

    private void updateProgressBar() {
        int progress = (int) ((timeLeftMillis * 100) / totalTimeMillis);
        binding.pomodoroProgress.setProgress(progress);
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
        String title = "Timer Finished!";
        String message = currentMode.equals("Focus") ? "Time to take a break!" : "Time to focus!";

        if (getActivity() != null) {
            new MaterialAlertDialogBuilder(requireActivity())
                    .setTitle(title)
                    .setMessage(message)
                    .setCancelable(false)
                    .setPositiveButton("OK", (dialog, which) -> stopAlarm())
                    .show();
        }

        // Automatically stop alarm after 15 seconds to prevent continuous ringing
        binding.getRoot().postDelayed(this::stopAlarm, 15000);
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
                    CHANNEL_NAME,
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

        String message = currentMode.equals("Focus") ? "Focus session completed. Take a break!" : "Break completed. Ready to focus?";

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_timer)
                .setContentTitle("Pomodoro Timer")
                .setContentText(message)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .setDefaults(NotificationCompat.DEFAULT_ALL);

        manager.notify(999, builder.build());
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        pauseTimer();
        stopAlarm();
        binding = null;
    }
}
