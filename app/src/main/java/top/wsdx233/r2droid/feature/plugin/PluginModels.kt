package top.wsdx233.r2droid.feature.plugin

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class PluginIndex(
    @SerialName("schemaVersion") val schemaVersion: Int = 1,
    @SerialName("generatedAt") val generatedAt: String? = null,
    @SerialName("plugins") val plugins: List<PluginIndexEntry> = emptyList()
)

@Serializable
data class PluginIndexEntry(
    @SerialName("id") val id: String,
    @SerialName("name") val name: String,
    @SerialName("version") val version: String,
    @SerialName("description") val description: String = "",
    @SerialName("author") val author: String = "",
    @SerialName("downloadUrl") val downloadUrl: String,
    @SerialName("sha256") val sha256: String,
    @SerialName("manifestPath") val manifestPath: String = "manifest.json",
    @SerialName("min_version") val minVersion: String? = null
)

@Serializable
data class PluginManifest(
    @SerialName("id") val id: String,
    @SerialName("name") val name: String,
    @SerialName("version") val version: String,
    @SerialName("description") val description: String = "",
    @SerialName("author") val author: String = "",
    @SerialName("permissions") val permissions: List<String> = emptyList(),
    @SerialName("entry") val entry: PluginEntry? = null,
    @SerialName("icon") val icon: PluginIcon? = null,
    @SerialName("ui") val ui: PluginUiOptions = PluginUiOptions(),
    @SerialName("proot") val proot: PluginProotConfig? = null,
    @SerialName("tabs") val tabs: List<PluginScreenTab> = emptyList(),
    @SerialName("projectTabs") val projectTabs: List<PluginProjectTab> = emptyList(),
    @SerialName("navigation") val navigation: List<PluginNavigationItem> = emptyList(),
    @SerialName("appBarActions") val appBarActions: List<PluginProjectAction> = emptyList()
)

@Serializable
data class PluginEntry(
    @SerialName("script") val script: String? = null,
    @SerialName("page") val page: PluginPage? = null,
    @SerialName("terminal") val terminal: PluginTerminalEntry? = null
)

@Serializable
data class PluginTerminalEntry(
    @SerialName("command") val command: String,
    @SerialName("title") val title: String? = null
)

@Serializable
data class PluginUiOptions(
    @SerialName("showEnableToggle") val showEnableToggle: Boolean = true,
    @SerialName("showEnableStatus") val showEnableStatus: Boolean = true
)

@Serializable
data class PluginProotConfig(
    /** Enable automatic setup for this plugin. */
    @SerialName("enabled") val enabled: Boolean = false,
    /** "plugin" = dedicated plugin Ubuntu, "main" = r2 Ubuntu used in proot mode. */
    @SerialName("environment") val environment: String = "plugin",
    @SerialName("rootfsAlias") val rootfsAlias: String = "ubuntu",
    @SerialName("aptPackages") val aptPackages: List<String> = emptyList(),
    @SerialName("setupCommands") val setupCommands: List<String> = emptyList(),
    /** r2pm packages are installed into the selected proot and use that proot's r2/r2pm. */
    @SerialName("r2pmPackages") val r2pmPackages: List<String> = emptyList(),
    @SerialName("python") val python: PluginPythonEnv? = null
)

@Serializable
data class PluginPythonEnv(
    @SerialName("name") val name: String = "default",
    @SerialName("packages") val packages: List<String> = emptyList(),
    /** Optional requirements path. Host app files are bind-mounted at the same absolute path. */
    @SerialName("requirements") val requirements: String? = null
)

@Serializable
data class PluginPage(
    @SerialName("type") val type: String = "webview",
    @SerialName("path") val path: String,
    @SerialName("function") val function: String? = null
)

@Serializable
data class PluginIcon(
    /** "material" = built-in Material icon by name, "asset" = plugin-local svg/png/webp. */
    @SerialName("type") val type: String = "material",
    @SerialName("name") val name: String? = null,
    @SerialName("path") val path: String? = null,
    /** When true, host tints the icon to match Material navigation colors. */
    @SerialName("monochrome") val monochrome: Boolean = true
)

@Serializable
data class PluginNavigationItem(
    @SerialName("target") val target: String = "project",
    @SerialName("key") val key: String,
    @SerialName("title") val title: String,
    @SerialName("icon") val icon: PluginIcon? = null,
    @SerialName("order") val order: Int = 100,
    @SerialName("visibleWhen") val visibleWhen: String = "always",
    @SerialName("tabs") val tabs: List<PluginNavigationTab> = emptyList()
)

@Serializable
data class PluginNavigationTab(
    @SerialName("key") val key: String,
    @SerialName("title") val title: String,
    @SerialName("page") val page: PluginPage,
    @SerialName("visibleWhen") val visibleWhen: String = "always"
)

@Serializable
data class PluginProjectTab(
    @SerialName("key") val key: String,
    @SerialName("title") val title: String,
    @SerialName("page") val page: PluginPage,
    @SerialName("visibleWhen") val visibleWhen: String = "always"
)

@Serializable
data class PluginScreenTab(
    @SerialName("target") val target: String = "project",
    @SerialName("key") val key: String,
    @SerialName("title") val title: String,
    @SerialName("page") val page: PluginPage,
    @SerialName("visibleWhen") val visibleWhen: String = "always"
)

@Serializable
data class PluginProjectAction(
    @SerialName("key") val key: String,
    @SerialName("title") val title: String = "",
    @SerialName("icon") val icon: String = "settings",
    @SerialName("function") val function: String? = null,
    @SerialName("script") val script: String? = null,
    @SerialName("visibleTabs") val visibleTabs: List<String> = listOf("*")
)

@Serializable
data class InstalledPluginState(
    @SerialName("id") val id: String,
    @SerialName("version") val version: String,
    @SerialName("installDir") val installDir: String,
    @SerialName("enabled") val enabled: Boolean = true,
    @SerialName("sourceUrl") val sourceUrl: String,
    @SerialName("sha256") val sha256: String,
    @SerialName("manifestPath") val manifestPath: String = "manifest.json",
    @SerialName("installedAt") val installedAt: Long = System.currentTimeMillis()
)

data class InstalledPlugin(
    val state: InstalledPluginState,
    val manifest: PluginManifest?
)
data class PluginCatalogItem(
    val indexEntry: PluginIndexEntry,
    val installed: InstalledPlugin?,
    val hasUpgrade: Boolean
)

data class PluginVersionCompatibility(
    val minVersion: String?,
    val currentVersion: String,
    val compatible: Boolean
)


data class PluginProjectTabDescriptor(
    val pluginId: String,
    val pluginName: String,
    val tab: PluginProjectTab
)

data class PluginScreenTabDescriptor(
    val pluginId: String,
    val pluginName: String,
    val target: String,
    val tab: PluginScreenTab
)

data class PluginProjectActionDescriptor(
    val pluginId: String,
    val pluginName: String,
    val action: PluginProjectAction
)

data class PluginNavigationDescriptor(
    val pluginId: String,
    val pluginName: String,
    val icon: PluginIcon?,
    val navigation: PluginNavigationItem
)

@Serializable
data class PluginDeveloperConfig(
    @SerialName("enabled") val enabled: Boolean = false,
    @SerialName("workspaceDir") val workspaceDir: String? = null
)

enum class DeveloperPluginType(val key: String) {
    WEBVIEW("webview"),
    SCHEMA("schema"),
    TERMINAL("terminal"),
    NATIVE("native");

    companion object {
        fun fromKey(key: String): DeveloperPluginType {
            return entries.firstOrNull { it.key.equals(key, ignoreCase = true) } ?: WEBVIEW
        }
    }
}

data class DeveloperPluginCreateRequest(
    val type: DeveloperPluginType,
    val id: String,
    val name: String,
    val version: String,
    val description: String,
    val author: String,
    val permissions: List<String> = emptyList()
)
