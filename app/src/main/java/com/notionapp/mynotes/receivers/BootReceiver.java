package com.notionapp.mynotes.receivers;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.notionapp.mynotes.utils.NotionApp;

/** Re-schedules reminders after a reboot (alarms do not survive one). */
public class BootReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        if (!Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) return;
        ((NotionApp) context.getApplicationContext()).getRepository().rescheduleAllReminders();
    }
}
