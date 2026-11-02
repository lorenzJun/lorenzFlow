package com.lorenzflow.app.ui;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.lorenzflow.app.R;
import com.lorenzflow.app.data.PasswordEntry;
import com.lorenzflow.app.databinding.ItemPasswordBinding;
import com.lorenzflow.app.util.AnimUtil;
import com.lorenzflow.app.util.SecurityUtil;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class PasswordAdapter extends RecyclerView.Adapter<PasswordAdapter.PasswordViewHolder> {

    public interface OnPasswordActionListener {
        void onDeletePassword(PasswordEntry entry);
    }

    private List<PasswordEntry> allPasswords = new ArrayList<>();
    private List<PasswordEntry> displayedPasswords = new ArrayList<>();
    private final OnPasswordActionListener listener;

    // Set of password IDs currently set to visible (revealed or being revealed)
    private final Set<Long> visiblePasswordIds = new HashSet<>();
    // Short-lived cache of decrypted passwords; only holds rows the user
    // explicitly revealed, and is erased when the row is hidden again.
    private final Map<Long, String> decryptedCache = new HashMap<>();
    // Decrypt jobs already queued (prevents duplicate work on rapid rebinds)
    private final Set<Long> pendingDecrypts = new HashSet<>();
    private final Set<Long> failedDecrypts = new HashSet<>();

    // KeyStore/cipher calls are slow — never run them on the UI thread.
    private final ExecutorService cryptoExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "password-decrypt");
        t.setDaemon(true);
        return t;
    });
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public PasswordAdapter(OnPasswordActionListener listener) {
        this.listener = listener;
    }

    public void setPasswords(List<PasswordEntry> passwords) {
        this.allPasswords = new ArrayList<>(passwords);
        this.displayedPasswords = new ArrayList<>(passwords);
        visiblePasswordIds.clear();
        decryptedCache.clear();
        pendingDecrypts.clear();
        failedDecrypts.clear();
        notifyDataSetChanged();
    }

    public void filter(String query) {
        // Filtered rows keep any reveal/error state by entry ID on purpose
        displayedPasswords.clear();
        if (query.isEmpty()) {
            displayedPasswords.addAll(allPasswords);
        } else {
            String lowerQuery = query.toLowerCase(Locale.ROOT).trim();
            for (PasswordEntry entry : allPasswords) {
                if (entry.getTitle().toLowerCase(Locale.ROOT).contains(lowerQuery) ||
                        entry.getUsername().toLowerCase(Locale.ROOT).contains(lowerQuery)) {
                    displayedPasswords.add(entry);
                }
            }
        }
        notifyDataSetChanged();
    }

    /**
     * Decrypts on the crypto executor and reports the result on the main
     * thread. Does not touch any UI; callers decide what to do with the
     * decrypted value.
     */
    private void decryptInBackground(PasswordEntry entry, DecryptCallback callback) {
        cryptoExecutor.execute(() -> {
            String decrypted = SecurityUtil.decrypt(entry.getEncryptedPassword(), entry.getIv());
            mainHandler.post(() -> callback.onDecrypted(decrypted));
        });
    }

    private interface DecryptCallback {
        void onDecrypted(String decrypted);
    }

    /**
     * Reveal path: decrypt in background, then cache the result (if any) and
     * rebind just this row. Skips rows that already have a decrypt in flight.
     */
    private void scheduleRevealDecrypt(PasswordEntry entry, long entryId) {
        if (!pendingDecrypts.add(entryId)) return;

        cryptoExecutor.execute(() -> {
            String decrypted = SecurityUtil.decrypt(entry.getEncryptedPassword(), entry.getIv());
            mainHandler.post(() -> {
                pendingDecrypts.remove(entryId);
                if (decrypted != null) {
                    decryptedCache.put(entryId, decrypted);
                    failedDecrypts.remove(entryId);
                } else {
                    failedDecrypts.add(entryId);
                }
                int position = displayedPasswords.indexOf(entry);
                if (position >= 0) {
                    notifyItemChanged(position);
                }
            });
        });
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
        holder.fadeIn(position);
    }

    @Override
    public int getItemCount() {
        return displayedPasswords.size();
    }

    class PasswordViewHolder extends RecyclerView.ViewHolder {
        private final ItemPasswordBinding binding;
        private boolean animatedIn;

        PasswordViewHolder(ItemPasswordBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        /** Subtle fade-in, run once per ViewHolder instance. */
        void fadeIn(int position) {
            animatedIn = AnimUtil.fadeInOnce(binding.getRoot(), position, animatedIn);
        }

        void bind(PasswordEntry entry) {
            binding.textPasswordTitle.setText(entry.getTitle());
            binding.textPasswordUsername.setText(entry.getUsername());

            Context ctx = binding.getRoot().getContext();
            long entryId = entry.getId();
            boolean isVisible = visiblePasswordIds.contains(entryId);

            if (isVisible) {
                String cached = decryptedCache.get(entryId);
                if (cached != null) {
                    // Fast path: already decrypted, no re-decryption on rebind
                    binding.textPasswordVal.setText(cached);
                    binding.btnToggleVisibility.setImageResource(R.drawable.ic_expand_less);
                } else if (failedDecrypts.contains(entryId)) {
                    binding.textPasswordVal.setText(ctx.getString(R.string.decrypt_error));
                    binding.btnToggleVisibility.setImageResource(R.drawable.ic_visibility);
                } else {
                    // Decrypting in background; show dots until the row rebinds
                    binding.textPasswordVal.setText("••••••••••••");
                    binding.btnToggleVisibility.setImageResource(R.drawable.ic_visibility);
                    scheduleRevealDecrypt(entry, entryId);
                }
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
                    Toast.makeText(ctx, R.string.username_copied, Toast.LENGTH_SHORT).show();
                }
            });

            // Copy password — decrypt happens off the UI thread
            binding.btnCopyPassword.setOnClickListener(v -> {
                decryptInBackground(entry, decrypted -> {
                    if (decrypted == null) {
                        Toast.makeText(ctx, R.string.error_decrypting_password, Toast.LENGTH_SHORT).show();
                        return;
                    }
                    ClipboardManager clipboard = (ClipboardManager) ctx.getSystemService(Context.CLIPBOARD_SERVICE);
                    ClipData clip = ClipData.newPlainText("password", decrypted);
                    if (clipboard != null) {
                        clipboard.setPrimaryClip(clip);
                        Toast.makeText(ctx, R.string.password_copied, Toast.LENGTH_SHORT).show();
                    }
                });
            });

            // Toggle visibility click — reveal work happens on the next bind()
            binding.btnToggleVisibility.setOnClickListener(v -> {
                int adapterPosition = getAdapterPosition();
                if (adapterPosition == RecyclerView.NO_POSITION) return;

                if (visiblePasswordIds.contains(entryId)) {
                    visiblePasswordIds.remove(entryId);
                    decryptedCache.remove(entryId); // keep plaintext ephemeral
                    failedDecrypts.remove(entryId);
                } else {
                    visiblePasswordIds.add(entryId);
                    decryptedCache.remove(entryId);
                }
                notifyItemChanged(adapterPosition);
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
