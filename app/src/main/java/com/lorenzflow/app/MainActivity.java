package com.lorenzflow.app;

import android.Manifest;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.navigation.NavigationBarView;
import com.lorenzflow.app.databinding.ActivityMainBinding;
import com.lorenzflow.app.notification.NotificationHelper;
import com.lorenzflow.app.ui.AboutFragment;
import com.lorenzflow.app.ui.CalendarFragment;
import com.lorenzflow.app.ui.NotesFragment;
import com.lorenzflow.app.ui.PomodoroFragment;
import com.lorenzflow.app.ui.ProfileFragment;
import com.lorenzflow.app.ui.SettingsFragment;
import com.lorenzflow.app.ui.TasksFragment;

public class MainActivity extends AppCompatActivity {

    private ActivityMainBinding binding;

    private final ActivityResultLauncher<String> requestPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
                if (!isGranted) showNotificationsDeniedDialog();
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // Load and apply theme preference before super.onCreate()
        SharedPreferences prefs = getSharedPreferences("lorenz_flow_prefs", Context.MODE_PRIVATE);
        int themeMode = prefs.getInt("pref_theme", 0); // 0 = System, 1 = Light, 2 = Dark
        if (themeMode == 1) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
        } else if (themeMode == 2) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
        } else {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
        }

        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        setSupportActionBar(binding.toolbar);

        binding.bottomNavigation.setOnItemSelectedListener(new NavigationBarView.OnItemSelectedListener() {
            @Override
            public boolean onNavigationItemSelected(@NonNull MenuItem item) {
                Fragment selectedFragment = null;
                int itemId = item.getItemId();
                if (itemId == R.id.nav_tasks) {
                    selectedFragment = new TasksFragment();
                } else if (itemId == R.id.nav_notes) {
                    selectedFragment = new NotesFragment();
                } else if (itemId == R.id.nav_calendar) {
                    selectedFragment = new CalendarFragment();
                } else if (itemId == R.id.nav_pomodoro) {
                    selectedFragment = new PomodoroFragment();
                } else if (itemId == R.id.nav_settings) {
                    selectedFragment = new SettingsFragment();
                }

                if (selectedFragment != null) {
                    updateNavIcons(itemId);
                    getSupportFragmentManager().beginTransaction()
                            .replace(R.id.fragment_container, selectedFragment)
                            .commit();
                    return true;
                }
                return false;
            }
        });

        // Set default fragment on first launch
        if (savedInstanceState == null) {
            binding.bottomNavigation.setSelectedItemId(R.id.nav_tasks);
        } else {
            // No state restore listener fires — sync the filled/outlined icons
            updateNavIcons(binding.bottomNavigation.getSelectedItemId());
        }

        checkNotificationPermission();
    }

    /**
     * Called when POST_NOTIFICATIONS was denied. Explains why reminders matter
     * and offers a shortcut to the app's system notification settings.
     */
    private void showNotificationsDeniedDialog() {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.notifications_denied_title)
                .setMessage(R.string.notifications_denied_message)
                .setPositiveButton(R.string.open_notification_settings,
                        (dialog, which) -> NotificationHelper.openNotificationSettings(this))
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    /**
     * Swaps each tab's icon between filled (selected) and outlined
     * (unselected) variants. The Settings tab uses a single glyph.
     */
    private void updateNavIcons(int selectedId) {
        Menu menu = binding.bottomNavigation.getMenu();
        for (int i = 0; i < menu.size(); i++) {
            MenuItem item = menu.getItem(i);
            int id = item.getItemId();
            int iconRes;
            if (id == R.id.nav_tasks) {
                iconRes = id == selectedId
                        ? R.drawable.ic_nav_tasks_filled : R.drawable.ic_nav_tasks_outline;
            } else if (id == R.id.nav_notes) {
                iconRes = id == selectedId
                        ? R.drawable.ic_nav_notes_filled : R.drawable.ic_nav_notes_outline;
            } else if (id == R.id.nav_calendar) {
                iconRes = id == selectedId
                        ? R.drawable.ic_nav_calendar_filled : R.drawable.ic_nav_calendar_outline;
            } else if (id == R.id.nav_pomodoro) {
                iconRes = id == selectedId
                        ? R.drawable.ic_nav_timer_filled : R.drawable.ic_nav_timer_outline;
            } else {
                // Settings keeps one glyph; color + label weight mark the state
                continue;
            }
            item.setIcon(iconRes);
        }
    }

    private void checkNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS);
            }
        }
    }
}
