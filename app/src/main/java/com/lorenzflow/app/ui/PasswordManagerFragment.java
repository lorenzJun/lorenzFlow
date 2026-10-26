package com.lorenzflow.app.ui;

import android.app.Activity;
import android.app.KeyguardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.lorenzflow.app.R;
import com.lorenzflow.app.data.AppDatabase;
import com.lorenzflow.app.data.PasswordDao;
import com.lorenzflow.app.data.PasswordEntry;
import com.lorenzflow.app.databinding.DialogAddPasswordBinding;
import com.lorenzflow.app.databinding.FragmentPasswordManagerBinding;
import com.lorenzflow.app.util.SecurityUtil;

import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.List;
import java.util.concurrent.Executors;

public class PasswordManagerFragment extends Fragment implements PasswordAdapter.OnPasswordActionListener {

    private FragmentPasswordManagerBinding binding;
    private PasswordAdapter adapter;
    private PasswordDao passwordDao;
    private SharedPreferences prefs;
    private boolean isAuthenticated = false;

    private final ActivityResultLauncher<Intent> authLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == Activity.RESULT_OK) {
                    isAuthenticated = true;
                    loadPasswords();
                } else {
                    Toast.makeText(requireContext(), R.string.auth_failed, Toast.LENGTH_SHORT).show();
                    getParentFragmentManager().popBackStack();
                }
            });

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentPasswordManagerBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        passwordDao = AppDatabase.getInstance(requireContext()).passwordDao();
        prefs = requireContext().getSharedPreferences("lorenz_flow_prefs", Context.MODE_PRIVATE);

        setupRecyclerView();
        setupSearch();
        setupFab();

        if (!isAuthenticated) {
            authenticateUser();
        } else {
            loadPasswords();
        }
    }

    private void setupRecyclerView() {
        adapter = new PasswordAdapter(this);
        binding.recyclerPasswords.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.recyclerPasswords.setAdapter(adapter);
    }

    private void setupSearch() {
        binding.editSearchPasswords.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                adapter.filter(s.toString());
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });
    }

    private void setupFab() {
        binding.fabAddPassword.setOnClickListener(v -> showAddPasswordDialog());
    }

    private void authenticateUser() {
        KeyguardManager km = (KeyguardManager) requireContext().getSystemService(Context.KEYGUARD_SERVICE);
        if (km != null && km.isDeviceSecure()) {
            Intent authIntent = km.createConfirmDeviceCredentialIntent(
                    getString(R.string.pm_lock_title), getString(R.string.pm_lock_desc));
            if (authIntent != null) {
                authLauncher.launch(authIntent);
            } else {
                checkCustomAppPin();
            }
        } else {
            checkCustomAppPin();
        }
    }

    private void checkCustomAppPin() {
        String savedPinHash = prefs.getString("pm_master_pin_hash", "");
        if (savedPinHash.isEmpty()) {
            // Setup master PIN
            showSetupPinDialog();
        } else {
            // Enter master PIN
            showEnterPinDialog(savedPinHash);
        }
    }

    private void showSetupPinDialog() {
        android.view.View dialogView = getLayoutInflater().inflate(R.layout.dialog_master_pin, null);
        com.google.android.material.textfield.TextInputLayout layoutMasterPin =
                dialogView.findViewById(R.id.layout_master_pin);
        com.google.android.material.textfield.TextInputEditText editPin =
                dialogView.findViewById(R.id.edit_master_pin);

        layoutMasterPin.setHint(getString(R.string.pm_pin_hint_setup));

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(getString(R.string.secure_setup_title))
                .setMessage(getString(R.string.secure_setup_message))
                .setView(dialogView)
                .setCancelable(false)
                .setPositiveButton(getString(R.string.set_pin), (dialog, which) -> {
                    String pin = editPin.getText() != null ? editPin.getText().toString().trim() : "";
                    if (pin.length() < 4) {
                        Toast.makeText(requireContext(), R.string.pin_too_short, Toast.LENGTH_SHORT).show();
                        getParentFragmentManager().popBackStack();
                        return;
                    }
                    String hash = hashString(pin);
                    if (hash != null) {
                        prefs.edit().putString("pm_master_pin_hash", hash).apply();
                        isAuthenticated = true;
                        loadPasswords();
                        Toast.makeText(requireContext(), R.string.pin_set_success, Toast.LENGTH_SHORT).show();
                    } else {
                        getParentFragmentManager().popBackStack();
                    }
                })
                .setNegativeButton(R.string.cancel, (dialog, which) -> getParentFragmentManager().popBackStack())
                .show();
    }

    private void showEnterPinDialog(String savedHash) {
        android.view.View dialogView = getLayoutInflater().inflate(R.layout.dialog_master_pin, null);
        com.google.android.material.textfield.TextInputLayout layoutMasterPin =
                dialogView.findViewById(R.id.layout_master_pin);
        com.google.android.material.textfield.TextInputEditText editPin =
                dialogView.findViewById(R.id.edit_master_pin);

        layoutMasterPin.setHint(getString(R.string.pm_pin_hint_enter));

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(getString(R.string.unlock_password_manager))
                .setView(dialogView)
                .setCancelable(false)
                .setPositiveButton(getString(R.string.unlock), (dialog, which) -> {
                    String pin = editPin.getText() != null ? editPin.getText().toString().trim() : "";
                    String hash = hashString(pin);
                    if (hash != null && hash.equals(savedHash)) {
                        isAuthenticated = true;
                        loadPasswords();
                    } else {
                        Toast.makeText(requireContext(), R.string.incorrect_pin, Toast.LENGTH_SHORT).show();
                        getParentFragmentManager().popBackStack();
                    }
                })
                .setNegativeButton(R.string.cancel, (dialog, which) -> getParentFragmentManager().popBackStack())
                .show();
    }

    private String hashString(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes("UTF-8"));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    private void loadPasswords() {
        Executors.newSingleThreadExecutor().execute(() -> {
            List<PasswordEntry> list = passwordDao.getAllPasswords();
            if (getActivity() == null) return;
            requireActivity().runOnUiThread(() -> {
                adapter.setPasswords(list);
                binding.emptyStatePasswords.setVisibility(list.isEmpty() ? View.VISIBLE : View.GONE);
                binding.recyclerPasswords.setVisibility(list.isEmpty() ? View.GONE : View.VISIBLE);
            });
        });
    }

    private void showAddPasswordDialog() {
        DialogAddPasswordBinding dialogBinding = DialogAddPasswordBinding.inflate(getLayoutInflater());

        dialogBinding.btnGeneratePassword.setOnClickListener(v -> {
            String securePass = generateSecurePassword();
            dialogBinding.editPasswordVal.setText(securePass);
            Toast.makeText(requireContext(), R.string.secure_password_generated, Toast.LENGTH_SHORT).show();
        });

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(getString(R.string.add_credential))
                .setView(dialogBinding.getRoot())
                .setPositiveButton("Save", (dialog, which) -> {
                    String title = dialogBinding.editPasswordTitle.getText() != null ? dialogBinding.editPasswordTitle.getText().toString().trim() : "";
                    String username = dialogBinding.editPasswordUsername.getText() != null ? dialogBinding.editPasswordUsername.getText().toString().trim() : "";
                    String password = dialogBinding.editPasswordVal.getText() != null ? dialogBinding.editPasswordVal.getText().toString().trim() : "";

                    if (title.isEmpty() || username.isEmpty() || password.isEmpty()) {
                        Toast.makeText(requireContext(), R.string.pm_fields_required, Toast.LENGTH_SHORT).show();
                        return;
                    }

                    SecurityUtil.EncryptedData encrypted = SecurityUtil.encrypt(password);
                    if (encrypted != null) {
                        PasswordEntry entry = new PasswordEntry();
                        entry.setTitle(title);
                        entry.setUsername(username);
                        entry.setEncryptedPassword(encrypted.encryptedText);
                        entry.setIv(encrypted.iv);

                        Executors.newSingleThreadExecutor().execute(() -> {
                            passwordDao.insert(entry);
                            if (getActivity() != null) {
                                requireActivity().runOnUiThread(this::loadPasswords);
                            }
                        });
                    } else {
                        Toast.makeText(requireContext(), R.string.error_encrypting_password, Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private String generateSecurePassword() {
        String upper = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
        String lower = "abcdefghijklmnopqrstuvwxyz";
        String digits = "0123456789";
        String punctuation = "!@#$%^&*()_-+=<>?";
        String all = upper + lower + digits + punctuation;
        SecureRandom random = new SecureRandom();
        StringBuilder sb = new StringBuilder();
        
        sb.append(upper.charAt(random.nextInt(upper.length())));
        sb.append(lower.charAt(random.nextInt(lower.length())));
        sb.append(digits.charAt(random.nextInt(digits.length())));
        sb.append(punctuation.charAt(random.nextInt(punctuation.length())));
        
        for (int i = 4; i < 16; i++) {
            sb.append(all.charAt(random.nextInt(all.length())));
        }
        
        char[] array = sb.toString().toCharArray();
        for (int i = array.length - 1; i > 0; i--) {
            int index = random.nextInt(i + 1);
            char a = array[index];
            array[index] = array[i];
            array[i] = a;
        }
        return new String(array);
    }

    @Override
    public void onDeletePassword(PasswordEntry entry) {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(getString(R.string.pm_delete_password_title))
                .setMessage(getString(R.string.pm_delete_password_confirm, entry.getTitle()))
                .setPositiveButton(getString(R.string.delete), (dialog, which) -> {
                    Executors.newSingleThreadExecutor().execute(() -> {
                        passwordDao.delete(entry);
                        if (getActivity() != null) {
                            requireActivity().runOnUiThread(this::loadPasswords);
                        }
                    });
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
