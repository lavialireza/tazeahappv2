package com.example.bookapp.data

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

private const val BACKUP_VERSION = 2
private const val BACKUP_MAGIC = "TAZIEH_BACKUP_ZIP"
private const val SEP = "\u001f"

private fun key(vararg parts: String): String = parts.joinToString(SEP) { it.trim() }

private suspend fun sectionContext(db: AppDatabase, sectionId: Long): String? {
    val section = db.sectionDao().getById(sectionId)
    val role = db.roleDao().getById(section.roleId)
    val tazieh = db.taziehDao().getById(role.taziehId) ?: return null
    val field = db.fieldDao().getAll().firstOrNull { it.id == tazieh.fieldId } ?: return null
    return key(field.title, tazieh.title, role.title, section.title)
}

private suspend fun buildBackupZip(context: Context, db: AppDatabase): ByteArray {
    val root = JSONObject().apply {
        put("app", "taziehapp")
        put("backupVersion", BACKUP_VERSION)
        put("format", BACKUP_MAGIC)
    }

    val notes = JSONArray()
    db.noteDao().getAll().forEach { n ->
        notes.put(JSONObject().apply {
            put("title", n.title)
            put("content", n.content)
            put("createdAt", n.createdAt)
        })
    }
    root.put("notes", notes)

    val bookmarks = JSONArray()
    Prefs.getBookmarks(context).mapNotNull { db.sectionDao().getByIdOrNull(it) }.forEach { sectionId ->
        sectionContext(db, sectionId)?.let { bookmarks.put(it) }
    }
    root.put("bookmarks", bookmarks)

    val footnotes = JSONArray()
    db.fieldDao().getAll().forEach { field ->
        db.taziehDao().getByField(field.id).forEach { tazieh ->
            db.roleDao().getByTazieh(tazieh.id).forEach { role ->
                db.sectionDao().getByRole(role.id).forEach { section ->
                    val sectionKey = key(field.title, tazieh.title, role.title, section.title)
                    db.footnoteDao().getBySection(section.id).forEach { fn ->
                        footnotes.put(JSONObject().apply {
                            put("sectionKey", sectionKey)
                            put("term", fn.term)
                            put("explanation", fn.explanation)
                        })
                    }
                }
            }
        }
    }
    root.put("footnotes", footnotes)

    val myRoles = JSONArray()
    Prefs.getAllMyRoles(context).forEach { (taziehId, roleId) ->
        val t = db.taziehDao().getById(taziehId)
        val r = runCatching { db.roleDao().getById(roleId) }.getOrNull()
        if (t != null && r != null) {
            val field = db.fieldDao().getAll().firstOrNull { it.id == t.fieldId }
            if (field != null) myRoles.put(JSONObject().apply {
                put("taziehKey", key(field.title, t.title))
                put("roleTitle", r.title)
            })
        }
    }
    root.put("myRoles", myRoles)

    val dialogues = JSONArray()
    db.fieldDao().getAll().forEach { field ->
        db.taziehDao().getByField(field.id).forEach { tazieh ->
            val taziehKey = key(field.title, tazieh.title)
            db.dialogueDao().getByTazieh(tazieh.id).forEach { dialogue ->
                val turns = JSONArray()
                db.dialogueTurnDao().getByDialogue(dialogue.id).forEach { turn ->
                    sectionContext(db, turn.sectionId)?.let { sectionKey ->
                        turns.put(JSONObject().apply {
                            put("sectionKey", sectionKey)
                            put("orderIndex", turn.orderIndex)
                        })
                    }
                }
                dialogues.put(JSONObject().apply {
                    put("taziehKey", taziehKey)
                    put("title", dialogue.title)
                    put("turns", turns)
                })
            }
        }
    }
    root.put("dialogues", dialogues)

    val imageMeta = JSONArray()
    val imageFiles = linkedMapOf<String, ByteArray>()
    db.taziehImageDao().getAll().forEach { image ->
        val t = db.taziehDao().getById(image.taziehId) ?: return@forEach
        val field = db.fieldDao().getAll().firstOrNull { it.id == t.fieldId } ?: return@forEach
        val source = File(image.filePath)
        if (!source.isFile) return@forEach
        val archiveName = "media/images/${UUID.randomUUID()}.bin"
        imageFiles[archiveName] = source.readBytes()
        imageMeta.put(JSONObject().apply {
            put("taziehKey", key(field.title, t.title))
            put("caption", image.caption)
            put("file", archiveName)
        })
    }
    root.put("images", imageMeta)

    val audioMeta = JSONArray()
    val audioFiles = linkedMapOf<String, ByteArray>()
    db.fieldDao().getAll().forEach { field ->
        db.taziehDao().getByField(field.id).forEach { tazieh ->
            db.roleDao().getByTazieh(tazieh.id).forEach { role ->
                db.sectionDao().getByRole(role.id).forEach { section ->
                    val audioUrl = section.audioUrl ?: return@forEach
                    val source = File(audioUrl)
                    if (!source.isFile) return@forEach
                    val archiveName = "media/audio/${UUID.randomUUID()}.bin"
                    audioFiles[archiveName] = source.readBytes()
                    audioMeta.put(JSONObject().apply {
                        put("sectionKey", key(field.title, tazieh.title, role.title, section.title))
                        put("file", archiveName)
                    })
                }
            }
        }
    }
    root.put("audio", audioMeta)

    val zipBytes = ByteArrayOutputStream()
    ZipOutputStream(zipBytes).use { zip ->
        val json = root.toString(2).toByteArray(Charsets.UTF_8)
        zip.putNextEntry(ZipEntry("backup.json")); zip.write(json); zip.closeEntry()
        imageFiles.forEach { (name, bytes) -> zip.putNextEntry(ZipEntry(name)); zip.write(bytes); zip.closeEntry() }
        audioFiles.forEach { (name, bytes) -> zip.putNextEntry(ZipEntry(name)); zip.write(bytes); zip.closeEntry() }
    }
    return zipBytes.toByteArray()
}

/** پشتیبان کامل: داده‌های کاربر + فایل‌های تصویر و صوت محلی. */
suspend fun writeBackupToUri(context: Context, db: AppDatabase, uri: Uri, password: String? = null) {
    val zip = buildBackupZip(context, db)
    val bytes = if (password.isNullOrBlank()) zip else encryptBackupBytes(zip, password)
    context.contentResolver.openOutputStream(uri)?.use { it.write(bytes) }
        ?: error("مسیر ذخیره‌سازی باز نشد")
}

private data class Archive(val json: JSONObject, val files: Map<String, ByteArray>)

private fun parseArchive(bytes: ByteArray, password: String?): Archive {
    val raw = if (password.isNullOrBlank()) bytes else decryptBackupToBytes(bytes, password)
    // سازگاری با پشتیبان قدیمی JSON
    if (raw.firstOrNull()?.toInt() == '{'.code) return Archive(JSONObject(String(raw, Charsets.UTF_8)), emptyMap())
    val files = linkedMapOf<String, ByteArray>()
    ZipInputStream(raw.inputStream()).use { zis ->
        while (true) {
            val e = zis.nextEntry ?: break
            if (!e.isDirectory) files[e.name] = zis.readBytes()
        }
    }
    val json = JSONObject(String(files["backup.json"] ?: error("backup.json در فایل پشتیبان پیدا نشد"), Charsets.UTF_8))
    return Archive(json, files)
}

private suspend fun resolveSection(db: AppDatabase, sectionKey: String): SectionEntity? {
    val p = sectionKey.split(SEP)
    if (p.size != 4) return null
    val field = db.fieldDao().getByTitle(p[0]) ?: return null
    val t = db.taziehDao().getByTitle(field.id, p[1]) ?: return null
    val r = db.roleDao().getByTitle(t.id, p[2]) ?: return null
    return db.sectionDao().getByTitle(r.id, p[3])
}

private suspend fun resolveTazieh(db: AppDatabase, taziehKey: String): TaziehEntity? {
    val p = taziehKey.split(SEP)
    if (p.size != 2) return null
    val field = db.fieldDao().getByTitle(p[0]) ?: return null
    return db.taziehDao().getByTitle(field.id, p[1])
}

suspend fun restoreBackupFromUri(context: Context, db: AppDatabase, uri: Uri, password: String? = null): Result<Unit> = try {
    val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
        ?: return Result.failure(IllegalStateException("فایل خوانده نشد"))
    val archive = parseArchive(bytes, password)
    val root = archive.json
    val version = root.optInt("backupVersion", 1)

    db.withTransaction {
        val notes = root.optJSONArray("notes") ?: JSONArray()
        for (i in 0 until notes.length()) {
            val o = notes.getJSONObject(i)
            val title = o.optString("title")
            val content = o.optString("content")
            if (title.isNotBlank() && content.isNotBlank()) db.noteDao().insert(NoteEntity(
                title = title, content = content, createdAt = o.optLong("createdAt", System.currentTimeMillis())
            ))
        }

        val bookmarks = root.optJSONArray("bookmarks") ?: JSONArray()
        for (i in 0 until bookmarks.length()) {
            val section = resolveSection(db, bookmarks.optString(i))
            if (section != null && !Prefs.isBookmarked(context, section.id)) Prefs.toggleBookmark(context, section.id)
        }

        val footnotes = root.optJSONArray("footnotes") ?: JSONArray()
        for (i in 0 until footnotes.length()) {
            val o = footnotes.getJSONObject(i)
            val section = resolveSection(db, o.optString("sectionKey")) ?: continue
            db.footnoteDao().insert(FootnoteEntity(sectionId = section.id, term = o.optString("term"), explanation = o.optString("explanation")))
        }

        val myRoles = root.optJSONArray("myRoles") ?: JSONArray()
        for (i in 0 until myRoles.length()) {
            val o = myRoles.getJSONObject(i)
            val t = resolveTazieh(db, o.optString("taziehKey")) ?: continue
            val role = db.roleDao().getByTitle(t.id, o.optString("roleTitle")) ?: continue
            Prefs.setMyRole(context, t.id, role.id)
        }

        val dialogues = root.optJSONArray("dialogues") ?: JSONArray()
        for (i in 0 until dialogues.length()) {
            val o = dialogues.getJSONObject(i)
            val t = resolveTazieh(db, o.optString("taziehKey")) ?: continue
            val title = o.optString("title")
            val dialogue = db.dialogueDao().getByTitle(t.id, title)
                ?: db.dialogueDao().insert(DialogueEntity(taziehId = t.id, title = title)).let { db.dialogueDao().getById(it) }
            o.optJSONArray("turns")?.let { turns ->
                for (j in 0 until turns.length()) {
                    val turn = turns.getJSONObject(j)
                    val section = resolveSection(db, turn.optString("sectionKey")) ?: continue
                    db.dialogueTurnDao().insert(DialogueTurnEntity(dialogueId = dialogue.id, sectionId = section.id, orderIndex = turn.optInt("orderIndex")))
                }
            }
        }
    }

    // رسانه‌ها بعد از تراکنش DB بازیابی می‌شوند تا مسیرهای جدید به رکوردهای واقعی وصل شوند.
    val images = root.optJSONArray("images") ?: JSONArray()
    for (i in 0 until images.length()) {
        val o = images.getJSONObject(i)
        val t = resolveTazieh(db, o.optString("taziehKey")) ?: continue
        val data = archive.files[o.optString("file")] ?: continue
        val dir = File(context.filesDir, "tazieh_images").apply { mkdirs() }
        val path = File(dir, "${UUID.randomUUID()}.jpg").absolutePath
        File(path).writeBytes(data)
        db.taziehImageDao().insert(TaziehImageEntity(taziehId = t.id, filePath = path, caption = o.optString("caption")))
    }

    val audio = root.optJSONArray("audio") ?: JSONArray()
    for (i in 0 until audio.length()) {
        val o = audio.getJSONObject(i)
        val section = resolveSection(db, o.optString("sectionKey")) ?: continue
        val data = archive.files[o.optString("file")] ?: continue
        val dir = File(context.filesDir, "tazieh_audio").apply { mkdirs() }
        val path = File(dir, "${UUID.randomUUID()}.mp3").absolutePath
        File(path).writeBytes(data)
        db.sectionDao().updateAudioUrl(section.id, path)
    }

    // نسخه ۱ قدیمی شناسه‌های Room را داشت و قابل نگاشت امن نبود؛ داده‌های ساختاری
    // آن نسخه عمداً با شناسه‌های قدیمی به رکورد دیگری وصل نمی‌شوند.
    if (version == 1) {
        // notes/bookmarks legacy are handled only when the old IDs still exist.
        // No unsafe ID remapping is attempted.
    }
    Result.success(Unit)
} catch (e: Exception) {
    Result.failure(e)
}
}
