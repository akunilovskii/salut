param([string]$ToolchainRoot)

$ErrorActionPreference = 'Stop'
$ProjectRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
$LogRoot = Join-Path $ProjectRoot 'build/maintenance'
New-Item -ItemType Directory -Force -Path $LogRoot | Out-Null
$Groups = @(
    @{
        Name = 'common'; Task = ':common:testDebugUnitTest'
        Suites = @(
            'com.liskovsoft.smartyoutubetv2.common.app.models.playback.controllers.VideoStateControllerTempFormatTest',
            'com.liskovsoft.smartyoutubetv2.common.misc.DiagnosticRedactorTest',
            'com.liskovsoft.smartyoutubetv2.common.app.models.playback.controllers.VideoStateControllerPreviousTest',
            'com.liskovsoft.smartyoutubetv2.common.app.models.playback.controllers.VideoStateControllerPositionTest',
            'com.liskovsoft.smartyoutubetv2.common.app.models.playback.controllers.SubscriptionsQueueTest',
            'com.liskovsoft.smartyoutubetv2.common.app.models.playback.QueuePlaybackModeTest',
            'com.liskovsoft.smartyoutubetv2.common.app.models.playback.controllers.SuggestionsControllerReopenTest',
            'com.liskovsoft.smartyoutubetv2.common.app.models.playback.controllers.SuggestionsAvailabilityTest',
            'com.liskovsoft.smartyoutubetv2.common.app.models.playback.controllers.ContextualSuggestionsTest',
            'com.liskovsoft.smartyoutubetv2.common.app.models.playback.controllers.ChannelProfileParserTest',
            'com.liskovsoft.smartyoutubetv2.common.app.models.playback.controllers.ChannelStateStoreTest',
            'com.liskovsoft.smartyoutubetv2.common.app.models.playback.controllers.OriginalSubtitleSelectorTest',
            'com.liskovsoft.smartyoutubetv2.common.app.models.playback.controllers.ChannelPinTest',
            'com.liskovsoft.smartyoutubetv2.common.prefs.SharedAppSettingsTest',
            'com.liskovsoft.smartyoutubetv2.common.app.presenters.service.SidebarServiceWatchLaterTest',
            'com.liskovsoft.smartyoutubetv2.common.app.presenters.service.SidebarServiceNotificationsTest',
            'com.liskovsoft.smartyoutubetv2.common.app.presenters.dialogs.menu.PlaylistRemovalTest',
            'com.liskovsoft.smartyoutubetv2.common.app.presenters.dialogs.menu.OwnPlaylistMatchTest'
        )
        Results = 'common/build/test-results/testDebugUnitTest'
    },
    @{
        Name = 'notification-provider'; Task = ':youtubeapi:testDebugUnitTest'
        Suites = @('com.liskovsoft.youtubeapi.notifications.PhoneNotificationPreferenceTest', 'com.liskovsoft.youtubeapi.next.v1.models.VideoOwnerHandleTest')
        Results = 'MediaServiceCore/youtubeapi/build/test-results/testDebugUnitTest'
    },
    @{
        Name = 'phone'; Task = ':smarttubetv:testStmobileDebugUnitTest'
        Suites = @(
            'com.newtube.mobile.feedback.FeedbackDraftTest',
            'com.newtube.mobile.feedback.FeedbackFormTest',
            'com.newtube.mobile.notifications.NotificationSeenStoreTest',
            'com.newtube.mobile.downloads.CoverDownloadTest',
            'com.newtube.mobile.audio.SwitchAudioTest',
            'com.newtube.mobile.player.Media3TrackAdapterVideoTest',
            'com.newtube.mobile.player.Media3TrackAdapterAudioTest',
            'com.newtube.mobile.player.VideoRungTest',
            'com.newtube.mobile.player.ViewportCappedTrackSelectionTest',
            'com.newtube.mobile.player.OpusPrerollRenderersFactoryTest',
            'com.newtube.mobile.watchlater.WatchLaterAdditionTest',
            'com.newtube.mobile.watchlater.PlaylistOrderStoreTest',
            'com.newtube.mobile.ui.browse.WatchLaterDragAdapterTest',
            'com.newtube.mobile.ui.common.YouTubeLinkSetupTest',
            'com.newtube.mobile.ui.common.ChannelAvatarTest',
            'com.newtube.mobile.ui.common.MobileSnackbarTest',
            'com.newtube.mobile.ui.common.MobileThemeTest',
            'com.newtube.mobile.ui.browse.VideoCardActionAppearanceTest',
            'com.newtube.mobile.ui.browse.FeedCardLayoutTest',
            'com.newtube.mobile.ui.browse.BrowseNavigationTest',
            'com.newtube.mobile.ui.browse.BottomTabMenuTest',
            'com.newtube.mobile.accounts.AccountCycleTest',
            'com.newtube.mobile.subscriptions.SubscriptionUpdatesTest',
            'com.newtube.mobile.subscriptions.SubscriptionSeenStoreTest',
            'com.newtube.mobile.ui.browse.AvatarDrawableTest',
            'com.newtube.mobile.ui.browse.SubscriptionsDotTest',
            'com.newtube.mobile.ui.browse.BrowseToolbarLayoutTest',
            'com.newtube.mobile.ui.search.MobileSearchSubmitTest',
            'com.newtube.mobile.ui.search.SearchToolbarTest',
            'com.newtube.mobile.ui.settings.GeneralSettingsTest',
            'com.newtube.mobile.ui.settings.NavigationTabsSettingsTest',
            'com.newtube.mobile.ui.playback.MiniPlayerPositionTest',
            'com.newtube.mobile.ui.playback.MiniPlayerFlingTest',
            'com.newtube.mobile.ui.playback.PipControlsTest',
            'com.newtube.mobile.ui.playback.MiniPlayerSizeTest',
            'com.newtube.mobile.ui.playback.PlayerTransportAppearanceTest',
            'com.newtube.mobile.ui.playback.PlayerSpeedButtonTest',
            'com.newtube.mobile.ui.playback.PlayerTapPauseTest',
            'com.newtube.mobile.ui.dialog.PhoneVideoMenuTest',
            'com.newtube.mobile.ui.dialog.AiVideoShareTest',
            'com.newtube.mobile.ui.dialog.AiShareTargetStoreTest',
            'com.newtube.mobile.ui.channel.ChannelHeaderLayoutTest',
            'com.newtube.mobile.ui.channel.ChannelTitleFadeTest',
            'com.newtube.mobile.ui.channel.ChannelTabShortcutsTest',
            'com.newtube.mobile.ui.channel.ChannelDetailsPageTest',
            'com.newtube.mobile.ui.playback.SwipeLevelsTest',
            'com.newtube.mobile.PhoneSpeedPrefsTest',
            'com.newtube.mobile.ui.playback.VisibleNextQueueTest',
            'com.newtube.mobile.ui.playback.WatchHeaderTest',
            'com.newtube.mobile.ui.playback.PlaylistWatchScrollViewTest',
            'com.newtube.mobile.ui.playback.PlaylistPlaybackHeaderTest',
            'com.newtube.mobile.ui.playback.WatchActionsLayoutTest',
            'com.newtube.mobile.ui.playback.WatchMetadataLineTest',
            'com.newtube.mobile.ui.playback.WatchSubscriptionControlTest',
            'com.newtube.mobile.ui.playback.MiniPlayerControlsTest',
            'com.newtube.mobile.ui.playback.MiniPlayerCloseTest',
            'com.newtube.mobile.ui.playback.PipDismissalTest',
            'com.newtube.mobile.update.AutoUpdateCheckTest',
            'com.newtube.mobile.ui.playback.MiniCardDragTest',
            'com.newtube.mobile.ui.playback.MinimizeBackdropTest',
            'com.newtube.mobile.ui.playback.PlayingReturnTest'
        )
        Results = 'smarttubetv/build/test-results/testStmobileDebugUnitTest'
    }
)
foreach ($Group in $Groups) {
    $TestArguments = @()
    foreach ($Suite in $Group.Suites) { $TestArguments += @('--tests', $Suite) }
    $LogPath = Join-Path $LogRoot ("tests-" + $Group.Name + '.log')
    try {
        & (Join-Path $PSScriptRoot 'Build.ps1') -Tasks @($Group.Task) -GradleArguments $TestArguments -ToolchainRoot $ToolchainRoot *> $LogPath
    } catch {
        Get-Content -LiteralPath $LogPath -Tail 45
        throw
    }
    $Total = 0
    $Results = @{}
    Get-ChildItem -LiteralPath (Join-Path $ProjectRoot $Group.Results) -Filter 'TEST-*.xml' | ForEach-Object {
        [xml]$Report = Get-Content -LiteralPath $_.FullName
        $Results[$Report.testsuite.GetAttribute('name')] = $Report
    }
    foreach ($Suite in $Group.Suites) {
        $Result = $Results[$Suite]
        if (-not $Result) { throw "Required test report is missing: $Suite" }
        if ([int]$Result.testsuite.failures -ne 0 -or [int]$Result.testsuite.errors -ne 0 -or [int]$Result.testsuite.skipped -ne 0) {
            throw "Suite failed or was skipped: $Suite"
        }
        $Total += [int]$Result.testsuite.tests
    }
    Write-Output ($Group.Name + ': ' + $Total + ' tests passed. Log: ' + $LogPath)
}
