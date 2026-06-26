package com.lorenzflow.app.ui;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.lorenzflow.app.R;
import com.lorenzflow.app.data.PasswordEntry;
import com.lorenzflow.app.databinding.ItemPasswordBinding;
import com.lorenzflow.app.util.SecurityUtil;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class PasswordAdapter extends RecyclerView.Adapter<PasswordAdapter.PasswordViewHolder> {

    public interface OnPasswordActionListener {
        void onDeletePassword(PasswordEntry entry);
    }

    private List<PasswordEntry> allPasswords = new ArrayList<>();
    private List<PasswordEntry> displayedPasswords = new ArrayList<>();
    private final OnPasswordActionListener listener;
    
    // Set of password IDs currently set to visible (decrypted)
    private final Set<Long> visiblePasswordIds = new HashSet<>();

    public PasswordAdapter(OnPasswordActionListener listener) {
        this.listener = listener;
    }

    public void setPasswords(List<PasswordEntry> passwords) {
        this.allPasswords = new ArrayList<>(passwords);
        this.displayedPasswords = new ArrayList<>(passwords);
        visiblePasswordIds.clear();
        notifyDataSetChanged();
    }

    public void filter(String query) {
        displayedPasswords.clear();
        if (query.isEmpty()) {
            displayedPasswords.addAll(allPasswords);
        } else {
            String lowerQuery = query.toLowerCase().trim();
            for (PasswordEntry entry : allPasswords) {
                if (entry.getTitle().toLowerCase().contains(lowerQuery) ||
                        entry.getUsername().toLowerCase().contains(lowerQuery)) {
                    displayedPasswords.add(entry);
                }
            }
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public PasswordViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemPasswordBinding binding = ItemPasswordBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);
        return new PasswordViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull PasswordViewHolder holder, int position) {
        holder.bind(displayedPasswords.get(position));
    }

    @Override
    public int getItemCount() {
        return displayedPasswords.size();
    }

    class PasswordViewHolder extends RecyclerView.ViewHolder {
        private final ItemPasswordBinding binding;

        PasswordViewHolder(ItemPasswordBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(PasswordEntry entry) {
            binding.textPasswordTitle.setText(entry.getTitle());
            binding.textPasswordUsername.setText(entry.getUsername());

            Context ctx = binding.getRoot().getContext();
            long entryId = entry.getId();
            boolean isVisible = visiblePasswordIds.contains(entryId);

            if (isVisible) {
                String decrypted = SecurityUtil.decrypt(entry.getEncryptedPassword(), entry.getIv());
                binding.textPasswordVal.setText(decrypted != null ? decrypted : "Error decrypting");
                binding.btnToggleVisibility.setImageResource(R.drawable.ic_expand_less); // toggle visual indicator
            } else {
                binding.textPasswordVal.setText("••••••••••••");
                binding.btnToggleVisibility.setImageResource(R.drawable.ic_visibility);
            }

            // Copy username
            binding.btnCopyUsername.setOnClickListener(v -> {
                ClipboardManager clipboard = (ClipboardManager) ctx.getSystemService(Context.CLIPBOARD_SERVICE);
                ClipData clip = ClipData.newPlainText("username", entry.getUsername());
                if (clipboard != null) {
                    clipboard.setPrimaryClip(clip);
                    Toast.makeText(ctx, "Username copied to clipboard", Toast.LENGTH_SHORT).show();
                }
            });

            // Copy password
            binding.btnCopyPassword.setOnClickListener(v -> {
                String decrypted = SecurityUtil.decrypt(entry.getEncryptedPassword(), entry.getIv());
                if (decrypted != null) {
                    ClipboardManager clipboard = (ClipboardManager) ctx.getSystemService(Context.CLIPBOARD_SERVICE);
                    ClipData clip = ClipData.newPlainText("password", decrypted);
                    if (clipboard != null) {
                        clipboard.setPrimaryClip(clip);
                        Toast.makeText(ctx, "Password copied to clipboard", Toast.LENGTH_SHORT).show();
                    }
                } else {
                    Toast.makeText(ctx, "Error decrypting password", Toast.LENGTH_SHORT).show();
                }
            });

            // Toggle visibility click
            binding.btnToggleVisibility.setOnClickListener(v -> {
                if (isVisible) {
                    visiblePasswordIds.remove(entryId);
                } else {
                    visiblePasswordIds.add(entryId);
                }
                notifyItemChanged(getAdapterPosition());
            });

            // Delete click
            binding.btnDeletePassword.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onDeletePassword(entry);
                }
            });
        }
    }
}
