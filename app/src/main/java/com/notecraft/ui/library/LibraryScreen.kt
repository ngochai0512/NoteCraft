package com.notecraft.ui.library

import android.content.res.Configuration
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.automirrored.outlined.Label
import androidx.compose.material.icons.outlined.DriveFileRenameOutline
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.automirrored.outlined.Sort
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.notecraft.model.Note
import com.notecraft.ui.NoteCraftViewModel
import com.notecraft.ui.components.ConfirmDialog
import com.notecraft.ui.components.CoverToneDialog
import com.notecraft.ui.components.ErrorBanner
import com.notecraft.ui.components.NoteOrganizationDialog
import com.notecraft.ui.components.NoteThumbnail
import com.notecraft.ui.components.TextEntryDialog
import com.notecraft.ui.theme.InkBlackColor
import com.notecraft.ui.theme.InkSlate
import com.notecraft.ui.theme.NotebookCoverTones
import com.notecraft.ui.theme.PaperIvory
import com.notecraft.ui.theme.notebookToneFor
import com.notecraft.util.PdfExporter
import com.notecraft.util.formatNoteDate
import com.notecraft.util.matchesLibrary
import kotlinx.coroutines.launch

private enum class NoteSort(val label: String) {
    UPDATED("Recently updated"),
    CREATED("Recently created"),
    TITLE("Title (A–Z)"),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(viewModel: NoteCraftViewModel) {
    val library = viewModel.library
    var query by remember { mutableStateOf("") }
    var showSearch by remember { mutableStateOf(false) }
    var sort by remember { mutableStateOf(NoteSort.UPDATED) }
    var showSortMenu by remember { mutableStateOf(false) }
    var showFilterMenu by remember { mutableStateOf(false) }
    var folderFilter by remember { mutableStateOf<String?>(null) }
    var tagFilter by remember { mutableStateOf<String?>(null) }
    var menuNoteId by remember { mutableStateOf<String?>(null) }
    var renameNote by remember { mutableStateOf<Note?>(null) }
    var deleteNote by remember { mutableStateOf<Note?>(null) }
    var coverNote by remember { mutableStateOf<Note?>(null) }
    var organizeNote by remember { mutableStateOf<Note?>(null) }
    var exportNote by remember { mutableStateOf<Note?>(null) }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/pdf"),
    ) { uri ->
        val note = exportNote
        if (uri != null && note != null) {
            Toast.makeText(context, "Exporting PDF…", Toast.LENGTH_SHORT).show()
            scope.launch {
                runCatching { PdfExporter.export(context, uri, note) }
                    .onSuccess { Toast.makeText(context, "PDF exported", Toast.LENGTH_SHORT).show() }
                    .onFailure { Toast.makeText(context, "Could not export PDF", Toast.LENGTH_SHORT).show() }
            }
        }
        exportNote = null
    }

    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val isTablet = configuration.smallestScreenWidthDp >= 600
    val gridMinWidth = when {
        isTablet -> 240.dp
        isLandscape -> 140.dp
        else -> 156.dp
    }
    val screenPadding = if (isTablet) 24.dp else 16.dp

    val folders = remember(library.notes) {
        library.notes.mapNotNull { it.folder }
            .distinctBy { it.lowercase() }
            .sortedBy { it.lowercase() }
    }
    val allTags = remember(library.notes) {
        library.notes.flatMap { it.tags }
            .distinctBy { it.lowercase() }
            .sortedBy { it.lowercase() }
    }
    val filtered = remember(library.notes, query, sort, folderFilter, tagFilter) {
        val sortComparator = when (sort) {
            NoteSort.UPDATED -> compareByDescending<Note> { it.updatedAt }
            NoteSort.CREATED -> compareByDescending<Note> { it.createdAt }
            NoteSort.TITLE -> compareBy<Note> { it.title.lowercase() }
        }
        library.notes
            .filter { it.matchesLibrary(query, folderFilter, tagFilter) }
            .sortedWith(
                compareByDescending<Note> { it.pinned }.then(sortComparator),
            )
    }

    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    LaunchedEffect(showSearch) {
        if (showSearch) focusRequester.requestFocus()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                windowInsets = WindowInsets.safeDrawing.only(
                    WindowInsetsSides.Top + WindowInsetsSides.Horizontal,
                ),
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(38.dp),
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Outlined.Description,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                        }
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "NoteCraft",
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                text = if (library.notes.isEmpty()) {
                                    "YOUR PAPER, EVERYWHERE"
                                } else {
                                    "${library.notes.size} ${if (library.notes.size == 1) "NOTEBOOK" else "NOTEBOOKS"}"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
                actions = {
                    IconButton(onClick = viewModel::openSettings) {
                        Icon(Icons.Outlined.Settings, contentDescription = "Settings")
                    }
                    IconButton(
                        onClick = {
                            showSearch = !showSearch
                            if (!showSearch) {
                                query = ""
                                keyboardController?.hide()
                            }
                        },
                    ) {
                        Icon(
                            if (showSearch) Icons.Outlined.Close else Icons.Outlined.Search,
                            contentDescription = if (showSearch) "Close search" else "Search notes",
                        )
                    }
                    Box {
                        IconButton(onClick = { showFilterMenu = true }) {
                            Icon(Icons.Outlined.FilterList, contentDescription = "Filter notebooks")
                        }
                        DropdownMenu(
                            expanded = showFilterMenu,
                            onDismissRequest = { showFilterMenu = false },
                        ) {
                            DropdownMenuItem(
                                text = { Text("All notebooks") },
                                leadingIcon = {
                                    if (folderFilter == null && tagFilter == null) {
                                        Icon(Icons.Outlined.Check, contentDescription = null)
                                    } else {
                                        Spacer(Modifier.size(24.dp))
                                    }
                                },
                                onClick = {
                                    folderFilter = null
                                    tagFilter = null
                                    showFilterMenu = false
                                },
                            )
                            folders.forEach { folder ->
                                DropdownMenuItem(
                                    text = { Text("Folder · $folder") },
                                    leadingIcon = { Icon(Icons.Outlined.Folder, contentDescription = null) },
                                    trailingIcon = {
                                        if (folderFilter == folder) {
                                            Icon(Icons.Outlined.Check, contentDescription = null)
                                        }
                                    },
                                    onClick = {
                                        folderFilter = folder
                                        tagFilter = null
                                        showFilterMenu = false
                                    },
                                )
                            }
                            allTags.forEach { tag ->
                                DropdownMenuItem(
                                    text = { Text("Tag · $tag") },
                                    leadingIcon = { Icon(Icons.AutoMirrored.Outlined.Label, contentDescription = null) },
                                    trailingIcon = {
                                        if (tagFilter == tag) {
                                            Icon(Icons.Outlined.Check, contentDescription = null)
                                        }
                                    },
                                    onClick = {
                                        tagFilter = tag
                                        folderFilter = null
                                        showFilterMenu = false
                                    },
                                )
                            }
                        }
                    }
                    Box {
                        IconButton(onClick = { showSortMenu = true }) {
                            Icon(Icons.AutoMirrored.Outlined.Sort, contentDescription = "Sort notes")
                        }
                        DropdownMenu(
                            expanded = showSortMenu,
                            onDismissRequest = { showSortMenu = false },
                        ) {
                            NoteSort.entries.forEach { option ->
                                DropdownMenuItem(
                                    text = { Text(option.label) },
                                    leadingIcon = {
                                        if (sort == option) {
                                            Icon(Icons.Outlined.Check, contentDescription = null)
                                        } else {
                                            Spacer(Modifier.size(24.dp))
                                        }
                                    },
                                    onClick = {
                                        sort = option
                                        showSortMenu = false
                                    },
                                )
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
        floatingActionButton = {
            if (library.notes.isNotEmpty()) {
                ExtendedFloatingActionButton(
                    onClick = { viewModel.createNote() },
                    icon = { Icon(Icons.Outlined.Add, contentDescription = null) },
                    text = { Text("New notebook") },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.windowInsetsPadding(
                        WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal),
                    ),
                )
            }
        },
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets.safeDrawing,
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            if (showSearch) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                        .focusRequester(focusRequester),
                    placeholder = { Text("Search titles, tags, folders") },
                    leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                    trailingIcon = {
                        if (query.isNotEmpty()) {
                            IconButton(onClick = { query = "" }) {
                                Icon(Icons.Outlined.Close, contentDescription = "Clear search")
                            }
                        }
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(
                        onSearch = { keyboardController?.hide() },
                    ),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surface,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                    ),
                )
            }

            viewModel.saveError?.let { message ->
                ErrorBanner(
                    message = message,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                    actionLabel = "Retry",
                    onAction = viewModel::retrySave,
                    onDismiss = viewModel::dismissSaveError,
                )
            }
            library.error?.takeIf { library.notes.isNotEmpty() }?.let { message ->
                ErrorBanner(
                    message = message,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                    actionLabel = "Retry",
                    onAction = viewModel::reloadLibrary,
                )
            }

            when {
                library.loading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator()
                    }
                }

                library.error != null && library.notes.isEmpty() -> {
                    LibraryError(
                        message = library.error,
                        onRetry = viewModel::reloadLibrary,
                    )
                }

                library.notes.isEmpty() -> {
                    EmptyLibrary(
                        onCreate = { viewModel.createNote() },
                        isLandscape = isLandscape,
                        isTablet = isTablet,
                        modifier = Modifier.fillMaxSize(),
                    )
                }

                filtered.isEmpty() -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Icon(
                            Icons.Outlined.Search,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        )
                        Spacer(Modifier.height(12.dp))
                        Text(
                            text = when {
                                query.isNotBlank() -> "No notes match \"$query\""
                                folderFilter != null -> "No notes in $folderFilter"
                                tagFilter != null -> "No notes tagged $tagFilter"
                                else -> "No notes match this filter"
                            },
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = "Try a different search term or clear the filter.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (folderFilter != null || tagFilter != null) {
                            TextButton(
                                onClick = {
                                    folderFilter = null
                                    tagFilter = null
                                },
                            ) {
                                Text("Clear filter")
                            }
                        }
                    }
                }

                else -> {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = gridMinWidth),
                        contentPadding = PaddingValues(
                            start = screenPadding,
                            end = screenPadding,
                            top = 8.dp,
                            bottom = 104.dp,
                        ),
                        horizontalArrangement = Arrangement.spacedBy(if (isTablet) 20.dp else 16.dp),
                        verticalArrangement = Arrangement.spacedBy(if (isTablet) 22.dp else 18.dp),
                    ) {
                        items(filtered, key = { it.id }) { note ->
                            NoteCard(
                                note = note,
                                onOpen = { viewModel.openNote(note.id) },
                                menuExpanded = menuNoteId == note.id,
                                onMenuClick = { menuNoteId = note.id },
                                onMenuDismiss = { menuNoteId = null },
                                onRename = {
                                    menuNoteId = null
                                    renameNote = note
                                },
                                onDuplicate = {
                                    menuNoteId = null
                                    viewModel.duplicateNote(note.id)
                                },
                                onCover = {
                                    menuNoteId = null
                                    coverNote = note
                                },
                                onOrganize = {
                                    menuNoteId = null
                                    organizeNote = note
                                },
                                onTogglePin = {
                                    menuNoteId = null
                                    viewModel.toggleNotePin(note.id)
                                },
                                onExport = {
                                    menuNoteId = null
                                    exportNote = note
                                    exportLauncher.launch(PdfExporter.fileName(note.title))
                                },
                                onDelete = {
                                    menuNoteId = null
                                    deleteNote = note
                                },
                            )
                        }
                    }
                }
            }
        }
    }

    renameNote?.let { note ->
        TextEntryDialog(
            title = "Rename note",
            initialValue = note.title,
            label = "Title",
            onDismiss = { renameNote = null },
            onConfirm = { title ->
                viewModel.renameNoteInLibrary(note.id, title)
                renameNote = null
            },
        )
    }

    deleteNote?.let { note ->
        ConfirmDialog(
            title = "Delete note?",
            message = "\"${note.title}\" and all of its pages will be permanently deleted.",
            onDismiss = { deleteNote = null },
            onConfirm = { viewModel.deleteNote(note.id) },
        )
    }

    coverNote?.let { note ->
        CoverToneDialog(
            noteId = note.id,
            currentTone = note.coverTone,
            onDismiss = { coverNote = null },
            onSelect = { tone ->
                viewModel.setNoteCover(note.id, tone)
                coverNote = null
            },
        )
    }

    organizeNote?.let { note ->
        NoteOrganizationDialog(
            initialFolder = note.folder,
            initialTags = note.tags,
            onDismiss = { organizeNote = null },
            onConfirm = { folder, tags ->
                viewModel.updateNoteOrganization(note.id, folder, tags)
                organizeNote = null
            },
        )
    }
}

@Composable
private fun LibraryError(
    message: String,
    onRetry: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            Icons.Outlined.Close,
            contentDescription = null,
            modifier = Modifier.size(44.dp),
            tint = MaterialTheme.colorScheme.error,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "Your notes are still on this device. Try loading again.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        TextButton(onClick = onRetry) {
            Text("Retry")
        }
    }
}

@Composable
private fun EmptyLibrary(
    onCreate: () -> Unit,
    isLandscape: Boolean,
    isTablet: Boolean,
    modifier: Modifier = Modifier,
) {
    val tone = remember { notebookToneFor("welcome") }
    val coverWidth = when {
        isTablet -> 152.dp
        isLandscape -> 100.dp
        else -> 118.dp
    }
    val coverHeight = when {
        isTablet -> 196.dp
        isLandscape -> 130.dp
        else -> 152.dp
    }

    if (isLandscape) {
        Row(
            modifier = modifier
                .fillMaxSize()
                .padding(horizontal = 48.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            EmptyNotebookCover(tone = tone, width = coverWidth, height = coverHeight)
            Spacer(Modifier.width(32.dp))
            Column(
                modifier = Modifier.widthIn(max = 440.dp),
                horizontalAlignment = Alignment.Start,
            ) {
                Text(
                    text = "A fresh notebook awaits",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Capture lectures, sketches and ideas with tactile paper, ink and focused tools.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(16.dp))
                CreateNotebookButton(onCreate = onCreate)
            }
        }
    } else {
        Column(
            modifier = modifier.padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            EmptyNotebookCover(tone = tone, width = coverWidth, height = coverHeight)
            Spacer(Modifier.height(if (isTablet) 32.dp else 26.dp))
            Text(
                text = "A fresh notebook awaits",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Capture lectures, sketches and ideas with tactile paper, ink and focused tools.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 12.dp),
            )
            Spacer(Modifier.height(24.dp))
            CreateNotebookButton(onCreate = onCreate)
        }
    }
}

@Composable
private fun EmptyNotebookCover(
    tone: com.notecraft.ui.theme.NotebookCoverTone,
    width: androidx.compose.ui.unit.Dp,
    height: androidx.compose.ui.unit.Dp,
) {
    Box(
        modifier = Modifier
            .size(width = width, height = height)
            .shadow(10.dp, RoundedCornerShape(10.dp))
            .clip(RoundedCornerShape(10.dp))
            .background(Brush.linearGradient(listOf(tone.cover, tone.coverDeep))),
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxHeight()
                .width(24.dp),
        ) {
            drawRect(
                brush = Brush.horizontalGradient(
                    listOf(Color.Black.copy(alpha = 0.20f), Color.Transparent),
                ),
            )
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 16.dp, top = 14.dp, end = 12.dp, bottom = 14.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(PaperIvory),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Outlined.Description,
                contentDescription = null,
                modifier = Modifier.size(36.dp),
                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.65f),
            )
        }
        Canvas(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(end = width * 0.23f)
                .size(width = 13.dp, height = 38.dp),
        ) {
            val notch = 5.dp.toPx()
            val ribbon = Path().apply {
                moveTo(0f, 0f)
                lineTo(size.width, 0f)
                lineTo(size.width, size.height)
                lineTo(size.width / 2f, size.height - notch)
                lineTo(0f, size.height)
                close()
            }
            drawPath(ribbon, tone.ribbon.copy(alpha = 0.95f))
        }
    }
}

@Composable
private fun CreateNotebookButton(onCreate: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.clickable(onClick = onCreate),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Outlined.Add,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(19.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = "Create notebook",
                color = MaterialTheme.colorScheme.onPrimary,
                style = MaterialTheme.typography.labelLarge,
            )
        }
    }
}

@Composable
private fun NoteCard(
    note: Note,
    onOpen: () -> Unit,
    menuExpanded: Boolean,
    onMenuClick: () -> Unit,
    onMenuDismiss: () -> Unit,
    onRename: () -> Unit,
    onDuplicate: () -> Unit,
    onCover: () -> Unit,
    onOrganize: () -> Unit,
    onTogglePin: () -> Unit,
    onExport: () -> Unit,
    onDelete: () -> Unit,
) {
    val tone = remember(note.id, note.coverTone) {
        note.coverTone
            ?.let { name -> NotebookCoverTones.firstOrNull { it.name == name } }
            ?: notebookToneFor(note.id)
    }
    val previewPage = remember(note.pages) {
        note.pages.firstOrNull { !it.isEmpty } ?: note.pages.firstOrNull()
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(3f / 4f)
                .shadow(6.dp, RoundedCornerShape(10.dp))
                .clip(RoundedCornerShape(10.dp))
                .background(Brush.linearGradient(listOf(tone.cover, tone.coverDeep))),
        ) {
            // Spine crease along the binding edge.
            Canvas(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(26.dp),
            ) {
                drawRect(
                    brush = Brush.horizontalGradient(
                        listOf(
                            Color.Black.copy(alpha = 0.22f),
                            Color.Black.copy(alpha = 0.05f),
                            Color.Transparent,
                        ),
                    ),
                )
                drawLine(
                    color = Color.White.copy(alpha = 0.10f),
                    start = Offset(5.dp.toPx(), 0f),
                    end = Offset(5.dp.toPx(), size.height),
                    strokeWidth = 1f,
                )
            }

            // Paper page tucked inside the cover.
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(start = 20.dp, top = 14.dp, end = 12.dp, bottom = 76.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(PaperIvory),
            ) {
                NoteThumbnail(
                    page = previewPage,
                    modifier = Modifier.fillMaxSize(),
                    cacheKey = previewPage?.let { page ->
                        "${note.id}:${note.updatedAt}:${page.id}"
                    },
                )
            }

            // Pinned badge.
            if (note.pinned) {
                Surface(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(6.dp),
                    shape = CircleShape,
                    color = PaperIvory.copy(alpha = 0.94f),
                    shadowElevation = 2.dp,
                ) {
                    Box(
                        modifier = Modifier.size(30.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Filled.PushPin,
                            contentDescription = "Pinned notebook",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
            }

            // Ribbon bookmark.
            Canvas(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(end = 34.dp)
                    .size(width = 14.dp, height = 40.dp),
            ) {
                val notch = 5.dp.toPx()
                val ribbon = Path().apply {
                    moveTo(0f, 0f)
                    lineTo(size.width, 0f)
                    lineTo(size.width, size.height)
                    lineTo(size.width / 2f, size.height - notch)
                    lineTo(0f, size.height)
                    close()
                }
                drawPath(ribbon, tone.ribbon.copy(alpha = 0.95f))
            }

            // Quick actions.
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp),
            ) {
                Surface(
                    shape = CircleShape,
                    color = PaperIvory.copy(alpha = 0.94f),
                    shadowElevation = 2.dp,
                ) {
                    IconButton(
                        onClick = onMenuClick,
                        modifier = Modifier.size(30.dp),
                    ) {
                        Icon(
                            Icons.Outlined.MoreVert,
                            contentDescription = "Notebook options",
                            tint = InkBlackColor,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = onMenuDismiss,
                ) {
                    DropdownMenuItem(
                        text = { Text(if (note.pinned) "Unpin notebook" else "Pin notebook") },
                        leadingIcon = {
                            Icon(
                                if (note.pinned) Icons.Filled.PushPin else Icons.Outlined.PushPin,
                                contentDescription = null,
                                tint = if (note.pinned) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                            )
                        },
                        onClick = onTogglePin,
                    )
                    DropdownMenuItem(
                        text = { Text("Rename") },
                        leadingIcon = { Icon(Icons.Outlined.DriveFileRenameOutline, contentDescription = null) },
                        onClick = onRename,
                    )
                    DropdownMenuItem(
                        text = { Text("Cover color") },
                        leadingIcon = { Icon(Icons.Outlined.Palette, contentDescription = null) },
                        onClick = onCover,
                    )
                    DropdownMenuItem(
                        text = { Text("Tags & folder") },
                        leadingIcon = { Icon(Icons.AutoMirrored.Outlined.Label, contentDescription = null) },
                        onClick = onOrganize,
                    )
                    DropdownMenuItem(
                        text = { Text("Duplicate") },
                        leadingIcon = { Icon(Icons.Outlined.ContentCopy, contentDescription = null) },
                        onClick = onDuplicate,
                    )
                    DropdownMenuItem(
                        text = { Text("Export PDF") },
                        leadingIcon = { Icon(Icons.Outlined.PictureAsPdf, contentDescription = null) },
                        onClick = onExport,
                    )
                    DropdownMenuItem(
                        text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                        leadingIcon = {
                            Icon(
                                Icons.Outlined.Delete,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                            )
                        },
                        onClick = onDelete,
                    )
                }
            }

            // Title label badge, like a notebook sticker.
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 18.dp, bottom = 12.dp, end = 14.dp)
                    .widthIn(max = 150.dp),
                shape = RoundedCornerShape(6.dp),
                color = PaperIvory.copy(alpha = 0.97f),
                shadowElevation = 2.dp,
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp),
                ) {
                    Text(
                        text = note.title,
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = InkBlackColor,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(1.dp))
                    Text(
                        text = "${note.pageCount} ${if (note.pageCount == 1) "page" else "pages"}",
                        style = MaterialTheme.typography.labelSmall,
                        color = InkSlate,
                    )
                    val organization = remember(note.folder, note.tags) {
                        buildList {
                            note.folder?.let { add(it) }
                            if (note.tags.isNotEmpty()) {
                                add(note.tags.take(2).joinToString(" ") { "#$it" })
                            }
                        }.joinToString(" · ")
                    }
                    if (organization.isNotEmpty()) {
                        Spacer(Modifier.height(1.dp))
                        Text(
                            text = organization,
                            style = MaterialTheme.typography.labelSmall,
                            color = InkSlate,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = formatNoteDate(note.updatedAt),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "EDITED",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            )
        }
    }
}
