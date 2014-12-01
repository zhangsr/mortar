package com.cvte.mortar;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.database.sqlite.SQLiteDatabase;

import java.io.File;

/**
 * @description:
 * @author: Saul
 * @date: 14-11-17
 * @version: 1.0
 */
public class MortarProvider {
    private static final String WHERE_CLAUSE = DownloadEntry.COLUMN_URL + "=? AND " + DownloadEntry.COLUMN_LOCAL_PATH + "=?";

    public static void save(Context context, DownloadEntry downloadEntry) {
        if (downloadEntry == null) {
            MortarLog.e("Download entry is null");
            return;
        }

        MortarDBHelper dbHelper = new MortarDBHelper(context);
        SQLiteDatabase db = dbHelper.getWritableDatabase();

        String[] whereArgs = new String[]{downloadEntry.url, downloadEntry.localPath};
        Cursor cursor = db.query(true, DownloadEntry.TABLE_NAME, null, WHERE_CLAUSE, whereArgs , null, null, null, null);
        ContentValues values = new ContentValues();
        switch (cursor.getCount()) {
            case 0:
                values.put(DownloadEntry.COLUMN_URL, downloadEntry.url);
                values.put(DownloadEntry.COLUMN_LOCAL_PATH, downloadEntry.localPath);
                values.put(DownloadEntry.COLUMN_DOWNLOADED_LENGTH, downloadEntry.downloadedLength);
                values.put(DownloadEntry.COLUMN_TOTAL_LENGTH, downloadEntry.totalLength);
                db.insert(DownloadEntry.TABLE_NAME, "null", values);
                break;
            case 1:
                values.put(DownloadEntry.COLUMN_DOWNLOADED_LENGTH, downloadEntry.downloadedLength);
                int result = db.update(DownloadEntry.TABLE_NAME, values, WHERE_CLAUSE, whereArgs);
                if (result != 1) {
                    MortarLog.e("Update Error !");
                }
                break;
            default:
                MortarLog.e("Same downloading exist !");
        }
        cursor.close();
        db.close();
    }

    public static DownloadEntry load(Context context, String url, String localPath) {
        DownloadEntry downloadEntry = null;
        MortarDBHelper dbHelper = new MortarDBHelper(context);
        SQLiteDatabase db = dbHelper.getWritableDatabase();

        String[] whereArgs = new String[]{url, localPath};
        Cursor cursor = db.query(true, DownloadEntry.TABLE_NAME, null, WHERE_CLAUSE, whereArgs , null, null, null, null);
        cursor.moveToFirst();
        switch (cursor.getCount()) {
            case 0:
                break;
            case 1:
                if (new File(localPath).exists()) {
                    downloadEntry = new DownloadEntry(url, localPath,
                            cursor.getLong(cursor.getColumnIndexOrThrow(DownloadEntry.COLUMN_DOWNLOADED_LENGTH)),
                            cursor.getLong(cursor.getColumnIndexOrThrow(DownloadEntry.COLUMN_TOTAL_LENGTH)));
                } else { //TODO 14-11-19 handle file modified but still has record in db
                    // handle removed file but still has record in db
                    db.delete(DownloadEntry.TABLE_NAME, WHERE_CLAUSE, whereArgs);
                }
                break;
            default:
                MortarLog.e("Same downloading exist !");
        }
        cursor.close();
        db.close();
        return downloadEntry;
    }

    public static void print(Context context) {
        MortarDBHelper dbHelper = new MortarDBHelper(context);
        SQLiteDatabase db = dbHelper.getWritableDatabase();

        Cursor cursor = db.query(true, DownloadEntry.TABLE_NAME, null, null, null, null, null, null, null);
        cursor.moveToFirst();
        MortarLog.d(DatabaseUtils.dumpCursorToString(cursor));
        cursor.close();
        db.close();
    }
}
