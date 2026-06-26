package com.lorenzflow.app.ui;

import android.graphics.Paint;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.lorenzflow.app.data.Task;
import com.lorenzflow.app.databinding.ItemTaskBinding;

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
    }

    @Override
    public int getItemCount() {
        return tasks.size();
    }

    class TaskViewHolder extends RecyclerView.ViewHolder {
        private final ItemTaskBinding binding;

        TaskViewHolder(ItemTaskBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(Task task) {
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
                int bgColor;
                switch (task.getCategory()) {
                    case "Work":
                        textColor = 0xFF5856D6; // Indigo
                        bgColor = 0x1A5856D6;
                        break;
                    case "Personal":
                        textColor = 0xFF34C759; // Green
                        bgColor = 0x1A34C759;
                        break;
                    case "School":
                        textColor = 0xFFFF9500; // Orange
                        bgColor = 0x1AFF9500;
                        break;
                    case "Wellness":
                        textColor = 0xFF5AC8FA; // Teal
                        bgColor = 0x1A5AC8FA;
                        break;
                    case "Urgent":
                        textColor = 0xFFFF3B30; // Red
                        bgColor = 0x1AFF3B30;
                        break;
                    case "General":
                    default:
                        textColor = 0xFF8E8E93; // Gray
                        bgColor = 0x1A8E8E93;
                        break;
                }
                binding.textTaskCategory.setTextColor(textColor);
                binding.textTaskCategory.getBackground().setTint(bgColor);
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
