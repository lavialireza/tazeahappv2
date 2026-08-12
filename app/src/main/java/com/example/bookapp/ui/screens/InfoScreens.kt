package com.example.bookapp.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.bookapp.BuildConfig
import com.example.bookapp.data.AppDatabase
import com.example.bookapp.data.Prefs
import com.example.bookapp.data.exportBackup
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(
    fieldsCount: Int,
    taziehsCount: Int,
    rolesCount: Int,
    sectionsCount: Int,
    readCount: Int,
    streakDays: Int,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("درباره برنامه") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "بازگشت")
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            Text(
                "این اپلیکیشن یک کتابخانه دیجیتال از متون تعزیه است که بر اساس " +
                        "زمینه، تعزیه، نقش و بخش دسته‌بندی شده است."
            )
            Spacer(Modifier.height(20.dp))
            Text("آمار مجموعه:", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(8.dp))
            Text("$fieldsCount زمینه")
            Text("$taziehsCount تعزیه")
            Text("$rolesCount نقش")
            Text("$sectionsCount بخش")

            Spacer(Modifier.height(20.dp))
            Text("آمار مطالعه شما:", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(8.dp))
            Text("$readCount بخش را تا الان خوانده‌اید")
            if (streakDays > 1) {
                Text("$streakDays روز متوالی سر زده‌اید 🔥")
            }

            Spacer(Modifier.height(24.dp))
            Button(onClick = {
                val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(
                        android.content.Intent.EXTRA_TEXT,
                        "این اپ رو ببین: «تعزیه و شبیه‌خوانی» — کتابخانه‌ای کامل و آفلاین از نسخه‌های تعزیه.\nhttps://github.com/lavialireza/taziehapp"
                    )
                }
                context.startActivity(android.content.Intent.createChooser(intent, "معرفی اپ به دیگران"))
            }) {
                Text("معرفی این اپ به دیگران")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    darkMode: Boolean,
    onDarkModeChange: (Boolean) -> Unit,
    fontScale: Float,
    onFontScaleChange: (Float) -> Unit,
    fontChoice: String,
    onFontChoiceChange: (String) -> Unit,
    themeChoice: String,
    onThemeChoiceChange: (String) -> Unit,
    onSyncContent: suspend () -> Result<Unit>,
    db: AppDatabase,
    onBack: () -> Unit
) {
    var syncing by remember { mutableStateOf(false) }
    var syncMessage by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("تنظیمات") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "بازگشت")
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)) {

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("حالت شب (تیره)", style = MaterialTheme.typography.bodyLarge)
                Switch(checked = darkMode, onCheckedChange = onDarkModeChange)
            }

            Spacer(Modifier.height(24.dp))

            Text("سایز متن", style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                FontSizeOption("کوچک", 0.85f, fontScale, onFontScaleChange)
                FontSizeOption("متوسط", 1.0f, fontScale, onFontScaleChange)
                FontSizeOption("بزرگ", 1.3f, fontScale, onFontScaleChange)
                FontSizeOption("خیلی بزرگ", 1.6f, fontScale, onFontScaleChange)
            }

            Spacer(Modifier.height(24.dp))
            Text("تم رنگی", style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                ThemeOption("طلایی", "default", themeChoice, onThemeChoiceChange)
                ThemeOption("سبز", "green", themeChoice, onThemeChoiceChange)
                ThemeOption("قرمز", "red", themeChoice, onThemeChoiceChange)
            }

            Spacer(Modifier.height(24.dp))
            Text("فونت متن", style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                com.example.bookapp.ui.theme.FontChoiceLabels.forEach { (key, label) ->
                    ThemeOption(label, key, fontChoice, onFontChoiceChange)
                }
            }

            Spacer(Modifier.height(32.dp))
            HorizontalDivider()
            Spacer(Modifier.height(16.dp))

            Text("بروزرسانی محتوا", style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.height(4.dp))
            Text(
                "اگر محتوای جدیدی به گیت‌هاب اضافه شده، با این دکمه بدون نیاز به نصب دوباره اپ، محتوا به‌روز می‌شود (نیاز به اینترنت دارد).",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = {
                    syncing = true
                    syncMessage = null
                    scope.launch {
                        val result = onSyncContent()
                        syncing = false
                        syncMessage = if (result.isSuccess) {
                            "محتوا با موفقیت به‌روزرسانی شد ✅"
                        } else {
                            "خطا در بروزرسانی — اتصال اینترنت را بررسی کنید ❌"
                        }
                    }
                },
                enabled = !syncing
            ) {
                Text(if (syncing) "در حال بروزرسانی..." else "بروزرسانی محتوا از اینترنت")
            }
            syncMessage?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, style = MaterialTheme.typography.bodySmall)
            }

            Spacer(Modifier.height(32.dp))
            HorizontalDivider()
            Spacer(Modifier.height(16.dp))

            Text("پشتیبان‌گیری", style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.height(4.dp))
            Text(
                "یادداشت‌ها و علاقه‌مندی‌های خود را در یک فایل متنی ذخیره یا اشتراک‌گذاری کنید (مثلاً برای وقتی گوشی عوض می‌کنید).",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(12.dp))
            Button(onClick = {
                scope.launch { exportBackup(context, db) }
            }) {
                Text("خروجی گرفتن از یادداشت‌ها و علاقه‌مندی‌ها")
            }
        }
    }
}

@Composable
private fun FontSizeOption(label: String, value: Float, current: Float, onSelect: (Float) -> Unit) {
    val selected = kotlin.math.abs(current - value) < 0.01f
    FilterChip(
        selected = selected,
        onClick = { onSelect(value) },
        label = { Text(label) }
    )
}

@Composable
private fun ThemeOption(label: String, value: String, current: String, onSelect: (String) -> Unit) {
    FilterChip(
        selected = current == value,
        onClick = { onSelect(value) },
        label = { Text(label) }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VersionScreen(onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("ورژن برنامه") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "بازگشت")
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            Text("نسخه برنامه: ${BuildConfig.VERSION_NAME}")
        }
    }
}
