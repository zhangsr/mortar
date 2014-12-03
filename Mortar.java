package com.cvte.mortar;

import android.content.Context;
import android.os.Handler;

import java.io.File;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * @description:
 * @author: Saul
 * @date: 14-11-14
 * @version: 1.0
 */
public class Mortar {
    private static Mortar sMortar;
    private Context mContext;
    private Handler mHandler;
    private Set<DownloadEntry> mDownloadingSet = new HashSet<DownloadEntry>();

    private Mortar(Context context) {
        mContext = context;
        mHandler = new Handler(context.getMainLooper());
    }

    public static Mortar getInstance(Context context) {
        if (sMortar == null) {
            sMortar = new Mortar(context);
        }
        return sMortar;
    }

    public synchronized void download(String url, File file, OnDownloadListener listener) {
        // make sure downloadEntry not null
        DownloadEntry downloadEntry = MortarProvider.load(mContext, url, file.getPath());
        if (downloadEntry == null) {
            downloadEntry = new DownloadEntry(url, file.getPath(), 0, -1);
        }
        downloadEntry.listener = listener;
        if (mDownloadingSet.contains(downloadEntry)) {
            if (downloadEntry.listener != null) {
                final DownloadEntry finalDownloadEntry = downloadEntry;
                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        finalDownloadEntry.listener.onFailure(MortarStatus.CODE_FAILURE_ALREADY_DOWNLOADING,
                                MortarStatus.MSG_FAILURE_ALREADY_DOWNLOADING);
                    }
                });
            }
        } else {
            mDownloadingSet.add(downloadEntry);

            // Fix doInBackground not called, ref : http://stackoverflow.com/questions/16832376/asynctask-doinbackground-not-called
            int corePoolSize = 60;
            int maximumPoolSize = 80;
            int keepAliveTime = 10;
            BlockingQueue<Runnable> workQueue = new LinkedBlockingQueue<Runnable>(maximumPoolSize);
            Executor threadPoolExecutor = new ThreadPoolExecutor(corePoolSize, maximumPoolSize, keepAliveTime,
                    TimeUnit.SECONDS, workQueue);

            new DownloadTask(mContext, mHandler, mDownloadingSet, downloadEntry).executeOnExecutor(threadPoolExecutor);
        }
    }

    public boolean isDownloading(String url, File file) {
        return mDownloadingSet.contains(new DownloadEntry(url, file.getPath(), 0, -1));
    }
}
