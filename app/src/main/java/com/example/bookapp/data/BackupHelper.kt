package com.example.bookapp.data

import android.content.Context
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * پشتیبان کامل نسخه 2: JSON + فایل‌های واقعی تصویر و صدا داخل ZIP.
 * ارجاع‌ها با مسیر معنایی (زمینه/تعزیه/نقش/بخش) ذخیره می‌شوند، نه ID دیتابیس؛
 * بنابراین بازیابی روی دیتابیس تازه هم ارتباط‌ها را درست بازسازی می‌کند.
 */
suspend fun buildBackupJson(context: Context, db: AppDatabase): String {
    val root = JSONObject().apply {
        put("app", "taziehapp")
        put("backupVersion", 2)
    }

    val notesArr = JSONArray()
    db.noteDao().getAll().forEach { n ->
        notesArr.put(JSONObject().apply { put("title", n.title); put("content", n.content) })
    }
    root.put("notes", notesArr)

    val bookmarks = JSONArray()
    for (id in Prefs.getBookmarks(context)) {
        val key = sectionKey(db, id)
        if (key != null) bookmarks.put(key)
    }
    root.put("bookmarks", bookmarks)

    val footnotes = JSONArray()
    val myRoles = JSONArray()
    val dialogues = JSONArray()
    val images = JSONArray()
    val audio = JSONArray()

    db.fieldDao().getAll().forEach { field ->
        db.taziehDao().getByField(field.id).forEach { tazieh ->
            val tKey = taziehKey(field.title, tazieh.title)
            Prefs.getAllMyRoles(context).firstOrNull { it.first == tazieh.id }?.let { pair ->
                db.roleDao().getById(pair.second).let { role ->
                    myRoles.put(JSONObject().apply { put("tazieh", tKey); put("role", role.title) })
                }
            }
            db.taziehImageDao().getByTazieh(tazieh.id).forEach { image ->
                val file = File(image.filePath)
                if (file.exists() && file.isFile) {
                    val archiveName = "media/images/${image.id}_${file.name}"
                    images.put(JSONObject().apply {
                        put("tazieh", tKey); put("caption", image.caption); put("archive", archiveName)
                    })
                }
            }
            db.roleDao().getByTazieh(tazieh.id).forEach { role ->
                db.sectionDao().getByRole(role.id).forEach { section ->
                    val sKey = sectionKey(field.title, tazieh.title, role.title, section.title)
                    db.footnoteDao().getBySection(section.id).forEach { fn ->
                        footnotes.put(JSONObject().apply {
                            put("section", sKey); put("term", fn.term); put("explanation", fn.explanation)
                        })
                    }
                    val audioPath = section.audioUrl?.takeIf { it.isNotBlank() }?.let { File(it) }
                    if (audioPath != null && audioPath.exists() && audioPath.isFile && audioPath.canonicalPath.startsWith(File(context.filesDir, "tazieh_audio").canonicalPath)) {
                        audio.put(JSONObject().apply {
                            put("section", sKey); put("archive", "media/audio/${audioPath.name}")
                        })
                    }
                }
            }
            db.dialogueDao().getByTazieh(tazieh.id).forEach { dialogue ->
                val turns = JSONArray()
                db.dialogueTurnDao().getByDialogue(dialogue.id).forEach { turn ->
                    sectionKey(db, turn.sectionId)?.let { key ->
                        turns.put(JSONObject().apply { put("section", key); put("orderIndex", turn.orderIndex) })
                    }
                }
                dialogues.put(JSONObject().apply { put("tazieh", tKey); put("title", dialogue.title); put("turns", turns) })
            }
        }
    }
    root.put("footnotes", footnotes)
    root.put("myRoles", myRoles)
    root.put("dialogues", dialogues)
    root.put("images", images)
    root.put("audio", audio)
    return root.toString(2)
}

private fun taziehKey(field: String, tazieh: String) = "$field\u001f$tazieh"
private fun sectionKey(field: String, tazieh: String, role: String, section: String) = "$field\u001f$tazieh\u001f$role\u001f$section"

private suspend fun sectionKey(db: AppDatabase, sectionId: Long): String? = runCatching {
    val section = db.sectionDao().getById(sectionId)
    val role = db.roleDao().getById(section.roleId)
    val tazieh = db.taziehDao().getById(role.taziehId) ?: return null
    val field = db.fieldDao().getAll().firstOrNull { it.id == tazieh.fieldId } ?: return null
    sectionKey(field.title, tazieh.title, role.title, section.title)
}.getOrNull()

private suspend fun writeZip(context: Context, db: AppDatabase): ByteArray {
    val out = ByteArrayOutputStream()
    ZipOutputStream(out).use { zip ->
        fun putText(name: String, text: String) {
            zip.putNextEntry(ZipEntry(name)); zip.write(text.toByteArray(Charsets.UTF_8)); zip.closeEntry()
        }
        val json = buildBackupJson(context, db)
        putText("backup.json", json)
        val root = JSONObject(json)
        val images = root.optJSONArray("images") ?: JSONArray()
        for (i in 0 until images.length()) {
            val o = images.getJSONObject(i); val archive = o.getString("archive")
            val file = File(context.filesDir, "tazieh_images").resolve(archive.substringAfterLast('/').substringAfter('_'))
            if (file.exists()) { zip.putNextEntry(ZipEntry(archive)); file.inputStream().use { it.copyTo(zip) }; zip.closeEntry() }
        }
        val audio = root.optJSONArray("audio") ?: JSONArray()
        for (i in 0 until audio.length()) {
            val o = audio.getJSONObject(i); val archive = o.getString("archive")
            val file = File(context.filesDir, "tazieh_audio").resolve(archive.substringAfterLast('/'))
            if (file.exists()) { zip.putNextEntry(ZipEntry(archive)); file.inputStream().use { it.copyTo(zip) }; zip.closeEntry() }
        }
    }
    return out.toByteArray()
}

suspend fun writeBackupToUri(context: Context, db: AppDatabase, uri: Uri, password: String? = null) {
    val bytes = writeZip(context, db)
    val finalBytes = if (password.isNullOrBlank()) bytes else encryptBackupBytes(bytes, password)
    context.contentResolver.openOutputStream(uri)?.use { it.write(finalBytes) }
        ?: error("فایل پشتیبان قابل نوشتن نیست")
}

private fun restoreZipBytes(context: Context, db: AppDatabase, bytes: ByteArray): JSONObject {
    var json: String? = null
    val tempFiles = mutableMapOf<String, ByteArray>()
    ZipInputStream(ByteArrayInputStream(bytes)).use { zis ->
        while (true) {
            val e = zis.nextEntry ?: break
            val data = zis.readBytes()
            if (e.name == "backup.json") json = String(data, Charsets.UTF_8) else if (e.name.startsWith("media/")) tempFiles[e.name] = data
        }
    }
    val root = JSONObject(json ?: error("backup.json پیدا نشد"))
    root.put("__media", JSONObject(tempFiles.mapValues { String(it.value, Charsets.ISO_8859_1) }))
    return root
}

suspend fun restoreBackupFromUri(context: Context, db: AppDatabase, uri: Uri, password: String? = null): Result<Unit> = runCatching {
    val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: error("فایل خوانده نشد")
    val payload = if (bytes.size >= 2 && bytes[0] == 0x50.toByte() && bytes[1] == 0x4b.toByte()) bytes
    else if (!password.isNullOrBlank()) decryptBackupBinary(bytes, password)
    else bytes
    val isZip = payload.size >= 2 && payload[0] == 0x50.toByte() && payload[1] == 0x4b.toByte()
    if (!isZip) {
        val text = if (!password.isNullOrBlank()) decryptBackupBytes(bytes, password) else String(payload, Charsets.UTF_8)
        restoreLegacyV1(context, db, text); return@runCatching
    }
    val obj = restoreZipBytes(context, db, payload)
    restoreV2(context, db, obj)
}

private suspend fun restoreLegacyV1(context: Context, db: AppDatabase, text: String) {
    val root = JSONObject(text)
    val notes = root.optJSONArray("notes") ?: JSONArray()
    for (i in 0 until notes.length()) { val o = notes.getJSONObject(i); db.noteDao().insert(NoteEntity(title=o.getString("title"), content=o.getString("content"))) }
    // نسخه 1 فقط ID داشت؛ فقط IDهایی که در دیتابیس فعلی واقعاً وجود دارند پذیرفته می‌شوند.
    val bookmarks = root.optJSONArray("bookmarks") ?: JSONArray()
    for (i in 0 until bookmarks.length()) { val id = bookmarks.getLong(i); if (runCatching { db.sectionDao().getById(id) }.isSuccess && !Prefs.isBookmarked(context,id)) Prefs.toggleBookmark(context,id) }
}

private suspend fun restoreV2(context: Context, db: AppDatabase, root: JSONObject) {
    val notes = root.optJSONArray("notes") ?: JSONArray()
    for (i in 0 until notes.length()) { val o=notes.getJSONObject(i); db.noteDao().insert(NoteEntity(title=o.getString("title"), content=o.getString("content"))) }
    val keyToSection = mutableMapOf<String, Long>(); val keyToTazieh = mutableMapOf<String, Long>(); val keyToRole = mutableMapOf<String, Long>()
    db.fieldDao().getAll().forEach { f -> db.taziehDao().getByField(f.id).forEach { t -> keyToTazieh[taziehKey(f.title,t.title)] = t.id; db.roleDao().getByTazieh(t.id).forEach { r -> keyToRole["${taziehKey(f.title,t.title)}\u001f${r.title}"]=r.id; db.sectionDao().getByRole(r.id).forEach { s -> keyToSection[sectionKey(f.title,t.title,r.title,s.title)] = s.id } } } }
    val bookmarks = root.optJSONArray("bookmarks") ?: JSONArray(); for(i in 0 until bookmarks.length()) { keyToSection[bookmarks.getString(i)]?.let { if(!Prefs.isBookmarked(context,it)) Prefs.toggleBookmark(context,it) } }
    val fns=root.optJSONArray("footnotes") ?: JSONArray(); for(i in 0 until fns.length()){val o=fns.getJSONObject(i); keyToSection[o.getString("section")]?.let{db.footnoteDao().insert(FootnoteEntity(sectionId=it,term=o.getString("term"),explanation=o.getString("explanation")))}}
    val roles=root.optJSONArray("myRoles") ?: JSONArray(); for(i in 0 until roles.length()){val o=roles.getJSONObject(i); keyToTazieh[o.getString("tazieh")]?.let{tid-> val rid=keyToRole["${o.getString("tazieh")}\u001f${o.getString("role")}"]; if(rid!=null) Prefs.setMyRole(context,tid,rid)}}
    val dialogs=root.optJSONArray("dialogues") ?: JSONArray(); for(i in 0 until dialogs.length()){val o=dialogs.getJSONObject(i); val tid=keyToTazieh[o.getString("tazieh")] ?: continue; val did=db.dialogueDao().insert(DialogueEntity(taziehId=tid,title=o.getString("title"))); val turns=o.getJSONArray("turns"); for(j in 0 until turns.length()){val t=turns.getJSONObject(j); keyToSection[t.getString("section")]?.let{sid->db.dialogueTurnDao().insert(DialogueTurnEntity(dialogueId=did,sectionId=sid,orderIndex=t.getInt("orderIndex")))}}
    }
    val mediaObj=root.optJSONObject("__media") ?: JSONObject(); val images=root.optJSONArray("images") ?: JSONArray(); val imageDir=File(context.filesDir,"tazieh_images").apply{mkdirs()}
    for(i in 0 until images.length()){val o=images.getJSONObject(i); val tid=keyToTazieh[o.getString("tazieh")] ?: continue; val archive=o.getString("archive"); val raw=mediaObj.optString(archive,null) ?: continue; val name="restored_${System.currentTimeMillis()}_${i}.jpg"; val path=File(imageDir,name); path.writeBytes(raw.toByteArray(Charsets.ISO_8859_1)); db.taziehImageDao().insert(TaziehImageEntity(taziehId=tid,filePath=path.absolutePath,caption=o.optString("caption",""))) }
    val audios=root.optJSONArray("audio") ?: JSONArray(); val audioDir=File(context.filesDir,"tazieh_audio").apply{mkdirs()}
    for(i in 0 until audios.length()){val o=audios.getJSONObject(i); val sid=keyToSection[o.getString("section")] ?: continue; val archive=o.getString("archive"); val raw=mediaObj.optString(archive,null) ?: continue; val path=File(audioDir,"restored_${System.currentTimeMillis()}_${i}.mp3"); path.writeBytes(raw.toByteArray(Charsets.ISO_8859_1)); db.sectionDao().updateAudioUrl(sid,path.absolutePath) }
}
