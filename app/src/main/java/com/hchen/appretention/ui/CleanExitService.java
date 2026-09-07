package com.hchen.appretention.ui;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.os.Process;

/**
 * Ensures AppRetention completely stops and exits as soon as the user
 * swipes or closes it from Recents / Multitasking (đóng đa nhiệm).
 * Since all retention & restriction features operate via hooks inside system_server,
 * AppRetention requires zero background execution.
 */
public class CleanExitService extends Service {
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_NOT_STICKY;
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        super.onTaskRemoved(rootIntent);
        stopSelf();
        Process.killProcess(Process.myPid());
        System.exit(0);
    }
}
