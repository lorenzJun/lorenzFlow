package com.lorenzflow.app.ui;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.RadioGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.fragment.app.Fragment;

import com.lorenzflow.app.R;
import com.lorenzflow.app.databinding.FragmentSettingsBinding;

public class SettingsFragment extends Fragment {

    private FragmentSettingsBinding binding;
    private SharedPreferences prefs;

    private static final String PREFS_NAME = "lorenz_flow_prefs";
    private static final String PREF_THEME_KEY = "pref_theme"; // 0 = System, 1 = Light, 2 = Dark
    private static final String PREF_NOTIF_KEY = "pref_notifications";

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentSettingsBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        prefs = requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);

        setupThemeSelection();
        setupNotificationSelection();
        setupNavigationRows();
    }

    private void setupNavigationRows() {
        binding.rowProfileSettings.setOnClickListener(v -> {
            getParentFragmentManager().beginTransaction()
                    .replace(R.id.fragment_container, new ProfileFragment())
                    .addToBackStack(null)
                    .commit();
        });

        binding.rowPasswordManager.setOnClickListener(v -> {
            getParentFragmentManager().beginTransaction()
                    .replace(R.id.fragment_container, new PasswordManagerFragment())
                    .addToBackStack(null)
                    .commit();
        });

        binding.rowAboutSettings.setOnClickListener(v -> {
            getParentFragmentManager().beginTransaction()
                    .replace(R.id.fragment_container, new AboutFragment())
                    .addToBackStack(null)
                    .commit();
        });
    }

    private void setupThemeSelection() {
        int savedTheme = prefs.getInt(PREF_THEME_KEY, 0); // Default: 0 (System)

        // Select the active theme radio button
        if (savedTheme == 1) {
            binding.radioThemeLight.setChecked(true);
        } else if (savedTheme == 2) {
            binding.radioThemeDark.setChecked(true);
        } else {
            binding.radioThemeSystem.setChecked(true);
        }

        binding.radioGroupTheme.setOnCheckedChangeListener((group, checkedId) -> {
            int selectedTheme = 0; // default System
            int nightMode = AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM;

            if (checkedId == R.id.radio_theme_light) {
                selectedTheme = 1;
                nightMode = AppCompatDelegate.MODE_NIGHT_NO;
            } else if (checkedId == R.id.radio_theme_dark) {
                selectedTheme = 2;
                nightMode = AppCompatDelegate.MODE_NIGHT_YES;
            }

            // Save preference
            prefs.edit().putInt(PREF_THEME_KEY, selectedTheme).apply();

            // Apply immediately
            AppCompatDelegate.setDefaultNightMode(nightMode);
        });
    }

    private void setupNotificationSelection() {
        boolean savedNotifState = prefs.getBoolean(PREF_NOTIF_KEY, true); // Default: true

        binding.switchNotifications.setChecked(savedNotifState);

        binding.switchNotifications.setOnCheckedChangeListener((buttonView, isChecked) -> {
            // Save preference
            prefs.edit().putBoolean(PREF_NOTIF_KEY, isChecked).apply();
        });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
