package com.ao3reader.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.ao3reader.data.model.Site
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

enum class ThemeMode(val label: String) { DARK("Dark"), LIGHT("Light"), SYSTEM("Follow system") }

enum class AccentColor(val label: String, val argb: Long) {
    AO3_RED("AO3 red", 0xFFB71C1C),
    CRIMSON("Rose", 0xFFE91E63),
    PURPLE("Purple", 0xFF7E57C2),
    BLUE("Blue", 0xFF1E88E5),
    TEAL("Teal", 0xFF00897B),
    GREEN("Green", 0xFF43A047),
    AMBER("Amber", 0xFFFFA000),
}

enum class TextAlign(val label: String, val css: String) {
    LEFT("Left", "left"), JUSTIFY("Justified", "justify"), CENTER("Centered", "center"), RIGHT("Right", "right")
}

enum class ReaderFont(val label: String, val css: String) {
    SANS("Sans serif", "sans-serif"),
    SERIF("Serif", "serif"),
    MONO("Monospace", "monospace"),
    CONDENSED("Condensed", "sans-serif-condensed"),
}

enum class ReaderTheme(val label: String) { APP("Match app"), BLACK("Black"), DARK("Dark grey"), SEPIA("Sepia"), LIGHT("Light") }

data class ReaderSettings(
    val fontSize: Int = 18,
    val lineSpacing: Float = 1.6f,
    val paragraphSpacing: Float = 0.8f,
    val font: ReaderFont = ReaderFont.SERIF,
    val align: TextAlign = TextAlign.LEFT,
    val margin: Int = 16,
    val theme: ReaderTheme = ReaderTheme.APP,
    val keepScreenOn: Boolean = true,
    val showAuthorNotes: Boolean = true,
    /** Keep authors' own colors and fonts (AO3 work skins) instead of the reader's styling. Bold, italics etc. are always kept. */
    val useWorkSkins: Boolean = false,
)

enum class ListDensity(val label: String) { COMFORTABLE("Comfortable"), COMPACT("Compact") }

data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.DARK,
    val amoledBlack: Boolean = false,
    val dynamicColor: Boolean = false,
    val accent: AccentColor = AccentColor.AO3_RED,
    val density: ListDensity = ListDensity.COMFORTABLE,
    val showSummaries: Boolean = true,
    val showTags: Boolean = true,
    val maxTagsShown: Int = 12,
    val hideBlockedCompletely: Boolean = false,
    val notificationsEnabled: Boolean = true,
    val checkIntervalHours: Int = 6,
    val autoDownloadUpdates: Boolean = true,
    val wifiOnlyChecks: Boolean = false,
    val reader: ReaderSettings = ReaderSettings(),
    /** Which site Search and Categories show. */
    val currentSite: Site = Site.AO3,
    /** Library grouped under series/fandom headings instead of one flat list. */
    val groupLibrary: Boolean = true,
    /** Signed-in usernames; null when signed out ("" when signed in but the name isn't known). */
    val ao3User: String? = null,
    val ffnUser: String? = null,
    /** Push follows and likes made in the app to the site account too. */
    val syncToSites: Boolean = true,
    val lastSyncAt: Long = 0,
)

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class SettingsRepository(private val context: Context) {

    private object K {
        val themeMode = stringPreferencesKey("theme_mode")
        val amoled = booleanPreferencesKey("amoled")
        val dynamic = booleanPreferencesKey("dynamic_color")
        val accent = stringPreferencesKey("accent")
        val density = stringPreferencesKey("density")
        val showSummaries = booleanPreferencesKey("show_summaries")
        val showTags = booleanPreferencesKey("show_tags")
        val maxTags = intPreferencesKey("max_tags")
        val hideBlocked = booleanPreferencesKey("hide_blocked")
        val notifications = booleanPreferencesKey("notifications")
        val interval = intPreferencesKey("check_interval")
        val autoDownload = booleanPreferencesKey("auto_download")
        val wifiOnly = booleanPreferencesKey("wifi_only")
        val fontSize = intPreferencesKey("r_font_size")
        val lineSpacing = floatPreferencesKey("r_line_spacing")
        val paragraphSpacing = floatPreferencesKey("r_paragraph_spacing")
        val font = stringPreferencesKey("r_font")
        val align = stringPreferencesKey("r_align")
        val margin = intPreferencesKey("r_margin")
        val readerTheme = stringPreferencesKey("r_theme")
        val keepOn = booleanPreferencesKey("r_keep_on")
        val notes = booleanPreferencesKey("r_notes")
        val skins = booleanPreferencesKey("r_skins")
        val site = stringPreferencesKey("current_site")
        val group = booleanPreferencesKey("group_library")
        val ao3User = stringPreferencesKey("ao3_user")
        val ffnUser = stringPreferencesKey("ffn_user")
        val syncToSites = booleanPreferencesKey("sync_to_sites")
        val lastSync = longPreferencesKey("last_sync")
    }

    private inline fun <reified E : Enum<E>> Preferences.enumOf(key: Preferences.Key<String>, default: E): E =
        this[key]?.let { v -> enumValues<E>().firstOrNull { it.name == v } } ?: default

    val settings: Flow<AppSettings> = context.dataStore.data.map { settingsFrom(it) }

    suspend fun update(transform: (AppSettings) -> AppSettings) {
        context.dataStore.edit { p ->
            val current = settingsFrom(p)
            write(p, transform(current))
        }
    }

    suspend fun updateReader(transform: (ReaderSettings) -> ReaderSettings) =
        update { it.copy(reader = transform(it.reader)) }

    private fun settingsFrom(p: Preferences): AppSettings {
        val d = AppSettings()
        val r = ReaderSettings()
        return AppSettings(
            themeMode = p.enumOf(K.themeMode, d.themeMode),
            amoledBlack = p[K.amoled] ?: d.amoledBlack,
            dynamicColor = p[K.dynamic] ?: d.dynamicColor,
            accent = p.enumOf(K.accent, d.accent),
            density = p.enumOf(K.density, d.density),
            showSummaries = p[K.showSummaries] ?: d.showSummaries,
            showTags = p[K.showTags] ?: d.showTags,
            maxTagsShown = p[K.maxTags] ?: d.maxTagsShown,
            hideBlockedCompletely = p[K.hideBlocked] ?: d.hideBlockedCompletely,
            notificationsEnabled = p[K.notifications] ?: d.notificationsEnabled,
            checkIntervalHours = p[K.interval] ?: d.checkIntervalHours,
            autoDownloadUpdates = p[K.autoDownload] ?: d.autoDownloadUpdates,
            wifiOnlyChecks = p[K.wifiOnly] ?: d.wifiOnlyChecks,
            reader = ReaderSettings(
                fontSize = p[K.fontSize] ?: r.fontSize,
                lineSpacing = p[K.lineSpacing] ?: r.lineSpacing,
                paragraphSpacing = p[K.paragraphSpacing] ?: r.paragraphSpacing,
                font = p.enumOf(K.font, r.font),
                align = p.enumOf(K.align, r.align),
                margin = p[K.margin] ?: r.margin,
                theme = p.enumOf(K.readerTheme, r.theme),
                keepScreenOn = p[K.keepOn] ?: r.keepScreenOn,
                showAuthorNotes = p[K.notes] ?: r.showAuthorNotes,
                useWorkSkins = p[K.skins] ?: r.useWorkSkins,
            ),
            currentSite = p.enumOf(K.site, d.currentSite),
            groupLibrary = p[K.group] ?: d.groupLibrary,
            ao3User = p[K.ao3User],
            ffnUser = p[K.ffnUser],
            syncToSites = p[K.syncToSites] ?: d.syncToSites,
            lastSyncAt = p[K.lastSync] ?: d.lastSyncAt,
        )
    }

    private fun write(p: MutablePreferences, s: AppSettings) {
        p[K.themeMode] = s.themeMode.name
        p[K.amoled] = s.amoledBlack
        p[K.dynamic] = s.dynamicColor
        p[K.accent] = s.accent.name
        p[K.density] = s.density.name
        p[K.showSummaries] = s.showSummaries
        p[K.showTags] = s.showTags
        p[K.maxTags] = s.maxTagsShown
        p[K.hideBlocked] = s.hideBlockedCompletely
        p[K.notifications] = s.notificationsEnabled
        p[K.interval] = s.checkIntervalHours
        p[K.autoDownload] = s.autoDownloadUpdates
        p[K.wifiOnly] = s.wifiOnlyChecks
        val r = s.reader
        p[K.fontSize] = r.fontSize
        p[K.lineSpacing] = r.lineSpacing
        p[K.paragraphSpacing] = r.paragraphSpacing
        p[K.font] = r.font.name
        p[K.align] = r.align.name
        p[K.margin] = r.margin
        p[K.readerTheme] = r.theme.name
        p[K.keepOn] = r.keepScreenOn
        p[K.notes] = r.showAuthorNotes
        p[K.skins] = r.useWorkSkins
        p[K.site] = s.currentSite.name
        p[K.group] = s.groupLibrary
        if (s.ao3User != null) p[K.ao3User] = s.ao3User else p.remove(K.ao3User)
        if (s.ffnUser != null) p[K.ffnUser] = s.ffnUser else p.remove(K.ffnUser)
        p[K.syncToSites] = s.syncToSites
        p[K.lastSync] = s.lastSyncAt
    }
}
