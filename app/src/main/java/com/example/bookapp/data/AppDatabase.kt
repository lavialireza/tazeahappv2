package com.example.bookapp.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.withTransaction
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL

@Database(
    entities = [
        FieldEntity::class,
        TaziehEntity::class,
        RoleEntity::class,
        SectionEntity::class,
        NoteEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun fieldDao(): FieldDao
    abstract fun taziehDao(): TaziehDao
    abstract fun roleDao(): RoleDao
    abstract fun sectionDao(): SectionDao
    abstract fun searchDao(): SearchDao
    abstract fun noteDao(): NoteDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "bookapp.db"
                )
                    // چون بین نسخه ۱ و ۲ فقط جدول یادداشت‌ها اضافه شده،
                    // در صورت نبود Migration رسمی، دیتابیس محتوا از نو ساخته می‌شود
                    // (بارگذاری اولیه دوباره از assets/sample_data.json انجام می‌شود)
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}

/**
 * داده‌های واقعی/نمونه را از فایل assets/sample_data.json می‌خواند
 * و در صورتی که دیتابیس خالی باشد، آن‌ها را وارد می‌کند.
 */
suspend fun seedDatabaseIfEmpty(context: Context, db: AppDatabase) {
    if (db.fieldDao().getAll().isNotEmpty()) return

    val jsonText = context.assets.open("sample_data.json")
        .bufferedReader(Charsets.UTF_8).use { it.readText() }
    insertContentFromJson(db, jsonText)
}

/**
 * محتوای فعلی (زمینه/تعزیه/نقش/بخش) را کامل پاک کرده و از یک متن JSON
 * که از اینترنت دریافت شده، دوباره می‌سازد. برای «بروزرسانی محتوا بدون
 * نیاز به ساخت نسخه جدید اپ» استفاده می‌شود.
 * آدرس پیش‌فرض: فایل sample_data.json روی گیت‌هاب (شاخه main).
 */
suspend fun syncRemoteContent(
    db: AppDatabase,
    url: String = "https://raw.githubusercontent.com/lavialireza/taziehapp/main/app/src/main/assets/sample_data.json"
): Result<Unit> {
    return try {
        val jsonText = withHttpGet(url)
        db.withTransaction {
            db.sectionDao().deleteAll()
            db.roleDao().deleteAll()
            db.taziehDao().deleteAll()
            db.fieldDao().deleteAll()
        }
        insertContentFromJson(db, jsonText)
        Result.success(Unit)
    } catch (e: Exception) {
        Result.failure(e)
    }
}

private fun withHttpGet(urlString: String): String {
    val connection = URL(urlString).openConnection() as HttpURLConnection
    connection.connectTimeout = 15000
    connection.readTimeout = 15000
    connection.requestMethod = "GET"
    return try {
        connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
    } finally {
        connection.disconnect()
    }
}

private suspend fun insertContentFromJson(db: AppDatabase, jsonText: String) {
    val fields = JSONArray(jsonText)
    db.withTransaction {
        for (fi in 0 until fields.length()) {
            val fieldObj = fields.getJSONObject(fi)
            val fieldId = db.fieldDao().insert(FieldEntity(title = fieldObj.getString("title")))

            val taziehs = fieldObj.getJSONArray("taziehs")
            for (ti in 0 until taziehs.length()) {
                val taziehObj = taziehs.getJSONObject(ti)
                val taziehId = db.taziehDao().insert(
                    TaziehEntity(fieldId = fieldId, title = taziehObj.getString("title"))
                )

                val roles = taziehObj.getJSONArray("roles")
                for (ri in 0 until roles.length()) {
                    val roleObj = roles.getJSONObject(ri)
                    val roleId = db.roleDao().insert(
                        RoleEntity(taziehId = taziehId, title = roleObj.getString("title"))
                    )

                    val sections = roleObj.getJSONArray("sections")
                    for (si in 0 until sections.length()) {
                        val secObj = sections.getJSONObject(si)
                        db.sectionDao().insert(
                            SectionEntity(
                                roleId = roleId,
                                orderIndex = si,
                                title = secObj.getString("title"),
                                content = secObj.getString("content")
                            )
                        )
                    }
                }
            }
        }
    }
}
