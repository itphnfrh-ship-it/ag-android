package com.cruisepass.verify.ui

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.ui.Alignment
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.cruisepass.verify.MainActivity
import com.cruisepass.verify.R
import com.cruisepass.verify.data.CruiseRepository
import com.cruisepass.verify.data.HardwareScanner
import com.cruisepass.verify.data.LoadedCruise
import com.cruisepass.verify.data.LogCruise
import com.cruisepass.verify.data.LogEvent
import com.cruisepass.verify.data.Lookup
import com.cruisepass.verify.data.ManualSearch
import com.cruisepass.verify.data.Person
import com.cruisepass.verify.data.Refresh
import com.cruisepass.verify.data.scannedCardId
import kotlinx.coroutines.Dispatchers
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private sealed interface ScreenState {
    data object Empty : ScreenState
    data class Profile(val person: Person, val cruise: LoadedCruise) : ScreenState
    data class Absent(val cardId: String, val manual: Boolean = false) : ScreenState
    data class Choices(val people: List<Person>) : ScreenState
    data object NoCruise : ScreenState
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScanScreen(repository: CruiseRepository) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val scanner = remember { HardwareScanner(context) }
    val lifecycleOwner = LocalLifecycleOwner.current
    var state by remember { mutableStateOf<ScreenState>(ScreenState.Empty) }
    var cruise by remember { mutableStateOf<LoadedCruise?>(null) }
    var syncing by remember { mutableStateOf(true) }
    var keptLocal by remember { mutableStateOf(false) }
    var online by remember { mutableStateOf<Boolean?>(null) }
    var showCamera by remember { mutableStateOf(false) }
    var manualOpen by remember { mutableStateOf(false) }
    var logOpen by remember { mutableStateOf(false) }
    var logCruiseId by remember { mutableStateOf<String?>(null) }
    var logCruises by remember { mutableStateOf<List<LogCruise>>(emptyList()) }
    var logEvents by remember { mutableStateOf<List<LogEvent>>(emptyList()) }
    var cameraMessage by remember { mutableStateOf<Int?>(null) }
    var lastCode by remember { mutableStateOf("") }
    var lastAt by remember { mutableLongStateOf(0L) }

    fun accept(raw: String) {
        val cardId = scannedCardId(raw)
        if (cardId.isEmpty()) return
        val now = android.os.SystemClock.elapsedRealtime()
        if (cardId == lastCode && now - lastAt < 1_200) return
        lastCode = cardId
        lastAt = now
        showCamera = false
        scope.launch {
            state = when (val lookup = repository.lookup(cardId)) {
                Lookup.NoCruise -> ScreenState.NoCruise
                is Lookup.Found -> {
                    repository.record(lookup.cruise, lookup.person)
                    ScreenState.Profile(lookup.person, lookup.cruise)
                }
                is Lookup.Missing -> ScreenState.Absent(lookup.cardId)
            }
            cruise = repository.currentCruise()
        }
    }

    DisposableEffect(manualOpen, logOpen) {
        val activity = context as MainActivity
        activity.onWedgeScan = if (manualOpen || logOpen) null else ::accept
        onDispose { activity.onWedgeScan = null }
    }

    fun submitManual(raw: String) {
        val query = raw.trim()
        if (query.isEmpty()) return
        scope.launch {
            when (val result = repository.search(query)) {
                ManualSearch.NoCruise -> {
                    manualOpen = false
                    state = ScreenState.NoCruise
                }
                is ManualSearch.None -> {
                    manualOpen = false
                    state = ScreenState.Absent(result.query, manual = true)
                }
                is ManualSearch.Matches -> {
                    manualOpen = false
                    if (result.people.size == 1) {
                        val person = result.people.first()
                        repository.record(result.cruise, person)
                        state = ScreenState.Profile(person, result.cruise)
                    } else {
                        state = ScreenState.Choices(result.people)
                    }
                }
            }
            cruise = repository.currentCruise()
        }
    }

    DisposableEffect(lifecycleOwner, scanner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> scanner.open()
                Lifecycle.Event.ON_STOP -> scanner.close()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
            scanner.open()
        }
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            scanner.close()
        }
    }
    scanner.onResult = ::accept

    suspend fun performRefresh() {
        syncing = true
        try {
            val report = repository.refresh()
            cruise = repository.currentCruise()
            online = report.online
            keptLocal = report.refresh == Refresh.KEPT_LOCAL
            val shown = (state as? ScreenState.Profile)?.person?.cardId
            if (report.refresh == Refresh.UNAVAILABLE) {
                state = ScreenState.NoCruise
            } else if (report.refresh == Refresh.UPDATED && shown != null) {
                state = when (val lookup = repository.lookup(shown)) {
                    Lookup.NoCruise -> ScreenState.NoCruise
                    is Lookup.Found -> ScreenState.Profile(lookup.person, lookup.cruise)
                    is Lookup.Missing -> ScreenState.Absent(lookup.cardId)
                }
            }
        } finally {
            syncing = false
        }
    }

    LaunchedEffect(Unit) {
        cruise = repository.currentCruise()
        performRefresh()
    }

    val exportLog = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        val cruiseId = logCruiseId
        if (uri == null || cruiseId == null) return@rememberLauncherForActivityResult
        val headers = listOf(
            context.getString(R.string.csv_date),
            context.getString(R.string.csv_time),
            context.getString(R.string.card_number),
            context.getString(R.string.csv_last_name),
            context.getString(R.string.csv_first_name),
            context.getString(R.string.cabin),
            context.getString(R.string.csv_type),
        )
        val pax = context.getString(R.string.type_pax)
        val crew = context.getString(R.string.type_crew)
        scope.launch {
            val events = repository.events(cruiseId)
            val bytes = buildLogCsv(headers, events, pax, crew)
            withContext(Dispatchers.IO) {
                context.contentResolver.openOutputStream(uri)?.use { it.write(bytes) }
            }
        }
    }

    LaunchedEffect(logOpen, logCruiseId) {
        if (!logOpen) return@LaunchedEffect
        val current = repository.currentCruise()
        val saved = repository.logCruises()
        val options = buildList {
            if (current != null && saved.none { it.id == current.id }) {
                add(LogCruise(current.id, current.ship))
            }
            addAll(saved)
        }.sortedByDescending { it.id == current?.id }
        logCruises = options
        val selected = logCruiseId?.takeIf { id -> options.any { it.id == id } }
            ?: current?.id
            ?: options.firstOrNull()?.id
        if (selected != logCruiseId) {
            logCruiseId = selected
            return@LaunchedEffect
        }
        logEvents = if (selected == null) emptyList() else repository.events(selected)
    }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            showCamera = true
        } else {
            cameraMessage = R.string.camera_permission
        }
    }

    if (showCamera) {
        CameraScanner(
            onCode = ::accept,
            onClose = { showCamera = false },
        )
        return
    }

    val chooseLanguage = stringResource(R.string.choose_language)
    val homeLabel = stringResource(R.string.home)
    var languageMenuOpen by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                navigationIcon = {
                    Surface(
                        onClick = { state = ScreenState.Empty },
                        modifier = Modifier
                            .padding(start = 12.dp)
                            .size(40.dp)
                            .semantics { contentDescription = homeLabel },
                        color = Color.White,
                        shape = RoundedCornerShape(8.dp),
                    ) {
                        Image(
                            painter = painterResource(R.drawable.logo_mark),
                            contentDescription = null,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(4.dp),
                        )
                    }
                },
                title = { Text(stringResource(R.string.header_title)) },
                actions = {
                    LinkStatus(online)
                    androidx.compose.foundation.layout.Box {
                        IconButton(
                            onClick = { languageMenuOpen = true },
                            modifier = Modifier.semantics { contentDescription = chooseLanguage },
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_globe),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimary,
                            )
                        }
                        DropdownMenu(
                            expanded = languageMenuOpen,
                            onDismissRequest = { languageMenuOpen = false },
                        ) {
                            AppLanguage.entries.forEach { language ->
                                DropdownMenuItem(
                                    text = { Text("${language.label} (${language.code})") },
                                    onClick = {
                                        languageMenuOpen = false
                                        if (language != AppLanguage.current(context)) {
                                            language.persist(context)
                                            AppLanguage.apply(context)
                                            (context as Activity).recreate()
                                        }
                                    },
                                )
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                ),
            )
        },
        bottomBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp)
                    .padding(top = 8.dp, bottom = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val manualLabel = stringResource(R.string.enter_manually)
                OutlinedButton(
                    onClick = { manualOpen = true },
                    modifier = Modifier
                        .size(56.dp)
                        .semantics { contentDescription = manualLabel },
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_hand),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
                Button(
                    onClick = {
                        cameraMessage = null
                        if (scanner.available) {
                            scanner.startDecode()
                            return@Button
                        }
                        val hasCamera = context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY)
                        if (!hasCamera) {
                            cameraMessage = R.string.no_camera
                        } else if (
                            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                            PackageManager.PERMISSION_GRANTED
                        ) {
                            showCamera = true
                        } else {
                            permissionLauncher.launch(Manifest.permission.CAMERA)
                        }
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(56.dp),
                ) {
                    Text(stringResource(R.string.scan_card))
                }
            }
        },
    ) { padding ->
        val shownCard = (state as? ScreenState.Profile)?.person?.cardId
        val detailsScroll = rememberScrollState()
        LaunchedEffect(shownCard) { detailsScroll.scrollTo(0) }
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
        ) {
            cruise?.let { loaded ->
                CruiseIdLine(
                    cruise = loaded,
                    syncing = syncing,
                    onRefresh = {
                        if (!syncing) scope.launch { performRefresh() }
                    },
                )
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(detailsScroll)
                    .padding(horizontal = 20.dp)
                    .padding(top = 16.dp, bottom = 8.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                cruise?.let { loaded ->
                    val profile = state as? ScreenState.Profile
                    if (profile == null) {
                        CruiseSummary(
                            loaded,
                            if (scanner.available) R.string.scan_hint_terminal else R.string.scan_hint,
                            onOpenLog = {
                                logCruiseId = loaded.id
                                logOpen = true
                            },
                        )
                    } else {
                        PersonCard(profile.person, loaded.ship)
                    }
                }
                if (syncing) {
                    CircularProgressIndicator()
                    Text(stringResource(R.string.syncing))
                }
                if (keptLocal) {
                    Text(stringResource(R.string.sync_failed), color = MaterialTheme.colorScheme.error)
                }
                cameraMessage?.let { Text(stringResource(it), color = MaterialTheme.colorScheme.error) }
                if (state is ScreenState.NoCruise) Text(stringResource(R.string.no_cruise))
            }
        }
    }

    when (val current = state) {
        is ScreenState.Absent -> CardResultDialog(
            result = current,
            onClose = { state = ScreenState.Empty },
        )
        is ScreenState.Choices -> ChoiceDialog(
            people = current.people,
            onChoose = { person ->
                val loaded = cruise
                if (loaded == null) {
                    state = ScreenState.NoCruise
                } else {
                    state = ScreenState.Profile(person, loaded)
                    scope.launch { repository.record(loaded, person) }
                }
            },
            onClose = { state = ScreenState.Empty },
        )
        is ScreenState.Profile, ScreenState.Empty, ScreenState.NoCruise -> Unit
    }

    if (manualOpen) {
        ManualEntryDialog(
            onSearch = ::submitManual,
            onClose = { manualOpen = false },
        )
    }

    if (logOpen) {
        EventLogDialog(
            cruises = logCruises,
            selectedId = logCruiseId,
            events = logEvents,
            onSelect = { logCruiseId = it },
            onExport = {
                val id = logCruiseId ?: return@EventLogDialog
                exportLog.launch("events-$id.csv")
            },
            onClose = { logOpen = false },
        )
    }
}

@Composable
private fun LinkStatus(online: Boolean?) {
    val description = stringResource(
        when (online) {
            null -> R.string.syncing
            true -> R.string.status_online
            false -> R.string.status_offline
        },
    )
    Row(
        modifier = Modifier
            .padding(end = 4.dp)
            .semantics(mergeDescendants = true) { contentDescription = description },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (online == null) {
            CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.onPrimary,
            )
        } else {
            Icon(
                painter = painterResource(
                    if (online) R.drawable.ic_status_online else R.drawable.ic_status_offline,
                ),
                contentDescription = null,
                tint = Color.Unspecified,
                modifier = Modifier.size(22.dp),
            )
            Text(
                text = description,
                color = MaterialTheme.colorScheme.onPrimary,
                style = MaterialTheme.typography.labelLarge,
            )
        }
    }
}

@Composable
private fun CruiseIdLine(cruise: LoadedCruise, syncing: Boolean, onRefresh: () -> Unit) {
    val refreshLabel = stringResource(if (syncing) R.string.syncing else R.string.refresh)
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.secondary,
        shape = RectangleShape,
    ) {
        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Text(
                text = "${cruise.ship} | ${cruise.id}",
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
                    .padding(horizontal = 36.dp),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSecondary,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
            )
            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 4.dp)
                    .size(28.dp)
                    .clickable(enabled = !syncing, onClick = onRefresh)
                    .semantics { contentDescription = refreshLabel },
                contentAlignment = Alignment.Center,
            ) {
                if (syncing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onSecondary,
                    )
                } else {
                    Icon(
                        painter = painterResource(R.drawable.ic_sync),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSecondary,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
    }
}

private fun updatedLabel(updatedAt: String?): String? {
    val raw = updatedAt ?: return null
    val instant = runCatching { Instant.parse(raw) }.getOrNull() ?: return null
    return DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")
        .withZone(ZoneId.systemDefault())
        .format(instant)
}

@Composable
private fun CruiseSummary(cruise: LoadedCruise, hint: Int, onOpenLog: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = cruise.ship,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            updatedLabel(cruise.updatedAt)?.let { updated ->
                Text(
                    text = updated,
                    modifier = Modifier.padding(start = 12.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.secondary,
                    maxLines = 1,
                )
            }
        }
        cruise.currentPort?.let { port ->
            Text(
                text = stringResource(R.string.current_port, port),
                color = MaterialTheme.colorScheme.secondary,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            CountCard(cruise.paxCount, R.string.passengers, Modifier.weight(1f))
            CountCard(cruise.crewCount, R.string.crew_members, Modifier.weight(1f))
        }
        Text(stringResource(hint))
        TextButton(onClick = onOpenLog, modifier = Modifier.align(Alignment.Start)) {
            Text(stringResource(R.string.event_log))
        }
    }
}

private fun eventParts(atMillis: Long): Pair<String, String> {
    val instant = Instant.ofEpochMilli(atMillis)
    val zone = ZoneId.systemDefault()
    val date = DateTimeFormatter.ofPattern("dd.MM.yyyy").withZone(zone).format(instant)
    val time = DateTimeFormatter.ofPattern("HH:mm").withZone(zone).format(instant)
    return date to time
}

private fun buildLogCsv(
    headers: List<String>,
    events: List<LogEvent>,
    pax: String,
    crew: String,
): ByteArray {
    val text = buildString {
        append('\uFEFF')
        append(headers.joinToString(";") { csvCell(it) })
        append("\r\n")
        events.forEach { event ->
            val (date, time) = eventParts(event.atMillis)
            val type = if (event.type == "crew") crew else pax
            append(
                listOf(date, time, event.cardId, event.lastName, event.firstName, event.cabin.orEmpty(), type)
                    .joinToString(";") { csvCell(it) },
            )
            append("\r\n")
        }
    }
    return text.toByteArray(Charsets.UTF_8)
}

private fun csvCell(value: String): String {
    val escaped = value.replace("\"", "\"\"")
    return if (escaped.any { it == ';' || it == '"' || it == '\n' || it == '\r' }) "\"$escaped\"" else escaped
}

@Composable
private fun EventLogDialog(
    cruises: List<LogCruise>,
    selectedId: String?,
    events: List<LogEvent>,
    onSelect: (String) -> Unit,
    onExport: () -> Unit,
    onClose: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    val selected = cruises.firstOrNull { it.id == selectedId }
    val cruiseLabel = selected?.let { "${it.ship} | ${it.id}" }.orEmpty()
    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(24.dp),
            contentAlignment = Alignment.Center,
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 560.dp)
                    .fillMaxHeight(0.85f),
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surface,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Box(modifier = Modifier.weight(1f)) {
                            TextButton(
                                onClick = { menuOpen = true },
                                enabled = cruises.isNotEmpty(),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(cruiseLabel, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                                cruises.forEach { item ->
                                    DropdownMenuItem(
                                        text = { Text("${item.ship} | ${item.id}") },
                                        onClick = {
                                            menuOpen = false
                                            onSelect(item.id)
                                        },
                                    )
                                }
                            }
                        }
                        Button(onClick = onExport, enabled = selectedId != null) {
                            Text(stringResource(R.string.export))
                        }
                    }
                    if (events.isEmpty()) {
                        Text(
                            text = stringResource(R.string.log_empty),
                            modifier = Modifier.weight(1f),
                        )
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            items(events, key = { it.id }) { event -> EventCard(event) }
                        }
                    }
                    TextButton(onClick = onClose, modifier = Modifier.align(Alignment.End)) {
                        Text(stringResource(R.string.close))
                    }
                }
            }
        }
    }
}

@Composable
private fun EventCard(event: LogEvent) {
    val (date, time) = eventParts(event.atMillis)
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.background,
        shape = MaterialTheme.shapes.medium,
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = "$date $time  ${event.cardId}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.secondary,
            )
            Text(
                text = "${event.lastName} ${event.firstName}",
                style = MaterialTheme.typography.titleMedium,
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                event.cabin?.takeIf { it.isNotBlank() }?.let { cabin ->
                    Text(text = cabin, style = MaterialTheme.typography.bodyLarge)
                }
                TypeTag(event.type)
            }
        }
    }
}

@Composable
private fun CountCard(count: Int, label: Int, modifier: Modifier) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.medium,
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = count.toString(),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = stringResource(label),
                color = MaterialTheme.colorScheme.secondary,
            )
        }
    }
}

@Composable
private fun CardResultDialog(result: ScreenState, onClose: () -> Unit) {
    val closeLabel = stringResource(R.string.close)
    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(24.dp),
            contentAlignment = Alignment.Center,
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 560.dp)
                    .heightIn(max = 720.dp),
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surface,
            ) {
                Column {
                    Box(modifier = Modifier.fillMaxWidth()) {
                        IconButton(
                            onClick = onClose,
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .semantics { contentDescription = closeLabel },
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Close,
                                contentDescription = closeLabel,
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                    Column(
                        modifier = Modifier
                            .weight(1f, fill = false)
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 20.dp)
                            .padding(bottom = 8.dp),
                    ) {
                        when (result) {
                            is ScreenState.Absent -> AbsentNotice(result.cardId, result.manual)
                            is ScreenState.Profile, is ScreenState.Choices, ScreenState.Empty, ScreenState.NoCruise -> Unit
                        }
                    }
                    Button(
                        onClick = onClose,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                    ) {
                        Text(closeLabel)
                    }
                }
            }
        }
    }
}

@Composable
private fun AbsentNotice(cardId: String, manual: Boolean) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color(0xFFFDECEC),
        shape = MaterialTheme.shapes.medium,
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                stringResource(R.string.not_in_cruise),
                color = Color(0xFFB42318),
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                stringResource(
                    if (manual) R.string.entered_value else R.string.scanned_code,
                    cardId,
                ),
            )
        }
    }
}

@Composable
private fun PersonCard(person: Person, ship: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.medium,
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            val wide = maxWidth >= 600.dp
            if (wide) {
                Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                    PersonPhoto(person.photoPath, Modifier.weight(0.4f))
                    PersonFacts(person, ship, Modifier.weight(0.6f))
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    PersonPhoto(person.photoPath, Modifier.fillMaxWidth().heightIn(max = 120.dp))
                    PersonFacts(person, ship, Modifier.fillMaxWidth())
                }
            }
        }
    }
}

@Composable
private fun PersonPhoto(path: String?, modifier: Modifier) {
    var bitmap by remember(path) { mutableStateOf<android.graphics.Bitmap?>(null) }
    LaunchedEffect(path) {
        bitmap = withContext(Dispatchers.IO) {
            path?.let { BitmapFactory.decodeFile(it) }
        }
    }
    Surface(modifier = modifier, color = MaterialTheme.colorScheme.surface, shape = MaterialTheme.shapes.medium) {
        val image = bitmap
        if (image == null) {
            Text(stringResource(R.string.no_photo), modifier = Modifier.padding(24.dp))
        } else {
            Image(
                bitmap = image.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun PersonFacts(person: Person, ship: String, modifier: Modifier) {
    val passport = person.documentNumber?.takeIf { it.isNotBlank() }
    val nationality = person.nationality?.takeIf { it.isNotBlank() }
    val passportLine = when {
        passport != null && nationality != null -> "$passport ($nationality)"
        else -> passport
    }
    val rank = if (person.type == "crew") person.rank else null
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("${person.lastName} ${person.firstName}", style = MaterialTheme.typography.headlineSmall)
        TypeTag(person.type)
        Fact(R.string.rank, rank)
        Fact(R.string.ship, ship)
        Fact(R.string.cabin, person.cabin)
        Fact(R.string.passport, passportLine)
        Fact(R.string.date_of_birth, person.dateOfBirth)
    }
}

@Composable
private fun TypeTag(type: String) {
    val crew = type == "crew"
    Surface(
        color = if (crew) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary,
        shape = RoundedCornerShape(6.dp),
    ) {
        Text(
            text = stringResource(if (crew) R.string.type_crew else R.string.type_pax),
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelLarge,
            color = Color.White,
        )
    }
}

@Composable
private fun ManualEntryDialog(onSearch: (String) -> Unit, onClose: () -> Unit) {
    var query by remember { mutableStateOf("") }
    val closeLabel = stringResource(R.string.close)
    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding()
                .padding(24.dp),
            contentAlignment = Alignment.Center,
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 560.dp),
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surface,
            ) {
                Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(R.string.enter_manually), style = MaterialTheme.typography.titleMedium)
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.manual_hint)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { onSearch(query) }),
                    )
                    Button(
                        onClick = { onSearch(query) },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = query.isNotBlank(),
                    ) {
                        Text(stringResource(R.string.manual_search))
                    }
                    TextButton(onClick = onClose, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                        Text(closeLabel)
                    }
                }
            }
        }
    }
}

@Composable
private fun ChoiceDialog(people: List<Person>, onChoose: (Person) -> Unit, onClose: () -> Unit) {
    val closeLabel = stringResource(R.string.close)
    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(24.dp),
            contentAlignment = Alignment.Center,
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 560.dp)
                    .heightIn(max = 720.dp),
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surface,
            ) {
                Column {
                    Text(
                        stringResource(R.string.several_matches),
                        modifier = Modifier.padding(20.dp),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Column(
                        modifier = Modifier
                            .weight(1f, fill = false)
                            .verticalScroll(rememberScrollState()),
                    ) {
                        people.forEach { person ->
                            TextButton(
                                onClick = { onChoose(person) },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Column(modifier = Modifier.fillMaxWidth()) {
                                    Text(
                                        "${person.lastName} ${person.firstName}",
                                        color = MaterialTheme.colorScheme.onSurface,
                                    )
                                    val details = listOfNotNull(person.cabin, person.documentNumber)
                                        .joinToString(" · ")
                                    if (details.isNotEmpty()) {
                                        Text(details, color = MaterialTheme.colorScheme.secondary)
                                    }
                                }
                            }
                        }
                    }
                    Button(
                        onClick = onClose,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                    ) {
                        Text(closeLabel)
                    }
                }
            }
        }
    }
}

@Composable
private fun Fact(label: Int, value: String?) {
    if (value.isNullOrBlank()) return
    Text(stringResource(label) + ": " + value)
}
