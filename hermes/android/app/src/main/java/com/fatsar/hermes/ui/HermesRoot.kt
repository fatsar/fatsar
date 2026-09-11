package com.fatsar.hermes.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.fatsar.hermes.data.BotEngine
import com.fatsar.hermes.service.BotRunnerService
import com.fatsar.hermes.ui.screens.BotEditScreen
import com.fatsar.hermes.ui.screens.BotListScreen
import com.fatsar.hermes.ui.screens.ChatScreen
import com.fatsar.hermes.ui.screens.ServerEditScreen
import com.fatsar.hermes.ui.screens.ServersScreen
import com.fatsar.hermes.ui.screens.SettingsScreen
import com.fatsar.hermes.ui.screens.SetupScreen

sealed interface Screen {
    data object Bots : Screen

    data class Chat(val botId: String) : Screen

    data class EditBot(val botId: String?) : Screen

    data object Servers : Screen

    data class EditServer(val serverId: String?) : Screen

    data object Settings : Screen

    data object Setup : Screen
}

@Composable
fun HermesRoot(
    engine: BotEngine,
    startBotId: MutableState<String?>,
    sharedText: MutableState<String?>,
) {
    val context = LocalContext.current
    val data by engine.data.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    var stack by remember { mutableStateOf<List<Screen>>(listOf(Screen.Bots)) }

    val current = stack.last()
    fun push(screen: Screen) {
        stack = stack + screen
    }

    fun pop() {
        if (stack.size > 1) stack = stack.dropLast(1)
    }

    fun replaceRoot(screen: Screen) {
        stack = listOf(screen)
    }

    val notificationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { }

    fun askNotifications() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            runCatching { notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS) }
        }
    }

    // İlk açılış: sunucu yoksa kurulum ekranı.
    LaunchedEffect(data.servers.isEmpty(), data.onboarded) {
        if (data.servers.isEmpty() && !data.onboarded && stack.size == 1 && current is Screen.Bots) {
            replaceRoot(Screen.Setup)
        }
    }

    // Bildirimden gelen bot açılır.
    LaunchedEffect(startBotId.value) {
        val botId = startBotId.value ?: return@LaunchedEffect
        if (engine.bot(botId) != null) {
            stack = listOf(Screen.Bots, Screen.Chat(botId))
        }
        startBotId.value = null
    }

    // Kısa bilgilendirmeler
    LaunchedEffect(Unit) {
        engine.notices.collect { snackbar.showSnackbar(it) }
    }

    // Bağlantı durumlarını açılışta tazele
    LaunchedEffect(Unit) { engine.refreshConnections() }

    // Arka plan servisi yalnızca telefonda zamanlanmış bot varsa çalışır.
    LaunchedEffect(data.backgroundEnabled, data.bots, data.servers) {
        if (data.backgroundEnabled && engine.locallyScheduledBots().isNotEmpty()) {
            BotRunnerService.start(context)
        } else {
            BotRunnerService.stop(context)
        }
    }

    BackHandler(enabled = stack.size > 1) { pop() }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        val modifier = Modifier.padding(padding)
        when (val screen = current) {
            is Screen.Setup -> SetupScreen(
                engine = engine,
                modifier = modifier,
                onDone = {
                    engine.markOnboarded()
                    askNotifications()
                    replaceRoot(Screen.Bots)
                },
            )

            is Screen.Bots -> BotListScreen(
                engine = engine,
                modifier = modifier,
                onOpenChat = { push(Screen.Chat(it)) },
                onNewBot = { push(Screen.EditBot(null)) },
                onEditBot = { push(Screen.EditBot(it)) },
                onServers = { push(Screen.Servers) },
                onSettings = { push(Screen.Settings) },
            )

            is Screen.Chat -> ChatScreen(
                engine = engine,
                botId = screen.botId,
                modifier = modifier,
                sharedText = sharedText,
                onBack = { pop() },
                onEdit = { push(Screen.EditBot(screen.botId)) },
            )

            is Screen.EditBot -> BotEditScreen(
                engine = engine,
                botId = screen.botId,
                modifier = modifier,
                onBack = { pop() },
                onSaved = { botId ->
                    askNotifications()
                    stack = listOf(Screen.Bots, Screen.Chat(botId))
                },
                onDeleted = { replaceRoot(Screen.Bots) },
                onAddServer = { push(Screen.EditServer(null)) },
            )

            is Screen.Servers -> ServersScreen(
                engine = engine,
                modifier = modifier,
                onBack = { pop() },
                onAdd = { push(Screen.EditServer(null)) },
                onEdit = { push(Screen.EditServer(it)) },
            )

            is Screen.EditServer -> ServerEditScreen(
                engine = engine,
                serverId = screen.serverId,
                modifier = modifier,
                onBack = { pop() },
            )

            is Screen.Settings -> SettingsScreen(
                engine = engine,
                modifier = modifier,
                onBack = { pop() },
                onRequestNotifications = { askNotifications() },
            )
        }
    }
}
