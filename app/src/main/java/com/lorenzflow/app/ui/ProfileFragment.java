package com.lorenzflow.app.ui;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.lorenzflow.app.R;
import com.lorenzflow.app.databinding.FragmentProfileBinding;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;

public class ProfileFragment extends Fragment {

    private FragmentProfileBinding binding;
    private SharedPreferences prefs;

    private static final String PREFS_NAME = "lorenz_flow_prefs";
    private static final String PREF_NAME_KEY = "profile_name";
    private static final String PREF_BIO_KEY = "profile_bio";
    private static final String PREF_IMAGE_URI_KEY = "profile_image_uri";

    private final ActivityResultLauncher<String> getContentLauncher =
            registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
                if (uri != null) {
                    saveImageLocally(uri);
                }
            });

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentProfileBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        prefs = requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);

        loadProfileData();

        binding.btnChangePhoto.setOnClickListener(v -> getContentLauncher.launch("image/*"));

        binding.btnSaveProfile.setOnClickListener(v -> saveProfileData());
    }

    private void loadProfileData() {
        String name = prefs.getString(PREF_NAME_KEY, "");
        String bio = prefs.getString(PREF_BIO_KEY, "");
        String imageUriStr = prefs.getString(PREF_IMAGE_URI_KEY, null);

        binding.editName.setText(name);
        binding.editBio.setText(bio);

        if (imageUriStr != null) {
            try {
                binding.profileImage.setImageURI(Uri.parse(imageUriStr));
            } catch (Exception e) {
                binding.profileImage.setImageResource(R.drawable.ic_default_avatar);
            }
        } else {
            binding.profileImage.setImageResource(R.drawable.ic_default_avatar);
        }
    }

    private void saveProfileData() {
        String name = binding.editName.getText() != null ? binding.editName.getText().toString().trim() : "";
        String bio = binding.editBio.getText() != null ? binding.editBio.getText().toString().trim() : "";

        SharedPreferences.Editor editor = prefs.edit();
        editor.putString(PREF_NAME_KEY, name);
        editor.putString(PREF_BIO_KEY, bio);
        editor.apply();

        Toast.makeText(requireContext(), R.string.profile_saved, Toast.LENGTH_SHORT).show();
    }

    private void saveImageLocally(Uri imageUri) {
        try {
            InputStream is = requireContext().getContentResolver().openInputStream(imageUri);
            if (is == null) return;

            File file = new File(requireContext().getFilesDir(), "profile_pic.jpg");
            FileOutputStream os = new FileOutputStream(file);

            byte[] buffer = new byte[1024];
            int bytesRead;
            while ((bytesRead = is.read(buffer)) != -1) {
                os.write(buffer, 0, bytesRead);
            }

            is.close();
            os.close();

            String savedUriStr = Uri.fromFile(file).toString();
            prefs.edit().putString(PREF_IMAGE_URI_KEY, savedUriStr).apply();

            binding.profileImage.setImageURI(Uri.parse(savedUriStr));
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(requireContext(), "Failed to save profile picture", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
