package com.sonelli.juicessh.performancemonitor.controllers;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Handler;
import android.util.Log;

import com.sonelli.juicessh.performancemonitor.R;
import com.sonelli.juicessh.pluginlibrary.exceptions.ServiceNotConnectedException;
import com.sonelli.juicessh.pluginlibrary.listeners.OnSessionExecuteListener;

import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class DiskUsageController extends BaseController {

    public static final String TAG = "DiskUsageController";

    private String partition;

    public DiskUsageController(Context context) {
        super(context);
        partition = "/";
    }

    public void choosePartition() {
        final ArrayList<String> partitions = new ArrayList<String>();

        final Pattern partitionNamePattern = Pattern.compile("(/[\\w/]*$)");

        final Handler handler = new Handler();

        final boolean wasRunning = isRunning();

        handler.post(new Runnable() {
            @Override
            public void run() {
                try {
                    if(isRunning())
                        stop();

                    getPluginClient().executeCommandOnSession(getSessionId(), getSessionKey(), "df | sed '1 d'", new OnSessionExecuteListener() {
                        @Override
                        public void onCompleted(int exitCode) {
                            switch(exitCode) {
                                case 0:
                                    if(context.get() != null) {
                                        final String[] partitionsArray = (String[]) partitions.toArray(new String[0]);
                                        AlertDialog.Builder builder = new AlertDialog.Builder(context.get());
                                        builder.setTitle("Choose a partition")
                                                .setItems(partitionsArray, new DialogInterface.OnClickListener() {
                                                    @Override
                                                    public void onClick(DialogInterface dialogInterface, int which) {
                                                        partition = partitionsArray[which];
                                                        dialogInterface.cancel();
                                                        if(wasRunning)
                                                            start();
                                                    }
                                                })
                                                .show();
                                    }
                                    break;
                                case 127:
                                default:
                                    Log.d(TAG, "Could not load available partitions");
                                    break;
                            }
                        }

                        @Override
                        public void onOutputLine(String line) {
                            Matcher partitionNameMatcher = partitionNamePattern.matcher(line);
                            if(partitionNameMatcher.find())
                                partitions.add(partitionNameMatcher.group(1));
                        }

                        @Override
                        public void onError(int error, String reason) {
                            toast(reason);
                        }
                    });
                } catch (ServiceNotConnectedException e){
                    Log.d(TAG, "Tried to execute a command but could not connect to JuiceSSH plugin service");
                }
            }
        });
    }

    @Override
    public BaseController start() {
        super.start();

        // Work out the free disk space percentage on the / disk

        final Pattern diskUsagePattern = Pattern.compile("([0-9.]+%)"); // Heavy cpu so do out of loops.

        final Handler handler = new Handler();

        final String command = "df | grep ' " + partition + "$'";

        Log.d(TAG, command);

        handler.post(new Runnable() {
            @Override
            public void run() {

                try {

                    getPluginClient().executeCommandOnSession(getSessionId(), getSessionKey(), command, new OnSessionExecuteListener() {
                        @Override
                        public void onCompleted(int exitCode) {
                            switch(exitCode){
                                case 127:
                                    setText(getString(R.string.error));
                                    Log.d(TAG, "Tried to run a command but the command was not found on the server");
                                    break;
                            }
                        }
                        @Override
                        public void onOutputLine(String line) {
                            Matcher diskUsageMatcher = diskUsagePattern.matcher(line);
                            if(diskUsageMatcher.find()){
                                setText(diskUsageMatcher.group(1));
                            }
                        }

                        @Override
                        public void onError(int error, String reason) {
                            toast(reason);
                        }
                    });
                } catch (ServiceNotConnectedException e){
                    Log.d(TAG, "Tried to execute a command but could not connect to JuiceSSH plugin service");
                }

                if(isRunning()){
                    handler.postDelayed(this, INTERVAL_SECONDS * 1000L);
                }
            }


        });

        return this;

    }

}
