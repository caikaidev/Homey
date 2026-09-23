package com.example.data.backup

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import androidx.room.withTransaction
import com.example.BuildConfig
import com.example.data.database.AppDatabase
import com.example.data.model.categoryEnum
import com.example.data.model.mode
import com.example.domain.prediction.PredictionEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class RestoreMode { MERGE, REPLACE }

data class BackupPreview(
    val fileName: String,
    val data: BackupData
) {
    val productCount: Int get() = data.products.count { it.deletedAt == null }
    val logCount: Int get() = data.stockLogs.size
}

data class RestoreResult(val added: Int, val updated: Int, val snapshotPath: String?)

/**
 * 备份与恢复。
 * - 导出：整库读出 → JSON（包含软删除的数据），写到用户选择的位置；
 * - 自动备份：每天写一份到 App 私有目录 files/backups/auto/，如果用户选了文件夹，同时写一份过去；各保留最近 7 份；
 * - 恢复：先把当前数据存一份快照（可撤回），再在一个事务里合并或覆盖。
 */
class BackupManager(
    context: Context,
    private val db: AppDatabase,
    private val prefs: BackupPrefs = BackupPrefs(context)
) {
    private val appContext = context.applicationContext
    private val resolver get() = appContext.contentResolver

    private val backupRoot: File get() = File(appContext.filesDir, "backups")
    private val autoDir: File get() = File(backupRoot, "auto").apply { mkdirs() }
    private val snapshotDir: File get() = File(backupRoot, "before-restore").apply { mkdirs() }

    suspend fun snapshot(now: Long = System.currentTimeMillis()): BackupData = db.withTransaction {
        BackupData(
            formatVersion = BackupCodec.FORMAT_VERSION,
            exportedAt = now,
            appVersion = BuildConfig.VERSION_NAME,
            products = db.productDao().getAll(),
            inventories = db.inventoryDao().getAll(),
            stockLogs = db.stockLogDao().getAll(),
            todos = db.todoDao().getAll()
        )
    }

    suspend fun exportJson(): String = BackupCodec.encode(snapshot())

    fun suggestedFileName(now: Long = System.currentTimeMillis()): String =
        "homey-backup-" + SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(now)) + ".json"

    fun suggestedCsvName(now: Long = System.currentTimeMillis()): String =
        "homey-items-" + SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(now)) + ".csv"

    /** 导出到用户用"另存为"选的位置。 */
    suspend fun exportTo(uri: Uri) = withContext(Dispatchers.IO) {
        val json = exportJson()
        resolver.openOutputStream(uri, "wt")?.use { it.write(json.toByteArray(Charsets.UTF_8)) }
            ?: throw BackupException("无法写入这个位置")
        prefs.lastBackupAt = System.currentTimeMillis()
    }

    suspend fun exportCsvTo(uri: Uri) = withContext(Dispatchers.IO) {
        val csv = buildCsv()
        resolver.openOutputStream(uri, "wt")?.use {
            // 带 BOM，Excel 打开中文不乱码
            it.write(byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()))
            it.write(csv.toByteArray(Charsets.UTF_8))
        } ?: throw BackupException("无法写入这个位置")
    }

    suspend fun readPreview(uri: Uri): BackupPreview = withContext(Dispatchers.IO) {
        val text = resolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) }
            ?: throw BackupException("无法读取这个文件")
        BackupPreview(fileName = displayName(uri) ?: "备份文件", data = BackupCodec.decode(text.removePrefix("\uFEFF")))
    }

    suspend fun readSnapshot(path: String): BackupPreview = withContext(Dispatchers.IO) {
        val file = File(path)
        if (!file.exists()) throw BackupException("找不到恢复前的快照")
        BackupPreview(fileName = file.name, data = BackupCodec.decode(file.readText()))
    }

    /** 恢复前一定先存快照；[takeSnapshot] 只在"撤回恢复"时为 false。 */
    suspend fun restore(data: BackupData, mode: RestoreMode, takeSnapshot: Boolean = true): RestoreResult =
        withContext(Dispatchers.IO) {
            val snapshotPath = if (takeSnapshot) {
                val file = File(snapshotDir, "before-restore-" + stamp() + ".json")
                file.writeText(exportJson())
                pruneFiles(snapshotDir.listFiles()?.toList().orEmpty(), keep = 5)
                prefs.lastRestoreSnapshot = file.path
                file.path
            } else null

            val result = db.withTransaction {
                when (mode) {
                    RestoreMode.REPLACE -> replaceAll(data)
                    RestoreMode.MERGE -> merge(data)
                }
            }
            result.copy(snapshotPath = snapshotPath)
        }

    private suspend fun replaceAll(data: BackupData): RestoreResult {
        db.todoDao().deleteAll()
        db.stockLogDao().deleteAll()
        db.inventoryDao().deleteAll()
        db.productDao().deleteAll()
        val ids = data.products.map { it.id }.toSet()
        db.productDao().upsertAll(data.products)
        db.inventoryDao().upsertAll(data.inventories.filter { it.productId in ids })
        db.stockLogDao().insertAll(data.stockLogs.filter { it.productId in ids })
        db.todoDao().upsertAll(data.todos)
        return RestoreResult(added = data.products.size, updated = 0, snapshotPath = null)
    }

    /**
     * 合并规则：按 ID 对齐；两边都有时，updatedAt 更新的一方胜出，并带上它的全部库存批次；
     * 流水只追加（ID 相同则跳过）；软删除也是一种"更新"，所以删掉的东西不会被旧备份复活。
     */
    private suspend fun merge(data: BackupData): RestoreResult {
        val existing = db.productDao().getAll().associateBy { it.id }
        val winners = data.products.filter { incoming ->
            val local = existing[incoming.id]
            local == null || incoming.updatedAt > local.updatedAt
        }
        db.productDao().upsertAll(winners)
        val winnerIds = winners.map { it.id }.toSet()
        winnerIds.forEach { db.inventoryDao().deleteByProduct(it) }
        db.inventoryDao().upsertAll(data.inventories.filter { it.productId in winnerIds })

        val allIds = existing.keys + winnerIds
        db.stockLogDao().insertAll(data.stockLogs.filter { it.productId in allIds })

        val localTodos = db.todoDao().getAll().associateBy { it.id }
        db.todoDao().upsertAll(data.todos.filter { t -> localTodos[t.id]?.let { t.updatedAt > it.updatedAt } ?: true })

        val added = winners.count { existing[it.id] == null }
        return RestoreResult(added = added, updated = winners.size - added, snapshotPath = null)
    }

    /** 每日自动备份。返回是否成功写入至少一处。 */
    suspend fun autoBackup(now: Long = System.currentTimeMillis()): Boolean = withContext(Dispatchers.IO) {
        val json = exportJson()
        val name = "homey-auto-" + stamp(now) + ".json"
        var ok = false

        runCatching {
            File(autoDir, name).writeText(json)
            pruneFiles(autoDir.listFiles()?.filter { it.name.startsWith("homey-auto-") }.orEmpty(), keep = KEEP_AUTO)
            ok = true
        }

        prefs.folderUri?.let { tree ->
            runCatching {
                writeToTree(tree, name, json)
                pruneTree(tree)
                ok = true
            }
        }
        if (ok) prefs.lastBackupAt = now
        ok
    }

    private fun writeToTree(tree: Uri, name: String, json: String) {
        val parent = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
        val doc = DocumentsContract.createDocument(resolver, parent, "application/json", name)
            ?: throw BackupException("无法在备份文件夹里创建文件")
        resolver.openOutputStream(doc, "wt")?.use { it.write(json.toByteArray(Charsets.UTF_8)) }
    }

    private fun pruneTree(tree: Uri) {
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
        val found = mutableListOf<Pair<String, String>>() // documentId to name
        resolver.query(
            children,
            arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME),
            null, null, null
        )?.use { c ->
            while (c.moveToNext()) {
                val name = c.getString(1) ?: continue
                if (name.startsWith("homey-auto-")) found += c.getString(0) to name
            }
        }
        found.sortedByDescending { it.second }.drop(KEEP_AUTO).forEach { (docId, _) ->
            runCatching { DocumentsContract.deleteDocument(resolver, DocumentsContract.buildDocumentUriUsingTree(tree, docId)) }
        }
    }

    private fun pruneFiles(files: List<File>, keep: Int) {
        files.sortedByDescending { it.name }.drop(keep).forEach { it.delete() }
    }

    private suspend fun buildCsv(): String {
        val now = System.currentTimeMillis()
        val products = db.productDao().getActive()
        val batches = db.inventoryDao().getAll().groupBy { it.productId }
        val sb = StringBuilder("名称,分类,记录方式,数量,单位,余量%,状态,存放位置,备注\n")
        for (p in products) {
            val s = PredictionEngine.evaluate(p, batches[p.id].orEmpty(), now)
            val row = listOf(
                p.name, p.categoryEnum.label, p.mode.label, PredictionEngine.formatQty(s.quantity), p.unit,
                s.levelPercent.toString(), s.statusText, p.location, p.note
            )
            sb.append(row.joinToString(",") { csvCell(it) }).append('\n')
        }
        return sb.toString()
    }

    private fun csvCell(value: String): String =
        if (value.any { it == ',' || it == '"' || it == '\n' }) "\"" + value.replace("\"", "\"\"") + "\"" else value

    private fun displayName(uri: Uri): String? = runCatching {
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
    }.getOrNull()

    private fun stamp(now: Long = System.currentTimeMillis()): String =
        SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date(now))

    companion object {
        const val KEEP_AUTO = 7
    }
}
