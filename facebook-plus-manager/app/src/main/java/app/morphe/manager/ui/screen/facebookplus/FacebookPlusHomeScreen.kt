/*
 * Copyright 2026 8BiT Softworks.
 * Derived from Morphe Manager.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen.facebookplus

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.morphe.manager.R
import app.morphe.manager.data.room.apps.installed.trackingKey
import app.morphe.manager.domain.facebookplus.FacebookPlusPin
import app.morphe.manager.ui.model.HomeAppItem
import app.morphe.manager.ui.model.navigation.Patcher
import app.morphe.manager.ui.screen.home.Android11Dialog
import app.morphe.manager.ui.screen.home.HomeDialogs
import app.morphe.manager.ui.screen.home.ManagerChangelogDialog
import app.morphe.manager.ui.screen.settings.SettingsNavigation
import app.morphe.manager.ui.screen.settings.system.InstallerFlowDialogs
import app.morphe.manager.ui.screen.shared.AppDialogButton
import app.morphe.manager.ui.screen.shared.AppDialogOutlinedButton
import app.morphe.manager.ui.screen.shared.Defaults
import app.morphe.manager.ui.screen.shared.DialogHost
import app.morphe.manager.ui.screen.shared.PageHeader
import app.morphe.manager.ui.screen.shared.PinnedHeaderPage
import app.morphe.manager.ui.screen.shared.TitleAction
import app.morphe.manager.ui.screen.shared.TitleActionStyle
import app.morphe.manager.ui.screen.shared.UserMessages
import app.morphe.manager.ui.viewmodel.HomeViewModel
import app.morphe.manager.ui.viewmodel.InstallViewModel
import app.morphe.manager.util.APK_FILE_MIME_TYPES
import app.morphe.manager.util.MPP_FILE_MIME_TYPES
import app.morphe.manager.util.PM
import app.morphe.manager.util.RequestInstallAppsContract
import app.morphe.manager.util.rememberAdaptiveFilePicker
import app.morphe.manager.util.rememberAdaptiveMultiFilePicker
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.rounded.Settings
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject

@Composable
fun FacebookPlusHomeScreen(
    settingsNavigation: SettingsNavigation,
    onOpenSettings: () -> Unit,
    onStartQuickPatch: (Patcher.ViewModelParams) -> Unit,
    homeViewModel: HomeViewModel,
    patchTriggerPackage: String? = null,
    onPatchTriggerHandled: () -> Unit = {},
    installViewModel: InstallViewModel = koinViewModel(),
    pm: PM = koinInject(),
    messages: UserMessages = koinInject()
) {
    val context = LocalContext.current
    val availablePatches by homeViewModel.availablePatches.collectAsStateWithLifecycle(0)
    val homeAppState by homeViewModel.apps.homeAppState.collectAsStateWithLifecycle()
    val recommendedVersions by homeViewModel.recommendedVersionsFlow.collectAsStateWithLifecycle()
    val homeAppItems = homeAppState?.visible.orEmpty() + homeAppState?.hidden.orEmpty()
    val facebookItem = remember(homeAppItems) { homeAppItems.facebookPlusItem() }
    val catalogVersion = recommendedVersions[FacebookPlusPin.packageName]?.version
    val pinReady = !catalogVersion.isNullOrBlank()
    val facebookPlusInstalled = facebookItem.isFacebookPlusInstalled()
    val needsUpdate = facebookPlusInstalled &&
            catalogVersion != null &&
            facebookItem?.version?.isNotBlank() == true &&
            catalogVersion != facebookItem.version
    val hasManagerUpdate = !homeViewModel.updatedManagerVersion.isNullOrEmpty()
    val showUpdateDetailsDialog = remember { mutableStateOf(false) }
    val patchesSheetItem = remember { mutableStateOf<HomeAppItem?>(null) }

    LaunchedEffect(Unit) {
        homeViewModel.patchFlow.onStartQuickPatch = onStartQuickPatch
    }

    LaunchedEffect(patchTriggerPackage) {
        patchTriggerPackage?.let { packageName ->
            homeViewModel.patchFlow.showPatchDialog(packageName)
            onPatchTriggerHandled()
        }
    }

    val openApkPicker = rememberAdaptiveFilePicker(
        mimeTypes = APK_FILE_MIME_TYPES,
        onResult = homeViewModel.patchFlow::handleApkSelection
    )
    val openBundlePicker = rememberAdaptiveMultiFilePicker(
        mimeTypes = MPP_FILE_MIME_TYPES,
        onResult = homeViewModel::pickBundles
    )
    val installAppsPermissionLauncher = rememberLauncherForActivityResult(
        contract = RequestInstallAppsContract
    ) { homeViewModel.showAndroid11Dialog = false }

    DialogHost(visible = showUpdateDetailsDialog.value) {
        ManagerChangelogDialog(
            onDismiss = { showUpdateDetailsDialog.value = false },
            expectsUpdate = true
        )
    }
    DialogHost(visible = homeViewModel.showAndroid11Dialog) {
        Android11Dialog(
            onDismissRequest = { homeViewModel.showAndroid11Dialog = false },
            onContinue = { installAppsPermissionLauncher.launch(context.packageName) }
        )
    }
    HomeDialogs(
        homeViewModel = homeViewModel,
        storagePickerLauncher = { openApkPicker() },
        openBundlePicker = { openBundlePicker() },
        patchesItem = patchesSheetItem,
        onSourceAdded = {},
        settingsNavigation = settingsNavigation
    )
    InstallerFlowDialogs(installViewModel = installViewModel)

    val startFacebookPlus: () -> Unit = {
        when {
            !pinReady || availablePatches <= 0 ->
                messages.show(R.string.home_facebook_plus_bundle_loading)
            homeViewModel.android11BugActive ->
                homeViewModel.showAndroid11Dialog = true
            else -> homeViewModel.patchFlow.showPatchDialog(
                FacebookPlusPin.packageName,
                facebookItem?.installedApp?.trackingKey
            )
        }
    }

    val scrollState = rememberScrollState()
    PinnedHeaderPage(
        header = {
            PageHeader(
                title = stringResource(R.string.app_name),
                actions = {
                    TitleAction(
                        icon = MaterialSymbols.Rounded.Settings,
                        contentDescription = stringResource(R.string.settings),
                        onClick = onOpenSettings,
                        style = TitleActionStyle.Neutral,
                        marked = hasManagerUpdate
                    )
                }
            )
        },
        scrollState = scrollState
    ) { insets ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    start = insets.horizontal,
                    top = insets.top + Defaults.SpaceExtraLarge,
                    end = insets.horizontal,
                    bottom = insets.bottom + Defaults.SpaceLarge
                ),
            verticalArrangement = Arrangement.spacedBy(Defaults.SpaceLarge, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = Defaults.ContentMaxWidth)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(Defaults.SpaceMedium)
            ) {
                if (!pinReady) {
                    AppDialogButton(
                        text = stringResource(R.string.home_facebook_plus_make),
                        onClick = {},
                        enabled = false,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        text = stringResource(R.string.home_facebook_plus_bundle_loading),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else if (!facebookPlusInstalled) {
                    AppDialogButton(
                        text = stringResource(R.string.home_facebook_plus_make),
                        onClick = startFacebookPlus,
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    AppDialogButton(
                        text = stringResource(R.string.home_facebook_plus_open),
                        onClick = { pm.launch(FacebookPlusPin.packageName) },
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (needsUpdate) {
                        AppDialogOutlinedButton(
                            text = stringResource(R.string.home_facebook_plus_update),
                            onClick = startFacebookPlus,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
    }
}

private fun List<HomeAppItem>.facebookPlusItem(): HomeAppItem? =
    firstOrNull { it.packageName == FacebookPlusPin.packageName && !it.isClone }

private fun HomeAppItem?.isFacebookPlusInstalled(): Boolean =
    this != null &&
            installedApp != null &&
            isInstalledOnDevice &&
            !isDeleted &&
            !isInstallStateNotPatched
