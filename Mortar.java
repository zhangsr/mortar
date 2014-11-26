package com.cvte.mortars;

import android.content.Context;
import android.os.AsyncTask;
import android.os.Handler;
import android.util.Log;

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
import java.net.MalformedURLException;
import java.net.ProtocolException;
import java.net.SocketTimeoutException;
import java.net.URL;
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
    public static final String TAG = "Mortar" ;
    private static final int TIME_OUT_READ = 500 * 1000; //TODO 14-11-14 ok ?
    private static final int TIME_OUT_CONNECT = 15000;
    private static final String REQUEST_METHOD = "GET";

    private OnDownloadListener mDownloadListener;
    private File mTargetFile;

    private Context mContext;
    private Handler mHandler;
    private DownloadEntry mDownloadEntry;
    private boolean mIsFailure;
    private DownloadTask mCurrentTask;


    public Mortar(Context context) {
        mContext = context;
        mHandler = new Handler(context.getMainLooper());
    }

    private class DownloadTask extends AsyncTask<URL, String, Object> {

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            mIsFailure = false;
        }

        @Override
        protected Object doInBackground(URL... params) {
            URL url = params[0];
            HttpURLConnection conn = null;
            InputStream in = null;
            try {
                conn = (HttpURLConnection) url.openConnection();
                conn.setReadTimeout(TIME_OUT_READ);
                conn.setConnectTimeout(TIME_OUT_CONNECT);
                conn.setRequestMethod(REQUEST_METHOD);
                conn.setDoInput(true);
                int responseCode = -1;
                int bytesReadOnce;
                byte[] buffer = new byte[512 * 1024];
                long totalLength;

                if (mDownloadEntry.downloadedLength == 0) {
                    OutputStream out = null;
                    try {
                        conn.connect();
                        totalLength = conn.getContentLength();
                        mDownloadEntry.totalLength = totalLength;
                        responseCode = conn.getResponseCode();
                        in = new BufferedInputStream(conn.getInputStream());
                        out = new FileOutputStream(mTargetFile);
                        postStart();
                        while ((bytesReadOnce = in.read(buffer)) != -1) {
                            out.write(buffer, 0, bytesReadOnce);
                            mDownloadEntry.downloadedLength += bytesReadOnce;
                            MortarProvider.save(mContext, mDownloadEntry);
                            Log.d(Mortar.TAG,
                                    "responseCode=" + responseCode
                                            + ", bytesReadOnce=" + bytesReadOnce
                                            + ", downloaded=" + mDownloadEntry.downloadedLength
                                            + ", total=" + totalLength
                                            + ", file=" + mTargetFile.getPath());
                            postProgress(mDownloadEntry.downloadedLength, totalLength);
                        }
                    } catch (ProtocolException e) {
                        e.printStackTrace();
                        postFailure(responseCode, e.getMessage());
                    } catch (FileNotFoundException e) {
                        e.printStackTrace();
                        postFailure(responseCode, e.getMessage());
                    } catch (IOException e) {
                        e.printStackTrace();
                        postFailure(responseCode, e.getMessage());
                    } finally {
                        closeStream(out);
                    }

                } else if (mDownloadEntry.downloadedLength > 0) {
                    RandomAccessFile randomAccessFile = null;
                    try {
                        conn.setRequestProperty("Range", "bytes=" + mDownloadEntry.downloadedLength + "-");
                        conn.connect();
                        responseCode = conn.getResponseCode();

                        totalLength = mDownloadEntry.downloadedLength + conn.getContentLength();

                        // ***** Begin a trick to check if support break-point download
                        if (mDownloadEntry.totalLength * 2 == totalLength) {
                            //Fixme 14-11-20 not rigorous, but how ?
                            Log.d(Mortar.TAG, "File already downloaded 1");
                            return null;
                        } else if (mDownloadEntry.totalLength < totalLength && responseCode != 416) {
                            File file = new File(mDownloadEntry.localPath);
                            if (!file.exists() || file.delete()) {
                                mDownloadEntry.downloadedLength = 0;
                                totalLength = mDownloadEntry.totalLength;
                            } else {
                                postFailure(-1, "Server not support break-point download and restart download failed");
                            }
                        }
                        // ***** End of trick

                        switch (responseCode) {
                            case 416:   // Requested Range Not Satisfiable. May be exceed file size.
                                Log.d(Mortar.TAG, "File already downloaded 2");
                                return null;
                            default:
                                in = new BufferedInputStream(conn.getInputStream());
                                randomAccessFile = new RandomAccessFile(mTargetFile, "rw");
                                postStart();
                                while ((bytesReadOnce = in.read(buffer)) != -1) {
                                    randomAccessFile.seek(mDownloadEntry.downloadedLength);
                                    randomAccessFile.write(buffer, 0, bytesReadOnce);
                                    mDownloadEntry.downloadedLength += bytesReadOnce;
                                    MortarProvider.save(mContext, mDownloadEntry);
                                    Log.d(Mortar.TAG,
                                            "responseCode=" + responseCode
                                                    + ", bytesReadOnce=" + bytesReadOnce
                                                    + ", downloaded=" + mDownloadEntry.downloadedLength
                                                    + ", total=" + totalLength
                                                    + ", file=" + mTargetFile.getPath());
                                    postProgress(mDownloadEntry.downloadedLength, totalLength);
                                }
                        }
                    } catch (SocketTimeoutException e) {
                        e.printStackTrace();
                        postFailure(responseCode, e.getMessage());
                    } catch (FileNotFoundException e) {
                        e.printStackTrace();
                        postFailure(responseCode, e.getMessage());
                    } catch (IOException e) {
                        e.printStackTrace();
                        postFailure(responseCode, e.getMessage());
                    } finally {
                        closeStream(randomAccessFile);
                    }

                }
            } catch (ProtocolException e) {
                e.printStackTrace();
                postFailure(-1, e.getMessage());
            } catch (IOException e) {
                e.printStackTrace();
                postFailure(-1, e.getMessage());
            } finally {
                MortarProvider.save(mContext, mDownloadEntry);
                MortarProvider.print(mContext);
                closeStream(in);
                disconnect(conn);
            }
            return null;
        }

        @Override
        protected void onPostExecute(Object o) {
            super.onPostExecute(o);
            if (!mIsFailure) {
                postSuccess(mTargetFile);
            }
        }
    }

    public void download(String url, File file, OnDownloadListener listener) {
        try {
            mTargetFile = file;
            mDownloadListener = listener;

            mDownloadEntry = MortarProvider.load(mContext, url, mTargetFile.getPath());
            if (mDownloadEntry == null) {
                mDownloadEntry = new DownloadEntry(url, mTargetFile.getPath(), 0, -1);
            }
            if (mCurrentTask != null && !mCurrentTask.isCancelled()) {
                mCurrentTask.cancel(true);
            }
            mCurrentTask = new DownloadTask();

            // Fix doInBackground not called, ref : http://stackoverflow.com/questions/16832376/asynctask-doinbackground-not-called
            int corePoolSize = 60;
            int maximumPoolSize = 80;
            int keepAliveTime = 10;
            BlockingQueue<Runnable> workQueue = new LinkedBlockingQueue<Runnable>(maximumPoolSize);
            Executor threadPoolExecutor = new ThreadPoolExecutor(corePoolSize, maximumPoolSize, keepAliveTime,
                    TimeUnit.SECONDS, workQueue);

            mCurrentTask.executeOnExecutor(threadPoolExecutor, new URL(url));
        } catch (MalformedURLException e) {
            e.printStackTrace();
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

    private void postStart() {
        if (mDownloadListener != null) {
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    mDownloadListener.onStart();
                }
            });
        }
    }

    private void postSuccess(final File file) {
        //TODO 14-11-20 need to delete db record when success ?
        if (mDownloadListener != null) {
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    mDownloadListener.onSuccess(file);
                }
            });
        }
    }

    private void postFailure(final int reasonCode, final String message) {
        mIsFailure = true;
        if (mDownloadListener != null) {
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    mDownloadListener.onFailure(reasonCode, message);
                }
            });
        }
    }

    private void postProgress(final long downloaded, final long total) {
        if (mDownloadListener != null) {
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    mDownloadListener.onProgress(downloaded, total);
                }
            });
        }
    }
}
