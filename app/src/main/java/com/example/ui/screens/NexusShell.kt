package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.data.model.ServerChannel
import com.example.data.model.ServerMember
import com.example.ui.audio.NexusSfx
import com.example.ui.audio.rememberNexusSoundPlayer
import com.example.ui.viewmodel.NexusSection
import com.example.ui.viewmodel.NexusUiState
import com.example.ui.viewmodel.NexusViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NexusShell(
    viewModel: NexusViewModel,
    onSignOut: () -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val isSound by viewModel.isSoundEnabled.collectAsStateWithLifecycle()
    val isHaptics by viewModel.isHapticsEnabled.collectAsStateWithLifecycle()
    val sfx = rememberNexusSoundPlayer(soundEnabled = isSound, hapticsEnabled = isHaptics)

    val snackbarHostState = remember { SnackbarHostState() }
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    var showCreateServerDialog by remember { mutableStateOf(false) }
    var showJoinInviteDialog by remember { mutableStateOf(false) }
    var editingChannelTarget by remember { mutableStateOf<Pair<Boolean, ServerChannel?>>(false to null) }
    var showServerSettingsDialog by remember { mutableStateOf(false) }
    var inspectedMember by remember { mutableStateOf<ServerMember?>(null) }
    var showMembersDrawer by remember { mutableStateOf(true) }
    var showMobileMembersSheet by remember { mutableStateOf(false) }

    LaunchedEffect(state.snackbarMessage, state.errorBanner) {
        val msg = state.snackbarMessage ?: state.errorBanner
        if (msg != null) {
            snackbarHostState.showSnackbar(msg)
            viewModel.clearSnackbar()
        }
    }

    // Sub-section BackHandler on mobile/tablet so Back returns to Home DMs
    BackHandler(enabled = state.activeSection != NexusSection.HOME_DMS && state.selectedDmThreadId == null) {
        viewModel.openHomeDms(null)
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val isWidePc = maxWidth >= 840.dp
        val isTabletMedium = maxWidth in 600.dp..839.dp
        val isCompactMobile = maxWidth < 600.dp

        val sidebarContent: @Composable () -> Unit = {
            Row(modifier = Modifier.fillMaxHeight()) {
                ServerRail(
                    state = state,
                    onHomeClick = {
                        sfx.play(NexusSfx.CLICK)
                        viewModel.openHomeDms(null)
                        if (isCompactMobile) scope.launch { drawerState.close() }
                    },
                    onServerClick = { srvId ->
                        sfx.play(NexusSfx.CLICK)
                        viewModel.selectServer(srvId)
                    },
                    onCreateServerClick = {
                        sfx.play(NexusSfx.CLICK)
                        showCreateServerDialog = true
                    },
                    onJoinInviteClick = {
                        sfx.play(NexusSfx.CLICK)
                        showJoinInviteDialog = true
                    },
                    onDiscoveryClick = {
                        sfx.play(NexusSfx.CLICK)
                        viewModel.selectSection(NexusSection.DISCOVERY)
                        if (isCompactMobile) scope.launch { drawerState.close() }
                    },
                    onNotificationsClick = {
                        sfx.play(NexusSfx.CLICK)
                        viewModel.selectSection(NexusSection.NOTIFICATIONS)
                        if (isCompactMobile) scope.launch { drawerState.close() }
                    },
                    onProfileClick = {
                        sfx.play(NexusSfx.CLICK)
                        viewModel.selectSection(NexusSection.PROFILE_SETTINGS)
                        if (isCompactMobile) scope.launch { drawerState.close() }
                    }
                )

                ChannelSidebar(
                    state = state,
                    server = state.activeServer,
                    channels = state.activeServerChannels,
                    voiceMembers = state.activeVoiceRoomMembers,
                    activeVoiceSession = state.activeVoiceSession,
                    onSelectChannel = { ch ->
                        sfx.play(NexusSfx.CLICK)
                        viewModel.selectChannel(ch.id)
                        if (isCompactMobile) scope.launch { drawerState.close() }
                    },
                    onCreateChannelClick = { editingChannelTarget = true to null },
                    onEditChannelClick = { ch -> editingChannelTarget = true to ch },
                    onOpenServerSettings = { showServerSettingsDialog = true },
                    onLeaveServer = { state.activeServer?.let(viewModel::leaveServer) },
                    onToggleMute = { viewModel.toggleMute(viewModel.voiceEngine.hasMicPermission()) },
                    onDisconnectVoice = viewModel::leaveVoiceRoom,
                    onOpenUserSettings = {
                        viewModel.selectSection(NexusSection.PROFILE_SETTINGS)
                        if (isCompactMobile) scope.launch { drawerState.close() }
                    },
                    onShowSnack = viewModel::showSnack,
                    modifier = Modifier.width(if (isCompactMobile) 256.dp else 246.dp)
                )
            }
        }

        val mainStage: @Composable (Modifier) -> Unit = { mod ->
            AnimatedContent(targetState = state.activeSection, label = "nexus_main_stage", modifier = mod) { section ->
                when (section) {
                    NexusSection.HOME_DMS -> FriendsAndDmScreen(
                        state = state,
                        viewModel = viewModel,
                        showDrawerButton = isCompactMobile,
                        onOpenNavDrawer = { scope.launch { drawerState.open() } }
                    )

                    NexusSection.SERVER -> ChannelWorkspaceScreen(
                        state = state,
                        viewModel = viewModel,
                        showDrawerButton = isCompactMobile,
                        showMembersInline = isWidePc && showMembersDrawer,
                        onOpenNavDrawer = { scope.launch { drawerState.open() } },
                        onToggleMembersSheet = {
                            if (isWidePc) showMembersDrawer = !showMembersDrawer
                            else showMobileMembersSheet = true
                        },
                        onInspectMember = { inspectedMember = it }
                    )

                    NexusSection.DISCOVERY -> ServerDiscoveryScreen(
                        state = state,
                        viewModel = viewModel,
                        showDrawerButton = isCompactMobile,
                        onOpenNavDrawer = { scope.launch { drawerState.open() } },
                        onCreateServerClick = { showCreateServerDialog = true },
                        onJoinInviteClick = { showJoinInviteDialog = true }
                    )

                    NexusSection.NOTIFICATIONS -> NotificationsScreen(
                        state = state,
                        viewModel = viewModel,
                        showDrawerButton = isCompactMobile,
                        onOpenNavDrawer = { scope.launch { drawerState.open() } }
                    )

                    NexusSection.PROFILE_SETTINGS -> ProfileAndSettingsScreen(
                        state = state,
                        viewModel = viewModel,
                        showDrawerButton = isCompactMobile,
                        onOpenNavDrawer = { scope.launch { drawerState.open() } },
                        onSignOut = onSignOut
                    )
                }
            }
        }

        if (isCompactMobile) {
            ModalNavigationDrawer(
                drawerState = drawerState,
                drawerContent = {
                    ModalDrawerSheet(modifier = Modifier.width(332.dp)) {
                        sidebarContent()
                    }
                }
            ) {
                Scaffold(
                    snackbarHost = { SnackbarHost(snackbarHostState) },
                    bottomBar = {
                        if (state.selectedDmThreadId == null) {
                            NavigationBar {
                                NavigationBarItem(
                                    selected = state.activeSection == NexusSection.HOME_DMS || state.activeSection == NexusSection.SERVER,
                                    onClick = { viewModel.openHomeDms(null) },
                                    icon = { Icon(Icons.Default.ChatBubble, null) },
                                    label = { Text(stringResource(R.string.nav_home)) }
                                )
                                NavigationBarItem(
                                    selected = state.activeSection == NexusSection.DISCOVERY,
                                    onClick = { viewModel.selectSection(NexusSection.DISCOVERY) },
                                    icon = { Icon(Icons.Default.Explore, null) },
                                    label = { Text(stringResource(R.string.nav_discover)) }
                                )
                                NavigationBarItem(
                                    selected = state.activeSection == NexusSection.NOTIFICATIONS,
                                    onClick = { viewModel.selectSection(NexusSection.NOTIFICATIONS) },
                                    icon = {
                                        BadgedBox(
                                            badge = {
                                                if (state.unreadNotificationsCount > 0) {
                                                    Badge { Text(state.unreadNotificationsCount.toString()) }
                                                }
                                            }
                                        ) { Icon(Icons.Default.Notifications, null) }
                                    },
                                    label = { Text(stringResource(R.string.nav_alerts)) }
                                )
                                NavigationBarItem(
                                    selected = state.activeSection == NexusSection.PROFILE_SETTINGS,
                                    onClick = { viewModel.selectSection(NexusSection.PROFILE_SETTINGS) },
                                    icon = { Icon(Icons.Default.Person, null) },
                                    label = { Text(stringResource(R.string.nav_you)) }
                                )
                            }
                        }
                    }
                ) { innerPadding ->
                    Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
                        mainStage(Modifier.fillMaxSize())
                    }
                }
            }
        } else {
            // Tablet (2-pane) & Desktop/PC (3-pane Discord Layout)
            Scaffold(snackbarHost = { SnackbarHost(snackbarHostState) }) { innerPadding ->
                Row(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
                    sidebarContent()
                    mainStage(Modifier.weight(1f).fillMaxHeight())
                }
            }
        }

        if (showMobileMembersSheet && (!isWidePc || isTabletMedium)) {
            ModalBottomSheet(onDismissRequest = { showMobileMembersSheet = false }) {
                Box(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
                    OnlineMembersPane(
                        members = state.activeServerMembers,
                        onMemberClick = {
                            showMobileMembersSheet = false
                            inspectedMember = it
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }

    // Global Dialogs
    if (showCreateServerDialog) {
        CreateServerDialog(
            onDismiss = { showCreateServerDialog = false },
            onCreate = viewModel::createServer
        )
    }
    if (showJoinInviteDialog) {
        JoinInviteDialog(
            onDismiss = { showJoinInviteDialog = false },
            onJoin = viewModel::joinByInviteCode
        )
    }
    if (editingChannelTarget.first && state.activeServer != null) {
        val srv = state.activeServer!!
        val existingCh = editingChannelTarget.second
        ChannelEditorDialog(
            existing = existingCh,
            onDismiss = { editingChannelTarget = false to null },
            onSave = { name, topic, type, cat, adminOnly ->
                if (existingCh == null) viewModel.createChannel(srv.id, name, topic, type, cat, adminOnly)
                else viewModel.updateChannel(srv.id, existingCh, name, topic, type, cat, adminOnly)
            },
            onDelete = existingCh?.let { ch -> { viewModel.deleteChannel(srv.id, ch.id) } }
        )
    }
    if (showServerSettingsDialog && state.activeServer != null) {
        val srv = state.activeServer!!
        ServerSettingsAndModDialog(
            server = srv,
            members = state.activeServerMembers,
            currentUid = state.currentUid.orEmpty(),
            onDismiss = { showServerSettingsDialog = false },
            onUpdateServer = { n, d, e, v, regen -> viewModel.updateServer(srv, n, d, e, v, regen) },
            onDeleteServer = { viewModel.deleteServer(srv) },
            onAssignRole = { targetUid, role -> viewModel.assignRole(srv, targetUid, role) },
            onKickMember = { targetUid -> viewModel.kickOrBanMember(srv, targetUid, ban = false) },
            onBanMember = { targetUid -> viewModel.kickOrBanMember(srv, targetUid, ban = true) },
            onUnbanMember = { targetUid -> viewModel.unbanMember(srv, targetUid) },
            onShowSnack = viewModel::showSnack
        )
    }
    inspectedMember?.let { member ->
        val srv = state.activeServer
        val profile = state.publicDirectory.firstOrNull { it.userId == member.userId }
        val isBlocked = member.userId in state.blockedUids
        MemberInspectorDialog(
            member = member,
            profile = profile,
            currentUid = state.currentUid.orEmpty(),
            isBlocked = isBlocked,
            myRole = state.myRoleInActiveServer,
            onDismiss = { inspectedMember = null },
            onSendFriendRequest = {
                profile?.let(viewModel::sendFriendRequest)
                    ?: viewModel.showSnack("Profile still loading")
            },
            onDirectMessage = {
                profile?.let(viewModel::openDmWithUser)
                    ?: viewModel.showSnack("Profile still loading")
            },
            onBlockToggle = {
                if (isBlocked) viewModel.unblockUser(member.userId)
                else viewModel.blockUser(member.userId, member.displayName, member.gamerTag)
            },
            onKick = { srv?.let { viewModel.kickOrBanMember(it, member.userId, ban = false) } },
            onBan = { srv?.let { viewModel.kickOrBanMember(it, member.userId, ban = true) } }
        )
    }
}
