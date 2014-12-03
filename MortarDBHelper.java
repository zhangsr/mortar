package com.cvte.mortar;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

/**
 * @description:
 * @author: Saul
 * @date: 14-11-17
 * @version: 1.0
 */
public class MortarDBHelper extends SQLiteOpenHelper {
    private static final int DB_VERSION = 3;
    private static final String DB_NAME = "Mortar.db";
    private static final String TYPE_TEXT = " TEXT";
    private static final String TYPE_INTEGER = " INTEGER";
    private static final String COMMA_SEP = ",";
    private static final String SQL_CREATE_DOWNLOAD_ENTRY =
            "CREATE TABLE " + DownloadEntry.TABLE_NAME + " (" +
                    DownloadEntry._ID + TYPE_INTEGER + " PRIMARY KEY" + COMMA_SEP +
                    DownloadEntry.COLUMN_URL + TYPE_TEXT + COMMA_SEP +
                    DownloadEntry.COLUMN_LOCAL_PATH + TYPE_TEXT + COMMA_SEP +
                    DownloadEntry.COLUMN_DOWNLOADED_LENGTH + TYPE_INTEGER + COMMA_SEP +
                    DownloadEntry.COLUMN_TOTAL_LENGTH + TYPE_INTEGER +
            " )";
    private static final String SQL_DELETE_DOWNLOAD_ENTRY =
            "DROP TABLE IF EXISTS " + DownloadEntry.TABLE_NAME;

    public MortarDBHelper(Context context) {
        super(context, DB_NAME, null, DB_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL(SQL_CREATE_DOWNLOAD_ENTRY);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL(SQL_DELETE_DOWNLOAD_ENTRY);
        onCreate(db);
    }

    @Override
    public void onDowngrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        onUpgrade(db, oldVersion, newVersion);
    }
}
