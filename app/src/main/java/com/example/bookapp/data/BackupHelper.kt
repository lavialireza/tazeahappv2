package com.example.bookapp.data

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File

/**
 * خروجی گرفتن از یادداشت‌ها و علاقه‌مندی‌ها در یک فایل متنی ساده،
 * و اشتراک‌گذاری آن (مثلاً ذخیره در گوگل‌درایو یا ارسال به خود).
 */
suspend fun exportBackup(context: Context, db: AppDatabase) {
    val notes = db.noteDao().getAll()
    val bookmarkIds = Prefs.getBookmarks(context).toList()
    val bookmarks = if (bookmarkIds.isNotEmpty()) db.searchDao().getByIds(bookmarkIds) else emptyList()

    val sb = StringBuilder()
    sb.appendLine("پشتیبان یادداشت‌ها و علاقه‌مندی‌های اپ تعزیه و شبیه‌خوانی")
    sb.appendLine("==========================================")
    sb.appendLine()
    sb.appendLine("--- یادداشت‌ها ---")
    if (notes.isEmpty()) {
        sb.appendLine("(یادداشتی ثبت نشده)")
    } else {
        notes.forEach {
            sb.appendLine("* ${it.title}")
            sb.appendLine(it.content)
            sb.appendLine()
        }
    }
    sb.appendLine()
    sb.appendLine("--- علاقه‌مندی‌ها ---")
    if (bookmarks.isEmpty()) {
        sb.appendLine("(موردی نشان نشده)")
    } else {
        bookmarks.forEach {
            sb.appendLine("* ${it.sectionTitle} (${it.fieldTitle} ← ${it.taziehTitle} ← ${it.roleTitle})")
        }
    }

    val file = File(context.cacheDir, "پشتیبان-تعزیه.txt")
    file.writeText(sb.toString(), Charsets.UTF_8)

    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, "ذخیره یا اشتراک‌گذاری پشتیبان"))
}
