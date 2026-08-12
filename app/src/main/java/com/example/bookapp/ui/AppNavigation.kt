package com.example.bookapp.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Share
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.bookapp.data.AppDatabase
import com.example.bookapp.data.NoteEntity
import com.example.bookapp.data.Prefs
import com.example.bookapp.data.SearchResult
import com.example.bookapp.data.SectionEntity
import com.example.bookapp.data.seedDatabaseIfEmpty
import com.example.bookapp.data.syncRemoteContent
import com.example.bookapp.ui.screens.*
import kotlinx.coroutines.launch

private const val ROUTE_SPLASH = "splash"
private const val ROUTE_ONBOARDING = "onboarding"
private const val ROUTE_LOGIN = "login"
private const val ROUTE_MAIN_MENU = "main_menu"
private const val ROUTE_SEARCH = "search"
private const val ROUTE_BOOKMARKS = "bookmarks"
private const val ROUTE_NOTES = "notes"
private const val ROUTE_ABOUT = "about"
private const val ROUTE_SETTINGS = "settings"
private const val ROUTE_VERSION = "version"
private const val ROUTE_FIELDS = "fields"
private const val ROUTE_TAZIEHS = "taziehs/{fieldId}/{fieldTitle}"
private const val ROUTE_ROLES = "roles/{taziehId}/{taziehTitle}"
private const val ROUTE_SECTIONS = "sections/{roleId}/{roleTitle}"
private const val ROUTE_TEXT = "text/{sectionId}"
private const val ROUTE_TEXT_PAGER = "text_pager/{roleId}/{startIndex}"
private const val ROUTE_COMPARE = "compare/{roleAId}/{roleBId}"

@Composable
fun AppNavigation(
    darkMode: Boolean,
    onDarkModeChange: (Boolean) -> Unit,
    fontScale: Float,
    onFontScaleChange: (Float) -> Unit,
    themeChoice: String,
    onThemeChoiceChange: (String) -> Unit,
    fontChoice: String,
    onFontChoiceChange: (String) -> Unit,
    shortcutTarget: String?
) {
    val context = LocalContext.current
    val db = remember { AppDatabase.getInstance(context) }
    val navController: NavHostController = rememberNavController()

    LaunchedEffect(Unit) {
        seedDatabaseIfEmpty(context, db)
    }

    // مقصد بعد از ورود: اگر از میان‌بر آیکون باز شده باشد، مستقیم به آن صفحه می‌رویم
    fun postLoginRoute(): String = when (shortcutTarget) {
        "search" -> ROUTE_SEARCH
        "notes" -> ROUTE_NOTES
        "bookmarks" -> ROUTE_BOOKMARKS
        else -> ROUTE_MAIN_MENU
    }

    NavHost(navController = navController, startDestination = ROUTE_SPLASH) {

        composable(ROUTE_SPLASH) {
            SplashScreen(onFinished = {
                val next = if (Prefs.isOnboardingShown(context)) ROUTE_LOGIN else ROUTE_ONBOARDING
                navController.navigate(next) {
                    popUpTo(ROUTE_SPLASH) { inclusive = true }
                }
            })
        }

        composable(ROUTE_ONBOARDING) {
            OnboardingScreen(onFinished = {
                Prefs.setOnboardingShown(context)
                navController.navigate(ROUTE_LOGIN) {
                    popUpTo(ROUTE_ONBOARDING) { inclusive = true }
                }
            })
        }

        composable(ROUTE_LOGIN) {
            LoginScreen(onLoginSuccess = {
                navController.navigate(postLoginRoute()) {
                    popUpTo(ROUTE_LOGIN) { inclusive = true }
                }
            })
        }

        composable(ROUTE_MAIN_MENU) {
            var randomVerse by remember { mutableStateOf<SearchResult?>(null) }
            var recentItems by remember { mutableStateOf(listOf<SearchResult>()) }

            LaunchedEffect(Unit) {
                randomVerse = db.searchDao().getRandomSection()
                val ids = Prefs.getRecent(context)
                if (ids.isNotEmpty()) {
                    val fetched = db.searchDao().getByIds(ids)
                    val byId = fetched.associateBy { it.sectionId }
                    recentItems = ids.mapNotNull { byId[it] }
                }
            }

            MainMenuScreen(
                randomVerse = randomVerse,
                recentItems = recentItems,
                onOpenTaziehList = { navController.navigate(ROUTE_FIELDS) },
                onOpenSearch = { navController.navigate(ROUTE_SEARCH) },
                onOpenBookmarks = { navController.navigate(ROUTE_BOOKMARKS) },
                onOpenNotes = { navController.navigate(ROUTE_NOTES) },
                onOpenAbout = { navController.navigate(ROUTE_ABOUT) },
                onOpenSettings = { navController.navigate(ROUTE_SETTINGS) },
                onOpenVersion = { navController.navigate(ROUTE_VERSION) },
                onItemClick = { result -> navController.navigate("text/${result.sectionId}") }
            )
        }

        composable(ROUTE_SEARCH) {
            var fields by remember { mutableStateOf(listOf<com.example.bookapp.data.FieldEntity>()) }
            var allTaziehs by remember { mutableStateOf(listOf<com.example.bookapp.data.TaziehEntity>()) }
            LaunchedEffect(Unit) {
                fields = db.fieldDao().getAll()
                allTaziehs = db.taziehDao().getAll()
            }
            SearchScreen(
                fields = fields,
                allTaziehs = allTaziehs,
                onSearch = { query, fieldId, taziehId ->
                    when {
                        taziehId != null -> db.searchDao().searchInTazieh(query, taziehId)
                        fieldId != null -> db.searchDao().searchInField(query, fieldId)
                        else -> db.searchDao().search(query)
                    }
                },
                onResultClick = { result -> navController.navigate("text/${result.sectionId}") },
                onBack = { navController.popBackStack() }
            )
        }

        composable(ROUTE_BOOKMARKS) {
            var items by remember { mutableStateOf(listOf<SearchResult>()) }
            LaunchedEffect(Unit) {
                val ids = Prefs.getBookmarks(context).toList()
                items = if (ids.isEmpty()) emptyList() else db.searchDao().getByIds(ids)
            }
            BookmarksScreen(
                items = items,
                onItemClick = { result -> navController.navigate("text/${result.sectionId}") },
                onBack = { navController.popBackStack() }
            )
        }

        composable(ROUTE_NOTES) {
            var notes by remember { mutableStateOf(listOf<NoteEntity>()) }
            val scope = androidx.compose.runtime.rememberCoroutineScope()
            suspend fun reload() { notes = db.noteDao().getAll() }
            LaunchedEffect(Unit) { reload() }

            NotesScreen(
                notes = notes,
                onAddNote = { title, content ->
                    val note = NoteEntity(title = title, content = content)
                    scope.launch {
                        db.noteDao().insert(note)
                        reload()
                    }
                },
                onDeleteNote = { id ->
                    scope.launch {
                        db.noteDao().delete(id)
                        reload()
                    }
                },
                onBack = { navController.popBackStack() }
            )
        }

        composable(ROUTE_ABOUT) {
            var fieldsCount by remember { mutableIntStateOf(0) }
            var taziehsCount by remember { mutableIntStateOf(0) }
            var rolesCount by remember { mutableIntStateOf(0) }
            var sectionsCount by remember { mutableIntStateOf(0) }
            LaunchedEffect(Unit) {
                fieldsCount = db.searchDao().countFields()
                taziehsCount = db.searchDao().countTaziehs()
                rolesCount = db.searchDao().countRoles()
                sectionsCount = db.searchDao().countSections()
            }
            AboutScreen(
                fieldsCount = fieldsCount,
                taziehsCount = taziehsCount,
                rolesCount = rolesCount,
                sectionsCount = sectionsCount,
                readCount = Prefs.getReadSectionsCount(context),
                streakDays = Prefs.getStreakDays(context),
                onBack = { navController.popBackStack() }
            )
        }

        composable(ROUTE_SETTINGS) {
            SettingsScreen(
                darkMode = darkMode,
                onDarkModeChange = onDarkModeChange,
                fontScale = fontScale,
                onFontScaleChange = onFontScaleChange,
                fontChoice = fontChoice,
                onFontChoiceChange = onFontChoiceChange,
                themeChoice = themeChoice,
                onThemeChoiceChange = onThemeChoiceChange,
                onSyncContent = { syncRemoteContent(db) },
                db = db,
                onBack = { navController.popBackStack() }
            )
        }

        composable(ROUTE_VERSION) { VersionScreen(onBack = { navController.popBackStack() }) }

        composable(ROUTE_FIELDS) {
            var items by remember { mutableStateOf(listOf<ListItemData>()) }
            LaunchedEffect(Unit) {
                items = db.fieldDao().getAll().map { ListItemData(it.id, it.title) }
            }
            GenericListScreen(
                screenTitle = "زمینه‌ها",
                items = items,
                onItemClick = { navController.navigate("taziehs/${it.id}/${it.title}") },
                onBack = { navController.popBackStack() }
            )
        }

        composable(ROUTE_TAZIEHS) { backStackEntry ->
            val fieldId = backStackEntry.arguments?.getString("fieldId")?.toLongOrNull() ?: 0L
            val fieldTitle = backStackEntry.arguments?.getString("fieldTitle") ?: ""
            var items by remember { mutableStateOf(listOf<ListItemData>()) }
            LaunchedEffect(fieldId) {
                items = db.taziehDao().getByField(fieldId).map { ListItemData(it.id, it.title) }
            }
            GenericListScreen(
                screenTitle = fieldTitle,
                items = items,
                onItemClick = { navController.navigate("roles/${it.id}/${it.title}") },
                onBack = { navController.popBackStack() }
            )
        }

        composable(ROUTE_ROLES) { backStackEntry ->
            val taziehId = backStackEntry.arguments?.getString("taziehId")?.toLongOrNull() ?: 0L
            val taziehTitle = backStackEntry.arguments?.getString("taziehTitle") ?: ""
            var items by remember { mutableStateOf(listOf<ListItemData>()) }
            var compareMode by remember { mutableStateOf(false) }
            var selectedIds by remember { mutableStateOf(setOf<Long>()) }
            LaunchedEffect(taziehId) {
                items = db.roleDao().getByTazieh(taziehId).map { ListItemData(it.id, it.title) }
            }
            GenericListScreen(
                screenTitle = if (compareMode) "دو نقش را انتخاب کنید" else taziehTitle,
                items = items,
                onItemClick = { navController.navigate("sections/${it.id}/${it.title}") },
                onBack = {
                    if (compareMode) {
                        compareMode = false
                        selectedIds = emptySet()
                    } else {
                        navController.popBackStack()
                    }
                },
                selectedIds = selectedIds,
                onToggleSelect = if (compareMode) { item ->
                    selectedIds = if (item.id in selectedIds) {
                        selectedIds - item.id
                    } else if (selectedIds.size < 2) {
                        selectedIds + item.id
                    } else {
                        selectedIds
                    }
                    if (selectedIds.size == 2) {
                        val (a, b) = selectedIds.toList()
                        compareMode = false
                        navController.navigate("compare/$a/$b")
                    }
                } else null,
                topBarAction = {
                    androidx.compose.material3.TextButton(onClick = {
                        compareMode = !compareMode
                        selectedIds = emptySet()
                    }) {
                        androidx.compose.material3.Text(if (compareMode) "لغو" else "مقایسه")
                    }
                }
            )
        }

        composable(ROUTE_COMPARE) { backStackEntry ->
            val roleAId = backStackEntry.arguments?.getString("roleAId")?.toLongOrNull() ?: 0L
            val roleBId = backStackEntry.arguments?.getString("roleBId")?.toLongOrNull() ?: 0L
            var roleATitle by remember { mutableStateOf("") }
            var roleBTitle by remember { mutableStateOf("") }
            var roleASections by remember { mutableStateOf(listOf<SectionEntity>()) }
            var roleBSections by remember { mutableStateOf(listOf<SectionEntity>()) }
            LaunchedEffect(roleAId, roleBId) {
                roleASections = db.sectionDao().getByRole(roleAId)
                roleBSections = db.sectionDao().getByRole(roleBId)
                roleATitle = db.roleDao().getById(roleAId).title
                roleBTitle = db.roleDao().getById(roleBId).title
            }
            CompareScreen(
                roleATitle = roleATitle.ifBlank { "نقش اول" },
                roleASections = roleASections,
                roleBTitle = roleBTitle.ifBlank { "نقش دوم" },
                roleBSections = roleBSections,
                onBack = { navController.popBackStack() }
            )
        }

        composable(ROUTE_SECTIONS) { backStackEntry ->
            val roleId = backStackEntry.arguments?.getString("roleId")?.toLongOrNull() ?: 0L
            val roleTitle = backStackEntry.arguments?.getString("roleTitle") ?: ""
            var items by remember { mutableStateOf(listOf<ListItemData>()) }
            val scope = androidx.compose.runtime.rememberCoroutineScope()
            LaunchedEffect(roleId) {
                items = db.sectionDao().getByRole(roleId).map { ListItemData(it.id, it.title) }
            }
            GenericListScreen(
                screenTitle = roleTitle,
                items = items,
                onItemClick = { clicked ->
                    val index = items.indexOfFirst { it.id == clicked.id }.coerceAtLeast(0)
                    navController.navigate("text_pager/$roleId/$index")
                },
                onBack = { navController.popBackStack() },
                floatingAction = {
                    androidx.compose.material3.ExtendedFloatingActionButton(
                        text = { androidx.compose.material3.Text("خروجی PDF") },
                        icon = { androidx.compose.material3.Icon(Icons.Filled.Share, contentDescription = null) },
                        onClick = {
                            scope.launch {
                                val fullSections = db.sectionDao().getByRole(roleId)
                                com.example.bookapp.data.exportRoleToPdf(context, roleTitle, fullSections)
                            }
                        }
                    )
                }
            )
        }

        composable(ROUTE_TEXT_PAGER) { backStackEntry ->
            val roleId = backStackEntry.arguments?.getString("roleId")?.toLongOrNull() ?: 0L
            val startIndex = backStackEntry.arguments?.getString("startIndex")?.toIntOrNull() ?: 0
            var sections by remember { mutableStateOf(listOf<SectionEntity>()) }
            var bookmarkVersion by remember { mutableIntStateOf(0) }
            LaunchedEffect(roleId) {
                sections = db.sectionDao().getByRole(roleId)
            }
            if (sections.isNotEmpty()) {
                TextPagerScreen(
                    sections = sections,
                    startIndex = startIndex,
                    isBookmarked = { id -> bookmarkVersion.let { Prefs.isBookmarked(context, id) } },
                    onToggleBookmark = { id ->
                        Prefs.toggleBookmark(context, id)
                        bookmarkVersion++
                    },
                    onPageShown = { id ->
                        Prefs.addRecent(context, id)
                        Prefs.markSectionRead(context, id)
                    },
                    onBack = { navController.popBackStack() }
                )
            }
        }

        composable(ROUTE_TEXT) { backStackEntry ->
            val sectionId = backStackEntry.arguments?.getString("sectionId")?.toLongOrNull() ?: 0L
            var title by remember { mutableStateOf("") }
            var content by remember { mutableStateOf("") }
            var bookmarked by remember { mutableStateOf(Prefs.isBookmarked(context, sectionId)) }
            LaunchedEffect(sectionId) {
                val section = db.sectionDao().getById(sectionId)
                title = section.title
                content = section.content
                bookmarked = Prefs.isBookmarked(context, sectionId)
                Prefs.addRecent(context, sectionId)
                Prefs.markSectionRead(context, sectionId)
            }
            TextScreen(
                title = title,
                content = content,
                isBookmarked = bookmarked,
                onToggleBookmark = {
                    bookmarked = Prefs.toggleBookmark(context, sectionId)
                },
                sectionId = sectionId,
                onBack = { navController.popBackStack() }
            )
        }
    }
}
