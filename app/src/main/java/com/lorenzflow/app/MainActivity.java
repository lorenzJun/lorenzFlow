package com.lorenzflow.app;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.MenuItem;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.fragment.app.Fragment;

import com.google.android.material.navigation.NavigationBarView;
import com.lorenzflow.app.databinding.ActivityMainBinding;
import com.lorenzflow.app.ui.AboutFragment;
import com.lorenzflow.app.ui.CalendarFragment;
import com.lorenzflow.app.ui.NotesFragment;
import com.lorenzflow.app.ui.PomodoroFragment;
import com.lorenzflow.app.ui.ProfileFragment;
import com.lorenzflow.app.ui.SettingsFragment;
import com.lorenzflow.app.ui.TasksFragment;

public class MainActivity extends AppCompatActivity {

    private ActivityMainBinding binding;

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
        }

        checkNotificationPermission();
    }

    private final androidx.activity.result.ActivityResultLauncher<String> requestPermissionLauncher =
            registerForActivityResult(new androidx.activity.result.contract.ActivityResultContracts.RequestPermission(), isGranted -> {
                // Handle permission result if needed
            });

    private void checkNotificationPermission() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            if (androidx.core.content.ContextCompat.checkSelfPermission(
                    this, android.Manifest.permission.POST_NOTIFICATIONS) !=
                    android.content.pm.PackageManager.PERMISSION_GRANTED) {
                requestPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS);
            }
        }
    }
}
