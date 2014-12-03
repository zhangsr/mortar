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
import java.util.Set;

/**
 * @description:
 * @author: Saul
 * @date: 14-12-3
 * @version: 1.0
 */
public class DownloadTask extends AsyncTask<String, String, Object> {
    private static final int DEFAULT_TIMEOUT = 20 * 1000; //TODO 14-11-14 ok ? Same as android download thread
    private static final int BUFFER_SIZE = 4 * 1024;
    private static final String REQUEST_METHOD = "GET";
    private DownloadEntry mDownloadEntry;
    private Handler mHandler;
    private Context mContext;
    private Set<DownloadEntry> mDownloadingSet;

    public DownloadTask(Context context, Handler handler, Set<DownloadEntry> downloadingSet, DownloadEntry downloadEntry) {
        mContext = context;
        mHandler = handler;
        mDownloadingSet = downloadingSet;
        mDownloadEntry = downloadEntry;
    }

    @Override
    protected Object doInBackground(String... params) {
        HttpURLConnection conn = null;
        InputStream in = null;
        try {
            File targetFile = new File(mDownloadEntry.localPath);
            URL url = new URL(mDownloadEntry.url);
            conn = (HttpURLConnection) url.openConnection();
            conn.setReadTimeout(DEFAULT_TIMEOUT);
            conn.setConnectTimeout(DEFAULT_TIMEOUT);
            conn.setRequestMethod(REQUEST_METHOD);
            conn.setInstanceFollowRedirects(false);
            int responseCode = -1;
            int bytesReadOnce;
            byte[] buffer = new byte[BUFFER_SIZE];
            long totalLength;

            if (mDownloadEntry.downloadedLength == 0) {
                if (targetFile.exists()) {
                    targetFile.delete();
                }

                OutputStream out = null;
                try {
                    conn.connect();
                    totalLength = conn.getContentLength();
                    mDownloadEntry.totalLength = totalLength;
                    responseCode = conn.getResponseCode();
                    in = new BufferedInputStream(conn.getInputStream());
                    out = new FileOutputStream(targetFile);
                    postStart();
                    while ((bytesReadOnce = in.read(buffer)) != -1) {
                        out.write(buffer, 0, bytesReadOnce);
                        mDownloadEntry.downloadedLength += bytesReadOnce;
                        MortarProvider.save(mContext, mDownloadEntry);
                        MortarLog.d("responseCode=" + responseCode
                                + ", bytesReadOnce=" + bytesReadOnce
                                + ", downloaded=" + mDownloadEntry.downloadedLength
                                + ", total=" + totalLength
                                + ", file=" + targetFile.getPath());
                        postProgress();
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
                        MortarLog.d("File already downloaded 1");
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
                            MortarLog.d("File already downloaded 2");
                            return null;
                        default:
                            in = new BufferedInputStream(conn.getInputStream());
                            randomAccessFile = new RandomAccessFile(targetFile, "rw");
                            postStart();
                            while ((bytesReadOnce = in.read(buffer)) != -1) {
                                randomAccessFile.seek(mDownloadEntry.downloadedLength);
                                randomAccessFile.write(buffer, 0, bytesReadOnce);
                                mDownloadEntry.downloadedLength += bytesReadOnce;
                                MortarProvider.save(mContext, mDownloadEntry);
                                MortarLog.d("responseCode=" + responseCode
                                        + ", bytesReadOnce=" + bytesReadOnce
                                        + ", downloaded=" + mDownloadEntry.downloadedLength
                                        + ", total=" + totalLength
                                        + ", file=" + targetFile.getPath());
                                postProgress();
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

            postSuccess();
            //TODO 14-11-20 need to delete db record when success ?
            MortarProvider.delete(mContext, mDownloadEntry);
        } catch (ProtocolException e) {
            e.printStackTrace();
            postFailure(-1, e.getMessage());
        } catch (IOException e) {
            e.printStackTrace();
            postFailure(-1, e.getMessage());
        } finally {
            MortarProvider.save(mContext, mDownloadEntry);
            MortarProvider.print(mContext);
            mDownloadingSet.remove(mDownloadEntry);
            closeStream(in);
            disconnect(conn);
        }
        return null;
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
        if (mDownloadEntry.listener != null) {
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    mDownloadEntry.listener.onStart();
                }
            });
        }
    }

    private void postSuccess() {
        if (mDownloadEntry.listener != null) {
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    mDownloadEntry.listener.onSuccess(new File(mDownloadEntry.localPath));
                }
            });
        }
    }

    private void postFailure(final int reasonCode, final String message) {
        if (mDownloadEntry.listener != null) {
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    mDownloadEntry.listener.onFailure(reasonCode, message);
                }
            });
        }
    }

    private void postProgress() {
        if (mDownloadEntry.listener != null) {
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    mDownloadEntry.listener.onProgress(mDownloadEntry.downloadedLength, mDownloadEntry.totalLength);
                }
            });
        }
    }
}
