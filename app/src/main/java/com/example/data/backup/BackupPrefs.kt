package com.example.data.backup

import android.content.Context
import android.net.Uri

/** 备份相关的小设置，放在 SharedPreferences（它本身也在系统备份的范围内）。 */
class BackupPrefs(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("backup_prefs", Context.MODE_PRIVATE)

    var autoBackupEnabled: Boolean
        get() = prefs.getBoolean(KEY_AUTO, true)
        set(value) = prefs.edit().putBoolean(KEY_AUTO, value).apply()

    /** 用户选的备份文件夹（SAF 树 URI）；为空时只备份在 App 私有目录。 */
    var folderUri: Uri?
        get() = prefs.getString(KEY_FOLDER, null)?.let(Uri::parse)
        set(value) = prefs.edit().putString(KEY_FOLDER, value?.toString()).apply()

    var folderLabel: String?
        get() = prefs.getString(KEY_FOLDER_LABEL, null)
        set(value) = prefs.edit().putString(KEY_FOLDER_LABEL, value).apply()

    var lastBackupAt: Long
        get() = prefs.getLong(KEY_LAST, 0L)
        set(value) = prefs.edit().putLong(KEY_LAST, value).apply()

    /** 最近一次恢复前自动存下的快照文件路径，用于"撤回恢复"。 */
    var lastRestoreSnapshot: String?
        get() = prefs.getString(KEY_SNAPSHOT, null)
        set(value) = prefs.edit().putString(KEY_SNAPSHOT, value).apply()

    private companion object {
        const val KEY_AUTO = "auto_backup_enabled"
        const val KEY_FOLDER = "folder_uri"
        const val KEY_FOLDER_LABEL = "folder_label"
        const val KEY_LAST = "last_backup_at"
        const val KEY_SNAPSHOT = "last_restore_snapshot"
    }
}
