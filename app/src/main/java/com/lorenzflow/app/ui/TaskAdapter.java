package com.lorenzflow.app.ui;

import android.content.Context;
import android.graphics.Paint;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.lorenzflow.app.R;
import com.lorenzflow.app.data.Task;
import com.lorenzflow.app.databinding.ItemTaskBinding;
import com.lorenzflow.app.util.AnimUtil;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class TaskAdapter extends RecyclerView.Adapter<TaskAdapter.TaskViewHolder> {

    public interface OnTaskActionListener {
        void onTaskCheckedChanged(Task task, boolean isChecked);
        void onTaskClick(Task task);
        void onTaskDelete(Task task);
        void onTaskShare(Task task);
        void onTaskArchive(Task task);
    }

    private List<Task> tasks = new ArrayList<>();
    private final OnTaskActionListener listener;
    private final SimpleDateFormat sdf =
            new SimpleDateFormat("MMM dd, yyyy · hh:mm a", Locale.getDefault());

    public TaskAdapter(OnTaskActionListener listener) {
        this.listener = listener;
    }

    public void setTasks(List<Task> tasks) {
        this.tasks = new ArrayList<>(tasks);
        notifyDataSetChanged();
    }

    public List<Task> getTasks() {
        return tasks;
    }

    public Task getTaskAt(int position) {
        if (position >= 0 && position < tasks.size()) {
            return tasks.get(position);
        }
        return null;
    }

    public void removeAt(int position) {
        if (position >= 0 && position < tasks.size()) {
            tasks.remove(position);
            notifyItemRemoved(position);
        }
    }

    @NonNull
    @Override
    public TaskViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemTaskBinding binding = ItemTaskBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);
        return new TaskViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull TaskViewHolder holder, int position) {
        holder.bind(tasks.get(position));
        holder.fadeIn(position);
    }

    @Override
    public int getItemCount() {
        return tasks.size();
    }

    /** Maps a category to its pastel gradient card background drawable. */
    private int categoryCardBackground(String category) {
        if (category == null) return R.drawable.card_pastel_neutral;
        switch (category) {
            case "Work":
                return R.drawable.card_pastel_lilac;
            case "Personal":
                return R.drawable.card_pastel_mint;
            case "School":
                return R.drawable.card_pastel_peach;
            case "Wellness":
                return R.drawable.card_pastel_sky;
            case "Urgent":
                return R.drawable.card_pastel_rose;
            case "General":
            default:
                return R.drawable.card_pastel_neutral;
        }
    }

    class TaskViewHolder extends RecyclerView.ViewHolder {
        private final ItemTaskBinding binding;
        private boolean animatedIn;

        TaskViewHolder(ItemTaskBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        /** Subtle fade-in, run once per ViewHolder instance. */
        void fadeIn(int position) {
            animatedIn = AnimUtil.fadeInOnce(binding.getRoot(), position, animatedIn);
        }

        void bind(Task task) {
            Context ctx = binding.getRoot().getContext();

            // Pastel gradient card background per category (24dp rounded)
            binding.taskCardBg.setBackgroundResource(categoryCardBackground(task.getCategory()));

            // Title
            binding.textTaskTitle.setText(task.getTitle());

            // Strikethrough if completed
            if (task.isCompleted()) {
                binding.textTaskTitle.setPaintFlags(
                        binding.textTaskTitle.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
                binding.textTaskTitle.setAlpha(0.6f);
            } else {
                binding.textTaskTitle.setPaintFlags(
                        binding.textTaskTitle.getPaintFlags() & (~Paint.STRIKE_THRU_TEXT_FLAG));
                binding.textTaskTitle.setAlpha(1.0f);
            }

            // Description
            if (task.getDescription() != null && !task.getDescription().isEmpty()) {
                binding.textTaskDescription.setText(task.getDescription());
                binding.textTaskDescription.setVisibility(View.VISIBLE);
            } else {
                binding.textTaskDescription.setVisibility(View.GONE);
            }

            // Deadline
            if (task.getDeadline() > 0) {
                binding.deadlineContainer.setVisibility(View.VISIBLE);
                binding.textTaskDeadline.setText(sdf.format(new Date(task.getDeadline())));
            } else {
                binding.deadlineContainer.setVisibility(View.GONE);
            }

            // Category Badge
            if (task.getCategory() != null && !task.getCategory().isEmpty()) {
                binding.textTaskCategory.setText(task.getCategory());
                binding.textTaskCategory.setVisibility(View.VISIBLE);

                int textColor;
                switch (task.getCategory()) {
                    case "Work":
                        textColor = R.color.lf_pastel_lilac_deep;
                        break;
                    case "Personal":
                        textColor = R.color.lf_pastel_mint_deep;
                        break;
                    case "School":
                        textColor = R.color.lf_pastel_peach_deep;
                        break;
                    case "Wellness":
                        textColor = R.color.lf_pastel_sky_deep;
                        break;
                    case "Urgent":
                        textColor = R.color.lf_pastel_rose_deep;
                        break;
                    case "General":
                    default:
                        textColor = R.color.lf_text_secondary;
                        break;
                }
                binding.textTaskCategory.setTextColor(ContextCompat.getColor(ctx, textColor));
                binding.textTaskCategory.getBackground()
                        .setTint(ContextCompat.getColor(ctx, R.color.lf_badge_tint));
            } else {
                binding.textTaskCategory.setVisibility(View.GONE);
            }

            // Checkbox
            binding.checkboxTask.setOnCheckedChangeListener(null); // clear old listener
            binding.checkboxTask.setChecked(task.isCompleted());
            binding.checkboxTask.setOnCheckedChangeListener((buttonView, isChecked) -> {
                if (listener != null) {
                    listener.onTaskCheckedChanged(task, isChecked);
                }
            });

            // Click
            binding.getRoot().setOnClickListener(v -> {
                if (listener != null) {
                    listener.onTaskClick(task);
                }
            });

            // Share click
            binding.btnShareTask.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onTaskShare(task);
                }
            });

            // Archive click
            binding.btnArchiveTask.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onTaskArchive(task);
                }
            });

            // Delete click
            binding.btnDeleteTask.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onTaskDelete(task);
                }
            });
        }
    }
}
