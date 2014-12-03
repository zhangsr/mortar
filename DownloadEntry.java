package com.cvte.mortar;

import android.provider.BaseColumns;

/**
 * @description:
 * @author: Saul
 * @date: 14-11-17
 * @version: 1.0
 */
public class DownloadEntry implements BaseColumns {
    public static final String TABLE_NAME = "download";
    public static final String COLUMN_URL = "url";
    public static final String COLUMN_LOCAL_PATH = "local_path";
    public static final String COLUMN_DOWNLOADED_LENGTH = "downloaded_length";
    public static final String COLUMN_TOTAL_LENGTH = "total_length";

    public String url;
    public String localPath;
    public long downloadedLength;
    public long totalLength;
    public OnDownloadListener listener;

    public DownloadEntry(String url, String localPath, long downloadedLength, long totalLength) {
        this.url = url;
        this.localPath = localPath;
        this.downloadedLength = downloadedLength;
        this.totalLength = totalLength;
    }

    @Override
    public boolean equals(Object o) {
        return hashCode() == o.hashCode();
    }

    @Override
    public int hashCode() {
        return (url + localPath).hashCode();
    }
}
