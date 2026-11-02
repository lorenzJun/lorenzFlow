package com.lorenzflow.app.notification;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.lorenzflow.app.data.AppDatabase;
import com.lorenzflow.app.data.Task;
import com.lorenzflow.app.util.AlarmScheduler;

import com.lorenzflow.app.util.AppExecutors;
import java.util.List;
import java.util.concurrent.Executors;

/**
 * Re-schedules all pending task alarms after a device reboot.
 */
public class BootReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || !Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            return;
        }

        // goAsync() keeps the process alive while the executor finishes the
        // DB read; finish() is always called so the system can recycle the
        // receiver.
        final BroadcastReceiver.PendingResult pendingResult = goAsync();
        AppExecutors.io().execute(() -> {
            try {
                AppDatabase db = AppDatabase.getInstance(context);
                List<Task> tasks = db.taskDao().getActiveTasksWithDeadline();
                long now = System.currentTimeMillis();

                for (Task task : tasks) {
                    // Re-register each pending deadline alarm on this thread
                    if (task.getDeadline() > now) {
                        AlarmScheduler.scheduleAlarm(context, task);
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                pendingResult.finish();
            }
        });
    }
}
