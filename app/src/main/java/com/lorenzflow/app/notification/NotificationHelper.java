package com.lorenzflow.app.notification;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import androidx.core.app.NotificationCompat;

import com.lorenzflow.app.MainActivity;
import com.lorenzflow.app.R;

public class NotificationHelper {

    public static final String CHANNEL_ID = "Task_Deadlines";

    /**
     * Creates the notification channel. Safe to call multiple times;
     * the system ignores if the channel already exists.
     */
    public static void createNotificationChannel(Context context) {
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_HIGH
        );
        channel.setDescription(context.getString(R.string.notification_channel_desc));
        channel.enableVibration(true);
        channel.enableLights(true);

        NotificationManager manager = context.getSystemService(NotificationManager.class);
        if (manager != null) {
            manager.createNotificationChannel(channel);
        }
    }

    /**
     * Shows a deadline notification for the given task.
     *
     * @param context    Application context
     * @param taskId     Unique task ID used as notification ID
     * @param taskTitle  Title of the task to display
     */
    /**
     * Opens the system screen where the user can grant app notifications.
     * Safe to call on any API 26+ device (app minSdk).
     */
    public static void openNotificationSettings(Context context) {
        Intent intent = new Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, context.getPackageName());
        try {
            context.startActivity(intent);
        } catch (android.content.ActivityNotFoundException | SecurityException e) {
            e.printStackTrace();
        }
    }

    public static void showTaskNotification(Context context, int taskId, String taskTitle) {
        createNotificationChannel(context);

        Intent intent = new Intent(context, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                context, taskId, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_tasks)
                .setContentTitle(context.getString(R.string.app_name))
                .setContentText(context.getString(R.string.task_deadline_notification, taskTitle))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_REMINDER)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .setDefaults(NotificationCompat.DEFAULT_ALL);

        NotificationManager manager = context.getSystemService(NotificationManager.class);
        if (manager != null) {
            manager.notify(taskId, builder.build());
        }
    }
}
