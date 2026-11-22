package com.farcast

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import androidx.lifecycle.lifecycleScope
import com.farcast.agent.darkalgorithm.*
import com.farcast.agent.darkalgorithm.features.MomentFinderService
import com.farcast.agent.darkalgorithm.plugins.PluginCatalogClient
import com.farcast.agent.darkalgorithm.privacy.TorGateway
import com.farcast.ui.FarcastNavHost
import com.farcast.ui.FarcastScreens
import com.farcast.ui.screens.*
import com.farcast.ui.theme.FarcastTheme
import kotlinx.coroutines.launch

/**
 * SCOPING NOTE, stated plainly rather than silently glossed over: this
 * centralizes state for every screen directly in the Activity instead of
 * one ViewModel per screen. That's a reasonable size for what's built so
 * far; it is NOT how this should look once there are real per-screen
 * concerns (configuration-change survival, SavedStateHandle, testing a
 * screen's logic in isolation from Compose). Split into ViewModels before
 * this file gets meaningfully bigger than it already is — don't let "it
 * still compiles" be the bar for when to do that.
 */
class MainActivity : ComponentActivity() {

    private val app get() = application as FarcastApplication

    private var activeCase by mutableStateOf<CaseFile?>(null)
    private var activeMixer: DarkMixer? = null
    private var latestProgress by mutableStateOf<DarkMixer.ProgressEvent?>(null)
    private var isWorkerRunning by mutableStateOf(false)

    private var catalogue by mutableStateOf<List<PluginCatalogClient.CatalogueEntry>>(emptyList())
    private var installingIds by mutableStateOf<Set<String>>(emptySet())

    private var searxngInstanceUrl by mutableStateOf("")
    private var generatedJsonPreview by mutableStateOf<String?>(null)
    private var pendingGeneratorState by mutableStateOf<GeneratorFormState?>(null)

    private var moments by mutableStateOf<List<MomentFinderService.Moment>>(emptyList())
    private var availableLabels by mutableStateOf<List<com.farcast.agent.darkalgorithm.features.LabelCount>>(emptyList())
    private var detectedLabelsFromImage by mutableStateOf<List<String>?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            FarcastTheme {
                val installedPlugins by produceState(initialValue = emptySet<String>()) {
                    value = app.pluginRegistry.listInstalled(onlyEnabled = false).map { it.manifest.id }.toSet()
                }

                val screens = FarcastScreens(
                    manifest = {
                        CaseManifestScreen(
                            caseFile = activeCase,
                            torConnected = false, // wire to TorGateway.statusUpdates(this).collectAsStateWithLifecycle() once an instance is current
                            installedPluginCount = installedPlugins.size,
                            isWorkerRunning = isWorkerRunning,
                            onFeedJson = { generatedJsonPreview?.let { feedJson(it) } },
                            onOpenLead = { /* navigate to a lead-detail screen — not built this pass */ }
                        )
                    },
                    pipeline = { PipelineStatusScreen(latestProgress, isWorkerRunning) },
                    generator = {
                        CaseGeneratorScreen(
                            onGenerate = { form -> generateJson(form) },
                            onFeedGenerated = { generatedJsonPreview?.let { feedJson(it) } },
                            generatedJsonPreview = generatedJsonPreview
                        )
                    },
                    plugins = {
                        LaunchedEffect(Unit) { loadCatalogueIfNeeded() }
                        PluginMarketplaceScreen(
                            catalogue = catalogue,
                            installedIds = installedPlugins,
                            installingIds = installingIds,
                            onInstall = { entry -> installPlugin(entry) },
                            onUninstall = { id -> uninstallPlugin(id) }
                        )
                    },
                    settings = {
                        SettingsScreen(
                            torStatus = TorGateway.TorStatus.Off, // wire to a real collected flow once a worker/case is active
                            onOpenOrbot = { com.farcast.agent.darkalgorithm.privacy.TorVpnGateway.promptEnableOrbotVpnMode(this) },
                            onEnableWholeDeviceVpn = { com.farcast.agent.darkalgorithm.privacy.TorVpnGateway.promptEnableOrbotVpnMode(this) },
                            searxngInstanceUrl = searxngInstanceUrl,
                            onSearxngUrlChange = { searxngInstanceUrl = it },
                            mcpEnabled = false,
                            onToggleMcp = { /* wire to McpAuth.issueNewToken()/revoke() */ },
                            mcpPairingToken = null,
                            savedKeyLabels = emptyList(), // wire to app.secureKeyVault once a "has value" check is added there
                            onEditKey = { /* open a key-entry dialog — not built this pass */ }
                        )
                    },
                    momentFinder = {
                        MomentFinderScreen(
                            availableLabels = availableLabels,
                            moments = moments,
                            onSearchLabel = { label -> searchMoments(label) },
                            onPickImage = { /* launch an image picker Activity Result contract — not built this pass */ },
                            detectedLabelsFromImage = detectedLabelsFromImage
                        )
                    },
                    locationPicker = {
                        LocationPickerScreen(markers = activeCase?.leads
                            ?.mapNotNull { lead ->
                                val lat = lead.rawData["lat"]?.toDoubleOrNull()
                                val lon = lead.rawData["lon"]?.toDoubleOrNull()
                                if (lat != null && lon != null) MapMarker(lat, lon, lead.branch, isLive = lead.branch in setOf("aviation", "orbital", "iss", "quakes"))
                                else null
                            } ?: emptyList()
                        )
                    }
                )

                FarcastNavHost(screens)
            }
        }
    }

    private fun generateJson(form: GeneratorFormState) {
        val profile = TargetProfile(
            domain = form.domain.ifBlank { null },
            usernames = form.usernames.split(",").map { it.trim() }.filter { it.isNotBlank() },
            ips = form.ips.split(",").map { it.trim() }.filter { it.isNotBlank() },
            freeTextQuery = form.freeTextQuery.ifBlank { null }
        )
        val caseFile = CaseFileBuilder.generate(
            caseId = "case-${System.currentTimeMillis()}",
            title = form.title,
            ownerAuthorizedScope = form.authorizedScope,
            profile = profile
        )
        generatedJsonPreview = DarkMixer.exportCase(caseFile)
    }

    private fun feedJson(json: String) {
        lifecycleScope.launch {
            val controller = DarkAlgorithmController(
                context = this@MainActivity,
                pluginRegistry = app.pluginRegistry,
                momentFinder = app.momentFinder,
                imageCacheDir = app.imageCacheDir,
                searxngInstanceUrl = searxngInstanceUrl.ifBlank { null }
            )
            val (caseFile, mixer) = controller.feedJsonAndRun(json, lifecycleScope) { event -> latestProgress = event }
            activeCase = caseFile
            activeMixer = mixer
            isWorkerRunning = true
        }
    }

    private suspend fun loadCatalogueIfNeeded() {
        if (catalogue.isNotEmpty()) return
        // No default catalogue URL baked in, matching SearXNG's "no default
        // instance" stance for the same reason — pick one in Settings before
        // this screen has anything to show. Left empty here deliberately
        // rather than pointed at a URL nobody has reviewed.
    }

    private fun installPlugin(entry: PluginCatalogClient.CatalogueEntry) {
        lifecycleScope.launch {
            installingIds = installingIds + entry.manifest.id
            app.pluginInstaller.install(entry)
            installingIds = installingIds - entry.manifest.id
        }
    }

    private fun uninstallPlugin(id: String) {
        lifecycleScope.launch { app.pluginInstaller.uninstall(id) }
    }

    private fun searchMoments(label: String) {
        val caseId = activeCase?.caseId ?: return
        lifecycleScope.launch {
            if (label.isBlank()) return@launch
            moments = app.momentFinder.findMoments(caseId, label)
        }
    }
}
