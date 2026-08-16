package com.youneshatti.jarboa.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.youneshatti.jarboa.BuildConfig
import com.youneshatti.jarboa.MainViewModel
import com.youneshatti.jarboa.R
import com.youneshatti.jarboa.domain.model.Conversation
import com.youneshatti.jarboa.domain.model.DirectMessage
import com.youneshatti.jarboa.domain.model.MessageStatus
import com.youneshatti.jarboa.domain.model.XmppConnectionState
import com.youneshatti.jarboa.domain.validation.JidValidationResult
import com.youneshatti.jarboa.domain.validation.XmppAddressValidator
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun JarboaApp(viewModel: MainViewModel) {
    val signedIn by viewModel.signedIn.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        if (signedIn) {
            HomeScreen(viewModel = viewModel, busy = busy)
        } else {
            SignInScreen(busy = busy, onSignIn = viewModel::signIn)
        }
    }

    error?.let { message ->
        AlertDialog(
            onDismissRequest = viewModel::dismissError,
            title = { Text("Jarboa could not complete that action") },
            text = { Text(message) },
            confirmButton = { TextButton(onClick = viewModel::dismissError) { Text("OK") } },
        )
    }
}

@Composable
private fun SignInScreen(
    busy: Boolean,
    onSignIn: (String, String, String, String) -> Unit,
) {
    var jid by rememberSaveable { mutableStateOf("") }
    // Passwords must not enter Android's saved-instance-state Bundle.
    var password by remember { mutableStateOf("") }
    var showServerSettings by rememberSaveable { mutableStateOf(false) }
    var serverHost by rememberSaveable { mutableStateOf("") }
    var serverPort by rememberSaveable { mutableStateOf("5222") }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 28.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        item {
            JarboaMark()
            Spacer(Modifier.height(28.dp))
            Text("JARBOA", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Black)
            Text(
                "A focused XMPP messenger for Android and GrapheneOS.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyLarge,
            )
            Spacer(Modifier.height(32.dp))
            OutlinedTextField(
                value = jid,
                onValueChange = { jid = it },
                modifier = Modifier.fillMaxWidth(),
                enabled = !busy,
                singleLine = true,
                label = { Text("XMPP address") },
                placeholder = { Text("name@example.org") },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                modifier = Modifier.fillMaxWidth(),
                enabled = !busy,
                singleLine = true,
                label = { Text("Password") },
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(
                    onDone = { onSignIn(jid, password, serverHost, serverPort) },
                ),
            )
            TextButton(
                onClick = { showServerSettings = !showServerSettings },
                enabled = !busy,
                modifier = Modifier.padding(top = 4.dp),
            ) {
                Text(if (showServerSettings) "Hide server settings" else "Custom server settings")
            }
            if (showServerSettings) {
                OutlinedTextField(
                    value = serverHost,
                    onValueChange = { serverHost = it },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !busy,
                    singleLine = true,
                    label = { Text("Host override (optional)") },
                    placeholder = { Text("xmpp.example.org") },
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = serverPort,
                    onValueChange = { serverPort = it.filter(Char::isDigit).take(5) },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !busy,
                    singleLine = true,
                    label = { Text("Port") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
            }
            Spacer(Modifier.height(20.dp))
            Button(
                onClick = { onSignIn(jid, password, serverHost, serverPort) },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                enabled = !busy,
            ) {
                if (busy) {
                    CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                } else {
                    Text("Connect securely")
                }
            }
            Spacer(Modifier.height(16.dp))
            Text(
                "TLS certificate validation is required. This first release does not yet provide OMEMO end-to-end encryption.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun JarboaMark() {
    Image(
        painter = painterResource(R.drawable.ic_jarboa_artwork),
        contentDescription = null,
        contentScale = ContentScale.Fit,
        modifier = Modifier.width(180.dp).aspectRatio(1009f / 748f),
    )
}

private enum class HomeTab(val title: String, val glyph: String) {
    CHATS("Chats", "●"),
    CONTACTS("Contacts", "◇"),
    SETTINGS("Settings", "≡"),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeScreen(viewModel: MainViewModel, busy: Boolean) {
    var tabName by rememberSaveable { mutableStateOf(HomeTab.CHATS.name) }
    var showNewChat by rememberSaveable { mutableStateOf(false) }
    val tab = HomeTab.valueOf(tabName)
    val selectedConversation by viewModel.selectedConversation.collectAsStateWithLifecycle()
    val connectionState by viewModel.connectionState.collectAsStateWithLifecycle()
    val conversations by viewModel.conversations.collectAsStateWithLifecycle()

    if (selectedConversation != null) {
        ConversationScreen(
            jid = selectedConversation!!,
            viewModel = viewModel,
            connectionState = connectionState,
        )
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(tab.title, fontWeight = FontWeight.Bold)
                        ConnectionLabel(connectionState)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Black),
            )
        },
        bottomBar = {
            NavigationBar(containerColor = Color(0xFF0D0D0D)) {
                HomeTab.entries.forEach { item ->
                    NavigationBarItem(
                        selected = item == tab,
                        onClick = { tabName = item.name },
                        icon = { Text(item.glyph, fontWeight = FontWeight.Black) },
                        label = { Text(item.title) },
                    )
                }
            }
        },
        floatingActionButton = {
            if (tab == HomeTab.CHATS) {
                FloatingActionButton(onClick = { showNewChat = true }) { Text("+", style = MaterialTheme.typography.headlineSmall) }
            }
        },
        containerColor = Color.Black,
    ) { padding ->
        when (tab) {
            HomeTab.CHATS -> ChatsScreen(
                conversations = conversations,
                onConversation = viewModel::openConversation,
                onNewChat = { showNewChat = true },
                modifier = Modifier.padding(padding),
            )
            HomeTab.CONTACTS -> PhasePlaceholder(
                title = "Contacts are next",
                detail = "Roster sync and presence are planned for Phase 0.3.0. You can start a direct chat now from the Chats tab.",
                modifier = Modifier.padding(padding),
            )
            HomeTab.SETTINGS -> SettingsScreen(
                viewModel = viewModel,
                state = connectionState,
                busy = busy,
                modifier = Modifier.padding(padding),
            )
        }
    }

    if (showNewChat) {
        NewChatDialog(
            onDismiss = { showNewChat = false },
            onOpen = { jid ->
                viewModel.openConversation(jid)
                showNewChat = false
            },
        )
    }
}

@Composable
private fun ConnectionLabel(state: XmppConnectionState) {
    val label = when (state) {
        is XmppConnectionState.Connected -> "Connected · ${state.boundJid}"
        XmppConnectionState.Connecting -> "Connecting"
        XmppConnectionState.Authenticating -> "Authenticating"
        is XmppConnectionState.Reconnecting -> "Reconnecting in ${state.secondsUntilRetry}s"
        is XmppConnectionState.Failed -> "Connection needs attention"
        XmppConnectionState.Disconnected -> "Offline"
        XmppConnectionState.SignedOut -> "Signed out"
    }
    val color = if (state is XmppConnectionState.Connected) Color(0xFFB9F6CA) else MaterialTheme.colorScheme.onSurfaceVariant
    Text(label, color = color, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
}

@Composable
private fun ChatsScreen(
    conversations: List<Conversation>,
    onConversation: (String) -> Unit,
    onNewChat: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (conversations.isEmpty()) {
        PhasePlaceholder(
            title = "No conversations yet",
            detail = "Start a direct chat with a full XMPP address.",
            actionLabel = "New chat",
            onAction = onNewChat,
            modifier = modifier,
        )
        return
    }
    LazyColumn(modifier = modifier.fillMaxSize()) {
        items(conversations, key = Conversation::jid) { conversation ->
            Row(
                modifier = Modifier.fillMaxWidth().clickable { onConversation(conversation.jid) }.padding(18.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier.size(44.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(conversation.displayName.take(1).uppercase(), fontWeight = FontWeight.Black)
                }
                Spacer(Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(conversation.displayName, fontWeight = FontWeight.Bold, maxLines = 1)
                        Text(formatListTime(conversation.latestTimestamp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text(
                        conversation.latestPreview,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (conversation.unreadCount > 0) {
                    Spacer(Modifier.width(10.dp))
                    Surface(shape = CircleShape, color = Color.White, contentColor = Color.Black) {
                        Text(conversation.unreadCount.coerceAtMost(99).toString(), modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp), style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ConversationScreen(
    jid: String,
    viewModel: MainViewModel,
    connectionState: XmppConnectionState,
) {
    val messages by viewModel.messages(jid).collectAsStateWithLifecycle(initialValue = emptyList())
    val listState = rememberLazyListState()
    var draft by rememberSaveable(jid) { mutableStateOf("") }
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.lastIndex)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = { TextButton(onClick = viewModel::closeConversation) { Text("‹ Back") } },
                title = {
                    Column {
                        Text(jid.substringBefore('@'), fontWeight = FontWeight.Bold)
                        Text(jid, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Black),
            )
        },
        bottomBar = {
            Row(
                modifier = Modifier.fillMaxWidth()
                    .navigationBarsPadding()
                    .imePadding()
                    .padding(12.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it.take(4096) },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Message") },
                    maxLines = 4,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = {
                        viewModel.sendMessage(jid, draft)
                        draft = ""
                    }),
                )
                Spacer(Modifier.width(10.dp))
                Button(
                    onClick = {
                        viewModel.sendMessage(jid, draft)
                        draft = ""
                    },
                    enabled = draft.isNotBlank() && connectionState is XmppConnectionState.Connected,
                    modifier = Modifier.height(56.dp),
                ) { Text("Send") }
            }
        },
        containerColor = Color.Black,
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            Surface(color = Color(0xFF2A2418), contentColor = Color(0xFFFFE0A3)) {
                Text(
                    "UNENCRYPTED · OMEMO IS PLANNED FOR PHASE 0.2.0",
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 9.dp),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                )
            }
            if (messages.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Start the conversation", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(messages, key = DirectMessage::id) { message -> MessageBubble(message) }
                }
            }
        }
    }
}

@Composable
private fun MessageBubble(message: DirectMessage) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (message.outgoing) Arrangement.End else Arrangement.Start,
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(0.82f),
            shape = RoundedCornerShape(16.dp),
            color = if (message.outgoing) Color.White else MaterialTheme.colorScheme.surfaceVariant,
            contentColor = if (message.outgoing) Color.Black else Color.White,
        ) {
            Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                Text(message.body)
                Row(modifier = Modifier.align(Alignment.End), verticalAlignment = Alignment.CenterVertically) {
                    Text(formatMessageTime(message.timestamp), style = MaterialTheme.typography.labelSmall)
                    if (message.outgoing) {
                        Spacer(Modifier.width(7.dp))
                        Text(message.status.label, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

private val MessageStatus.label: String
    get() = when (this) {
        MessageStatus.PENDING -> "QUEUED"
        MessageStatus.SENT -> "SENT"
        MessageStatus.DELIVERED -> "DELIVERED"
        MessageStatus.FAILED -> "FAILED"
    }

@Composable
private fun SettingsScreen(
    viewModel: MainViewModel,
    state: XmppConnectionState,
    busy: Boolean,
    modifier: Modifier = Modifier,
) {
    val hidden by viewModel.hideNotificationContent.collectAsStateWithLifecycle()
    var showThirdPartyNotices by rememberSaveable { mutableStateOf(false) }
    LazyColumn(modifier = modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            SettingsCard("Connection") {
                ConnectionLabel(state)
                Spacer(Modifier.height(6.dp))
                Text("TLS is required and certificate hostnames are validated.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        item {
            SettingsCard("Notification privacy") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Hide sender and message text on the lock screen", modifier = Modifier.weight(1f))
                    Switch(checked = hidden, onCheckedChange = viewModel::setHideNotificationContent)
                }
            }
        }
        item {
            SettingsCard("Encryption status") {
                Text("Direct chats are not end-to-end encrypted in ${BuildConfig.VERSION_NAME}.")
                Spacer(Modifier.height(6.dp))
                Text("OMEMO is a gated Phase 0.2.0 milestone.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        item {
            SettingsCard("About") {
                Text("Jarboa ${BuildConfig.VERSION_NAME}")
                Text("GPL-3.0-or-later · no Google Play services", color = MaterialTheme.colorScheme.onSurfaceVariant)
                TextButton(onClick = { showThirdPartyNotices = true }) { Text("Third-party notices") }
            }
        }
        item {
            OutlinedButton(onClick = viewModel::signOut, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                Text("Sign out and erase local data")
            }
        }
    }

    if (showThirdPartyNotices) {
        val context = LocalContext.current
        val notices = remember {
            context.assets.open("third_party_notices.txt").bufferedReader().use { it.readText() }
        }
        AlertDialog(
            onDismissRequest = { showThirdPartyNotices = false },
            title = { Text("Third-party notices") },
            text = {
                LazyColumn(modifier = Modifier.heightIn(max = 420.dp)) {
                    item { Text(notices, style = MaterialTheme.typography.bodySmall) }
                }
            },
            confirmButton = {
                TextButton(onClick = { showThirdPartyNotices = false }) { Text("Close") }
            },
        )
    }
}

@Composable
private fun SettingsCard(title: String, content: @Composable () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(modifier = Modifier.fillMaxWidth().padding(18.dp)) {
            Text(title, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(10.dp))
            content()
        }
    }
}

@Composable
private fun PhasePlaceholder(
    title: String,
    detail: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Box(modifier = modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(10.dp))
            Text(detail, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (actionLabel != null && onAction != null) {
                Spacer(Modifier.height(20.dp))
                Button(onClick = onAction) { Text(actionLabel) }
            }
        }
    }
}

@Composable
private fun NewChatDialog(onDismiss: () -> Unit, onOpen: (String) -> Unit) {
    var jid by rememberSaveable { mutableStateOf("") }
    var validationError by remember { mutableStateOf<String?>(null) }
    fun submit() {
        when (val result = XmppAddressValidator.validate(jid)) {
            is JidValidationResult.Valid -> onOpen(result.normalizedJid)
            is JidValidationResult.Invalid -> validationError = result.message
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New direct chat") },
        text = {
            OutlinedTextField(
                value = jid,
                onValueChange = {
                    jid = it
                    validationError = null
                },
                singleLine = true,
                label = { Text("XMPP address") },
                placeholder = { Text("person@example.org") },
                isError = validationError != null,
                supportingText = validationError?.let { message -> { Text(message) } },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { submit() }),
            )
        },
        confirmButton = { TextButton(onClick = { submit() }) { Text("Open") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

private val listTimeFormatter = DateTimeFormatter.ofPattern("MMM d")
private val messageTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

private fun formatListTime(timestamp: Long): String = Instant.ofEpochMilli(timestamp)
    .atZone(ZoneId.systemDefault())
    .format(listTimeFormatter)

private fun formatMessageTime(timestamp: Long): String = Instant.ofEpochMilli(timestamp)
    .atZone(ZoneId.systemDefault())
    .format(messageTimeFormatter)
