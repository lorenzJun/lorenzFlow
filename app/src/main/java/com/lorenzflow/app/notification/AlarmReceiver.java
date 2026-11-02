package com.lorenzflow.app.notification;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

/**
 * Receives alarm broadcasts at the task deadline time and fires a notification.
 */
public class AlarmReceiver extends BroadcastReceiver {

    public static final String EXTRA_TASK_ID = "extra_task_id";
    public static final String EXTRA_TASK_TITLE = "extra_task_title";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null) return;

        int taskId = intent.getIntExtra(EXTRA_TASK_ID, -1);
        String taskTitle = intent.getStringExtra(EXTRA_TASK_TITLE);

        if (taskId == -1 || taskTitle == null) return;

        // Check if notifications are enabled in settings
        SharedPreferences prefs =
                context.getSharedPreferences("lorenz_flow_prefs", Context.MODE_PRIVATE);
        boolean notificationsEnabled = prefs.getBoolean("pref_notifications", true);
        if (!notificationsEnabled) return;

        NotificationHelper.showTaskNotification(context, taskId, taskTitle);
    }
}
