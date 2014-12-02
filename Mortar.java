package com.cvte.mortar;

import android.content.Context;
import android.os.AsyncTask;
import android.os.Handler;

import java.io.BufferedInputStream;
import java.io.Closeable;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.net.HttpURLConnection;
import java.net.ProtocolException;
import java.net.SocketTimeoutException;
import java.net.URL;
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
    private static final int TIME_OUT_READ = 500 * 1000; //TODO 14-11-14 ok ?
    private static final int TIME_OUT_CONNECT = 15000;
    private static final String REQUEST_METHOD = "GET";
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

    private class DownloadTask extends AsyncTask<DownloadEntry, String, Object> {
        private File targetFile = null;
        private DownloadEntry downloadEntry = null;

        @Override
        protected Object doInBackground(DownloadEntry... downloadEntries) {
            HttpURLConnection conn = null;
            InputStream in = null;
            try {
                downloadEntry = downloadEntries[0];
                targetFile = new File(downloadEntry.localPath);
                URL url = new URL(downloadEntry.url);
                conn = (HttpURLConnection) url.openConnection();
                conn.setReadTimeout(TIME_OUT_READ);
                conn.setConnectTimeout(TIME_OUT_CONNECT);
                conn.setRequestMethod(REQUEST_METHOD);
                conn.setDoInput(true);
                int responseCode = -1;
                int bytesReadOnce;
                byte[] buffer = new byte[512 * 1024];
                long totalLength;

                if (downloadEntry.downloadedLength == 0) {
                    if (targetFile.exists()) {
                        targetFile.delete();
                    }

                    OutputStream out = null;
                    try {
                        conn.connect();
                        totalLength = conn.getContentLength();
                        downloadEntry.totalLength = totalLength;
                        responseCode = conn.getResponseCode();
                        in = new BufferedInputStream(conn.getInputStream());
                        out = new FileOutputStream(targetFile);
                        postStart(downloadEntry);
                        while ((bytesReadOnce = in.read(buffer)) != -1) {
                            out.write(buffer, 0, bytesReadOnce);
                            downloadEntry.downloadedLength += bytesReadOnce;
                            MortarProvider.save(mContext, downloadEntry);
                            MortarLog.d("responseCode=" + responseCode
                                            + ", bytesReadOnce=" + bytesReadOnce
                                            + ", downloaded=" + downloadEntry.downloadedLength
                                            + ", total=" + totalLength
                                            + ", file=" + targetFile.getPath());
                            postProgress(downloadEntry);
                        }
                    } catch (ProtocolException e) {
                        e.printStackTrace();
                        postFailure(responseCode, e.getMessage(), downloadEntry);
                    } catch (FileNotFoundException e) {
                        e.printStackTrace();
                        postFailure(responseCode, e.getMessage(), downloadEntry);
                    } catch (IOException e) {
                        e.printStackTrace();
                        postFailure(responseCode, e.getMessage(), downloadEntry);
                    } finally {
                        closeStream(out);
                    }

                } else if (downloadEntry.downloadedLength > 0) {
                    RandomAccessFile randomAccessFile = null;
                    try {
                        conn.setRequestProperty("Range", "bytes=" + downloadEntry.downloadedLength + "-");
                        conn.connect();
                        responseCode = conn.getResponseCode();

                        totalLength = downloadEntry.downloadedLength + conn.getContentLength();

                        // ***** Begin a trick to check if support break-point download
                        if (downloadEntry.totalLength * 2 == totalLength) {
                            //Fixme 14-11-20 not rigorous, but how ?
                            MortarLog.d("File already downloaded 1");
                            return null;
                        } else if (downloadEntry.totalLength < totalLength && responseCode != 416) {
                            File file = new File(downloadEntry.localPath);
                            if (!file.exists() || file.delete()) {
                                downloadEntry.downloadedLength = 0;
                                totalLength = downloadEntry.totalLength;
                            } else {
                                postFailure(-1, "Server not support break-point download and restart download failed",
                                        downloadEntry);
                            }
                        }
                        // ***** End of trick

                        switch (responseCode) {
                            case 416:   // Requested Range Not Satisfiable. May be exceed file size.
                                MortarLog.d("File already downloaded 2");
                                return null;
                            default:
                                in = new BufferedInputStream(conn.getInputStream());
                                randomAccessFile = new RandomAccessFile(targetFile, "rw");
                                postStart(downloadEntry);
                                while ((bytesReadOnce = in.read(buffer)) != -1) {
                                    randomAccessFile.seek(downloadEntry.downloadedLength);
                                    randomAccessFile.write(buffer, 0, bytesReadOnce);
                                    downloadEntry.downloadedLength += bytesReadOnce;
                                    MortarProvider.save(mContext, downloadEntry);
                                    MortarLog.d("responseCode=" + responseCode
                                                    + ", bytesReadOnce=" + bytesReadOnce
                                                    + ", downloaded=" + downloadEntry.downloadedLength
                                                    + ", total=" + totalLength
                                                    + ", file=" + targetFile.getPath());
                                    postProgress(downloadEntry);
                                }
                        }
                    } catch (SocketTimeoutException e) {
                        e.printStackTrace();
                        postFailure(responseCode, e.getMessage(), downloadEntry);
                    } catch (FileNotFoundException e) {
                        e.printStackTrace();
                        postFailure(responseCode, e.getMessage(), downloadEntry);
                    } catch (IOException e) {
                        e.printStackTrace();
                        postFailure(responseCode, e.getMessage(), downloadEntry);
                    } finally {
                        closeStream(randomAccessFile);
                    }

                }

                postSuccess(downloadEntry);
                //TODO 14-11-20 need to delete db record when success ?
                MortarProvider.delete(mContext, downloadEntry);
            } catch (ProtocolException e) {
                e.printStackTrace();
                postFailure(-1, e.getMessage(), downloadEntry);
            } catch (IOException e) {
                e.printStackTrace();
                postFailure(-1, e.getMessage(), downloadEntry);
            } finally {
                MortarProvider.save(mContext, downloadEntry);
                MortarProvider.print(mContext);
                mDownloadingSet.remove(downloadEntry);
                closeStream(in);
                disconnect(conn);
            }
            return null;
        }
    }

    public synchronized void download(String url, File file, OnDownloadListener listener) {
        // make sure downloadEntry not null
        DownloadEntry downloadEntry = MortarProvider.load(mContext, url, file.getPath());
        if (downloadEntry == null) {
            downloadEntry = new DownloadEntry(url, file.getPath(), 0, -1);
        }
        downloadEntry.listener = listener;
        if (mDownloadingSet.contains(downloadEntry)) {
            postFailure(MortarStatus.CODE_FAILURE_ALREADY_DOWNLOADING, MortarStatus.MSG_FAILURE_ALREADY_DOWNLOADING,
                    downloadEntry);
        } else {
            mDownloadingSet.add(downloadEntry);

            // Fix doInBackground not called, ref : http://stackoverflow.com/questions/16832376/asynctask-doinbackground-not-called
            int corePoolSize = 60;
            int maximumPoolSize = 80;
            int keepAliveTime = 10;
            BlockingQueue<Runnable> workQueue = new LinkedBlockingQueue<Runnable>(maximumPoolSize);
            Executor threadPoolExecutor = new ThreadPoolExecutor(corePoolSize, maximumPoolSize, keepAliveTime,
                    TimeUnit.SECONDS, workQueue);

            new DownloadTask().executeOnExecutor(threadPoolExecutor, downloadEntry);
        }
    }

    public interface OnDownloadListener {
        void onStart();
        void onProgress(long downloaded, long total);
        void onSuccess(File file);
        void onFailure(int reasonCode, String message);
    }

    private void closeStream(Closeable closeable) {
        if (closeable != null) {
            try {
                closeable.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private void disconnect(HttpURLConnection connection) {
        if (connection != null) {
            connection.disconnect();
        }
    }

    private void postStart(final DownloadEntry downloadEntry) {
        if (downloadEntry.listener != null) {
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    downloadEntry.listener.onStart();
                }
            });
        }
    }

    private void postSuccess(final DownloadEntry downloadEntry) {
        if (downloadEntry.listener != null) {
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    downloadEntry.listener.onSuccess(new File(downloadEntry.localPath));
                }
            });
        }
    }

    private void postFailure(final int reasonCode, final String message, final DownloadEntry downloadEntry) {
        if (downloadEntry.listener != null) {
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    downloadEntry.listener.onFailure(reasonCode, message);
                }
            });
        }
    }

    private void postProgress(final DownloadEntry downloadEntry) {
        if (downloadEntry.listener != null) {
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    downloadEntry.listener.onProgress(downloadEntry.downloadedLength, downloadEntry.totalLength);
                }
            });
        }
    }
}
