package com.cvte.mortar;

import java.io.File;

/**
 * @description:
 * @author: Saul
 * @date: 14-12-3
 * @version: 1.0
 */
public interface OnDownloadListener {
    void onStart();
    void onProgress(long downloaded, long total);
    void onSuccess(File file);
    void onFailure(int reasonCode, String message);
}
