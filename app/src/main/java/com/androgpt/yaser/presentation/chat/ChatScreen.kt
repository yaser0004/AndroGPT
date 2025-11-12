package com.androgpt.yaser.presentation.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ClipboardManager
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.androgpt.yaser.domain.model.GenerationState
import com.androgpt.yaser.domain.model.Message
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.*

private sealed class MessageSegment {
    data class Text(val text: String) : MessageSegment()
    data class Code(val language: String?, val code: String) : MessageSegment()
}

private sealed class TextBlock {
    data class Heading(val level: Int, val content: String) : TextBlock()
    data class Paragraph(val content: String) : TextBlock()
    data class BulletList(val items: List<String>) : TextBlock()
    data class OrderedList(val items: List<String>, val startIndex: Int) : TextBlock()
}

private const val LINK_TAG = "link-tag"
private const val EMAIL_TAG = "email-tag"

private val urlRegex = Regex("((https?://)|(www\\.))[-A-Za-z0-9+&@#/%?=~_|!:,.;]*[-A-Za-z0-9+&@#/%=~_|]")
private val emailRegex = Regex("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}")

private fun IntRange.overlaps(other: IntRange): Boolean {
    return first <= other.last && other.first <= last
}

private fun parseMessageContent(text: String): List<MessageSegment> {
    if (!text.contains("```")) {
        return listOf(MessageSegment.Text(text))
    }
    val segments = mutableListOf<MessageSegment>()
    val regex = Regex("```([\\w+-]*)\\s*\n([\\s\\S]*?)```", RegexOption.MULTILINE)
    var lastIndex = 0
    regex.findAll(text).forEach { matchResult ->
        val start = matchResult.range.first
        if (start > lastIndex) {
            val preceding = text.substring(lastIndex, start)
            if (preceding.isNotEmpty()) {
                segments.add(MessageSegment.Text(preceding))
            }
        }
        val language = matchResult.groupValues.getOrNull(1)?.takeIf { it.isNotBlank() }
        val code = matchResult.groupValues.getOrNull(2)?.replace("\r\n", "\n") ?: ""
        segments.add(MessageSegment.Code(language, code))
        lastIndex = matchResult.range.last + 1
    }
    if (lastIndex < text.length) {
        val remaining = text.substring(lastIndex)
        if (remaining.isNotEmpty()) {
            segments.add(MessageSegment.Text(remaining))
        }
    }
    if (segments.isEmpty()) {
        segments.add(MessageSegment.Text(text))
    }
    return segments
}

private fun parseTextBlocks(text: String): List<TextBlock> {
    val blocks = mutableListOf<TextBlock>()
    val lines = text.lines()
    var index = 0
    val orderedRegex = Regex("^(\\d+)\\.\\s+")

    while (index < lines.size) {
        val rawLine = lines[index]
        if (rawLine.isBlank()) {
            index++
            continue
        }

        val trimmed = rawLine.trim()

        if (trimmed.startsWith("#")) {
            val level = trimmed.takeWhile { it == '#' }.length.coerceIn(1, 6)
            val content = trimmed.drop(level).trim()
            if (content.isNotEmpty()) {
                blocks.add(TextBlock.Heading(level, content))
            }
            index++
            continue
        }

        if (trimmed.startsWith("- ") || trimmed.startsWith("* ")) {
            val items = mutableListOf<String>()
            while (index < lines.size) {
                val current = lines[index].trim()
                if (current.startsWith("- ") || current.startsWith("* ")) {
                    items.add(current.drop(2).trim())
                    index++
                } else if (current.isBlank()) {
                    index++
                    break
                } else {
                    break
                }
            }
            if (items.isNotEmpty()) {
                blocks.add(TextBlock.BulletList(items))
            }
            continue
        }

        val orderedMatch = orderedRegex.find(trimmed)
        if (orderedMatch != null) {
            val items = mutableListOf<String>()
            val startIndex = orderedMatch.groupValues[1].toIntOrNull() ?: 1
            var cursor = index
            while (cursor < lines.size) {
                val currentTrimmed = lines[cursor].trim()
                val match = orderedRegex.find(currentTrimmed)
                if (match != null) {
                    val content = currentTrimmed.substring(match.value.length).trim()
                    if (content.isNotEmpty()) {
                        items.add(content)
                    }
                    cursor++
                } else if (currentTrimmed.isBlank()) {
                    cursor++
                    break
                } else {
                    break
                }
            }
            if (items.isNotEmpty()) {
                blocks.add(TextBlock.OrderedList(items, startIndex))
            }
            index = cursor
            continue
        }

        val paragraphLines = mutableListOf<String>()
        while (index < lines.size && lines[index].isNotBlank()) {
            paragraphLines.add(lines[index])
            index++
        }
        if (paragraphLines.isNotEmpty()) {
            blocks.add(TextBlock.Paragraph(paragraphLines.joinToString("\n")))
        }
    }

    return blocks
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    viewModel: ChatViewModel = hiltViewModel()
) {
    val messages by viewModel.messages.collectAsState()
    val generationState by viewModel.generationState.collectAsState()
    val inputText by viewModel.inputText.collectAsState()
    val loadedModel by viewModel.loadedModel.collectAsState()
    val conversations by viewModel.conversations.collectAsState()
    val currentConversationId by viewModel.currentConversationId.collectAsState()
    val listState = rememberLazyListState()
    
    var showClearHistoryDialog by remember { mutableStateOf(false) }
    var showConversationListDialog by remember { mutableStateOf(false) }
    
    // Auto-scroll to bottom when new messages arrive
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size)
        }
    }
    
    // Clear history confirmation dialog
    if (showClearHistoryDialog) {
        AlertDialog(
            onDismissRequest = { showClearHistoryDialog = false },
            title = { Text("Clear Chat History") },
            text = { Text("This will delete all conversations and messages. This action cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.clearAllHistory()
                        showClearHistoryDialog = false
                    }
                ) {
                    Text("Clear")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearHistoryDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
    
    // Conversation list dialog
    if (showConversationListDialog) {
        ConversationListDialog(
            conversations = conversations,
            currentConversationId = currentConversationId,
            onConversationSelected = { conversationId ->
                viewModel.loadConversation(conversationId)
            },
            onNewConversation = {
                viewModel.startNewConversation()
            },
            onDeleteConversation = { conversationId ->
                viewModel.deleteConversation(conversationId)
            },
            onDismiss = { showConversationListDialog = false }
        )
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Column {
                        Text("Chat")
                        loadedModel?.let {
                            Text(
                                text = it.name,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                },
                actions = {
                    var showMenu by remember { mutableStateOf(false) }
                    
                    IconButton(onClick = { showMenu = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "More options")
                    }
                    
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("View History") },
                            onClick = {
                                showMenu = false
                                showConversationListDialog = true
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("New Chat") },
                            onClick = {
                                viewModel.startNewConversation()
                                showMenu = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Clear All History") },
                            onClick = {
                                showMenu = false
                                showClearHistoryDialog = true
                            }
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Messages list
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                if (messages.isEmpty() && generationState !is GenerationState.Generating) {
                    // Empty state
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = if (loadedModel == null) {
                                "No model loaded.\nPlease select a model in Settings."
                            } else {
                                "Start a conversation with your AI assistant"
                            },
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(messages, key = { it.id }) { message ->
                            MessageBubble(message)
                        }
                        
                        // Show loading/thinking indicator
                        if (generationState is GenerationState.Loading) {
                            item {
                                ThinkingIndicator()
                            }
                        }
                        
                        // Show generating state
                        if (generationState is GenerationState.Generating) {
                            item {
                                val currentText = (generationState as GenerationState.Generating).currentText
                                StreamingMessageBubble(currentText)
                            }
                        }
                        
                        // Show error state
                        if (generationState is GenerationState.Error) {
                            item {
                                val errorMessage = (generationState as GenerationState.Error).message
                                ErrorMessageBubble(errorMessage)
                            }
                        }
                    }
                }
            }
            
            // Input area
            ChatInputArea(
                inputText = inputText,
                onInputChange = viewModel::setInputText,
                onSend = viewModel::sendMessage,
                onStop = viewModel::stopGeneration,
                isGenerating = generationState is GenerationState.Generating,
                isLoading = generationState is GenerationState.Loading,
                isEnabled = true  // Always enable keyboard - will show error if no model loaded
            )
        }
    }
}

@Composable
fun MessageBubble(message: Message) {
    val clipboardManager = LocalClipboardManager.current
    var showCopiedSnackbar by remember { mutableStateOf(false) }
    
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (message.isUser) Arrangement.End else Arrangement.Start
    ) {
        Column(
            horizontalAlignment = if (message.isUser) Alignment.End else Alignment.Start
        ) {
            val bubbleColor = if (message.isUser) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.tertiaryContainer
            }
            val contentColor = if (message.isUser) {
                MaterialTheme.colorScheme.onPrimaryContainer
            } else {
                MaterialTheme.colorScheme.onTertiaryContainer
            }
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = bubbleColor,
                modifier = Modifier.widthIn(max = 300.dp)
            ) {
                Column(
                    modifier = Modifier.padding(12.dp)
                ) {
                    val textColor = contentColor
                    val segments = parseMessageContent(message.content)
                    MessageSegmentsContent(
                        segments = segments,
                        textColor = textColor,
                        clipboardManager = clipboardManager
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = formatTimestamp(message.timestamp),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            
            // Copy button for both user and AI messages
            Spacer(modifier = Modifier.height(2.dp))
            val buttonModifier = Modifier
                .size(32.dp)
                .padding(
                    top = 2.dp,
                    start = if (message.isUser) 0.dp else 4.dp,
                    end = if (message.isUser) 4.dp else 0.dp
                )
            IconButton(
                onClick = {
                    clipboardManager.setText(AnnotatedString(message.content))
                    showCopiedSnackbar = true
                },
                modifier = buttonModifier
            ) {
                val iconTint = if (showCopiedSnackbar) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
                Icon(
                    Icons.Default.ContentCopy,
                    contentDescription = "Copy",
                    modifier = Modifier.size(14.dp),
                    tint = iconTint
                )
            }

            // Reset "Copied!" text after 2 seconds
            LaunchedEffect(showCopiedSnackbar) {
                if (showCopiedSnackbar) {
                    delay(2000)
                    showCopiedSnackbar = false
                }
            }
        }
    }
}

@Composable
private fun MessageSegmentsContent(
    segments: List<MessageSegment>,
    textColor: Color,
    clipboardManager: ClipboardManager
) {
    val linkColor = MaterialTheme.colorScheme.primary
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        segments.forEach { segment ->
            when (segment) {
                is MessageSegment.Text -> {
                    val normalized = segment.text.trim('\n')
                    if (normalized.isNotBlank()) {
                        FormattedTextBlock(
                            text = normalized,
                            textColor = textColor,
                            linkColor = linkColor
                        )
                    }
                }
                is MessageSegment.Code -> {
                    CodeBlockSegment(
                        code = segment.code.trim('\n'),
                        language = segment.language,
                        clipboardManager = clipboardManager
                    )
                }
            }
        }
    }
}

@Composable
private fun FormattedTextBlock(
    text: String,
    textColor: Color,
    linkColor: Color
) {
    val blocks = remember(text) { parseTextBlocks(text) }
    val uriHandler = LocalUriHandler.current
    Column(
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        blocks.forEach { block ->
            when (block) {
                is TextBlock.Heading -> {
                    val style = when (block.level) {
                        1 -> MaterialTheme.typography.titleLarge
                        2 -> MaterialTheme.typography.titleMedium
                        3 -> MaterialTheme.typography.titleSmall
                        else -> MaterialTheme.typography.bodyMedium
                    }
                    val annotated = remember(block.content, block.level, textColor, linkColor) {
                        buildFormattedAnnotatedString(block.content, style, textColor, linkColor)
                    }
                    ClickableText(
                        text = annotated,
                        style = style,
                        onClick = { offset ->
                            handleAnnotationClick(annotated, offset, uriHandler)
                        }
                    )
                }
                is TextBlock.Paragraph -> {
                    val style = MaterialTheme.typography.bodyMedium
                    val annotated = remember(block.content, textColor, linkColor) {
                        buildFormattedAnnotatedString(block.content, style, textColor, linkColor)
                    }
                    ClickableText(
                        text = annotated,
                        style = style,
                        onClick = { offset ->
                            handleAnnotationClick(annotated, offset, uriHandler)
                        }
                    )
                }
                is TextBlock.BulletList -> {
                    val itemStyle = MaterialTheme.typography.bodyMedium
                    Column(
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        block.items.forEach { item ->
                            Row(
                                verticalAlignment = Alignment.Top
                            ) {
                                Text(
                                    text = "\u2022",
                                    style = itemStyle,
                                    color = textColor
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                val annotated = remember(item, textColor, linkColor) {
                                    buildFormattedAnnotatedString(item, itemStyle, textColor, linkColor)
                                }
                                ClickableText(
                                    modifier = Modifier.weight(1f),
                                    text = annotated,
                                    style = itemStyle,
                                    onClick = { offset ->
                                        handleAnnotationClick(annotated, offset, uriHandler)
                                    }
                                )
                            }
                        }
                    }
                }
                is TextBlock.OrderedList -> {
                    val itemStyle = MaterialTheme.typography.bodyMedium
                    Column(
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        block.items.forEachIndexed { index, item ->
                            val number = block.startIndex + index
                            Row(
                                verticalAlignment = Alignment.Top
                            ) {
                                Text(
                                    text = "$number.",
                                    style = itemStyle,
                                    color = textColor
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                val annotated = remember(item, number, textColor, linkColor) {
                                    buildFormattedAnnotatedString(item, itemStyle, textColor, linkColor)
                                }
                                ClickableText(
                                    modifier = Modifier.weight(1f),
                                    text = annotated,
                                    style = itemStyle,
                                    onClick = { offset ->
                                        handleAnnotationClick(annotated, offset, uriHandler)
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun handleAnnotationClick(
    annotatedString: AnnotatedString,
    offset: Int,
    uriHandler: UriHandler
) {
    annotatedString.getStringAnnotations(tag = LINK_TAG, start = offset, end = offset)
        .firstOrNull()
        ?.let { annotation ->
            uriHandler.openUri(annotation.item)
            return
        }

    annotatedString.getStringAnnotations(tag = EMAIL_TAG, start = offset, end = offset)
        .firstOrNull()
        ?.let { annotation ->
            uriHandler.openUri("mailto:${annotation.item}")
        }
}

private fun buildFormattedAnnotatedString(
    text: String,
    baseStyle: TextStyle,
    textColor: Color,
    linkColor: Color
): AnnotatedString {
    return buildAnnotatedString {
        pushStyle(
            SpanStyle(
                color = textColor,
                fontFamily = baseStyle.fontFamily,
                fontWeight = baseStyle.fontWeight,
                fontStyle = baseStyle.fontStyle,
                fontSize = baseStyle.fontSize,
                letterSpacing = baseStyle.letterSpacing
            )
        )
        val plainText = StringBuilder()
        val codeRanges = mutableListOf<IntRange>()
        val urlRanges = mutableListOf<IntRange>()

        fun appendText(value: String) {
            append(value)
            plainText.append(value)
        }

        fun appendChar(value: Char) {
            append(value)
            plainText.append(value)
        }

        var index = 0
        while (index < text.length) {
            when {
                text.startsWith("**", index) -> {
                    val end = text.indexOf("**", index + 2)
                    if (end != -1) {
                        pushStyle(SpanStyle(fontWeight = FontWeight.Bold))
                        appendText(text.substring(index + 2, end))
                        pop()
                        index = end + 2
                        continue
                    }
                }
                text.startsWith("_", index) -> {
                    val end = text.indexOf('_', index + 1)
                    if (end != -1) {
                        pushStyle(SpanStyle(fontStyle = FontStyle.Italic))
                        appendText(text.substring(index + 1, end))
                        pop()
                        index = end + 1
                        continue
                    }
                }
                text.startsWith("*", index) -> {
                    val end = text.indexOf('*', index + 1)
                    if (end != -1) {
                        pushStyle(SpanStyle(fontStyle = FontStyle.Italic))
                        appendText(text.substring(index + 1, end))
                        pop()
                        index = end + 1
                        continue
                    }
                }
                text.startsWith("`", index) -> {
                    val end = text.indexOf('`', index + 1)
                    if (end != -1) {
                        pushStyle(
                            SpanStyle(
                                fontFamily = FontFamily.Monospace,
                                background = textColor.copy(alpha = 0.1f)
                            )
                        )
                        val codeStart = plainText.length
                        val codeContent = text.substring(index + 1, end)
                        appendText(codeContent)
                        val codeEnd = plainText.length
                        if (codeEnd > codeStart) {
                            codeRanges.add(codeStart until codeEnd)
                        }
                        pop()
                        index = end + 1
                        continue
                    }
                }
            }
            appendChar(text[index])
            index++
        }
        pop()

        val content = plainText.toString()

        urlRegex.findAll(content).forEach { match ->
            val range = match.range
            if (range.isEmpty()) return@forEach
            if (codeRanges.any { it.overlaps(range) }) return@forEach

            val start = range.first
            val end = range.last + 1
            val normalized = if (match.value.startsWith("www.", ignoreCase = true)) {
                "https://${match.value}"
            } else {
                match.value
            }
            addStyle(
                style = SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline),
                start = start,
                end = end
            )
            addStringAnnotation(
                tag = LINK_TAG,
                annotation = normalized,
                start = start,
                end = end
            )
            urlRanges.add(range)
        }

        emailRegex.findAll(content).forEach { match ->
            val range = match.range
            if (range.isEmpty()) return@forEach
            if (codeRanges.any { it.overlaps(range) }) return@forEach
            if (urlRanges.any { it.overlaps(range) }) return@forEach

            val start = range.first
            val end = range.last + 1
            addStyle(
                style = SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline),
                start = start,
                end = end
            )
            addStringAnnotation(
                tag = EMAIL_TAG,
                annotation = match.value,
                start = start,
                end = end
            )
        }
    }
}

@Composable
private fun CodeBlockSegment(
    code: String,
    language: String?,
    clipboardManager: ClipboardManager
) {
    var copied by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(12.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            if (!language.isNullOrBlank()) {
                Text(
                    text = language.uppercase(Locale.getDefault()),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.weight(1f))
            IconButton(
                onClick = {
                    clipboardManager.setText(AnnotatedString(code))
                    copied = true
                }
            ) {
                val iconTint = if (copied) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
                Icon(
                    Icons.Default.ContentCopy,
                    contentDescription = "Copy code",
                    modifier = Modifier.size(16.dp),
                    tint = iconTint
                )
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = code,
            style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
    LaunchedEffect(copied) {
        if (copied) {
            delay(2000)
            copied = false
        }
    }
}

@Composable
fun StreamingMessageBubble(text: String) {
    val clipboardManager = LocalClipboardManager.current
    var showCopiedSnackbar by remember { mutableStateOf(false) }
    
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Start
    ) {
        Column(
            horizontalAlignment = Alignment.Start
        ) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.tertiaryContainer,
                modifier = Modifier.widthIn(max = 300.dp)
            ) {
                Column(
                    modifier = Modifier.padding(12.dp)
                ) {
                    val segments = parseMessageContent(text)
                    MessageSegmentsContent(
                        segments = segments,
                        textColor = MaterialTheme.colorScheme.onTertiaryContainer,
                        clipboardManager = clipboardManager
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
            
            // Copy button for streaming response
            Spacer(modifier = Modifier.height(2.dp))
            IconButton(
                onClick = {
                    clipboardManager.setText(AnnotatedString(text))
                    showCopiedSnackbar = true
                },
                modifier = Modifier
                    .size(32.dp)
                    .padding(top = 2.dp, start = 4.dp)
            ) {
                val iconTint = if (showCopiedSnackbar) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
                Icon(
                    Icons.Default.ContentCopy,
                    contentDescription = "Copy",
                    modifier = Modifier.size(14.dp),
                    tint = iconTint
                )
            }
            
            // Reset "Copied!" text after 2 seconds
            LaunchedEffect(showCopiedSnackbar) {
                if (showCopiedSnackbar) {
                    delay(2000)
                    showCopiedSnackbar = false
                }
            }
        }
    }
}

@Composable
fun ThinkingIndicator() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Start
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.tertiaryContainer,
            modifier = Modifier.widthIn(max = 200.dp)
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onTertiaryContainer
                )
                Text(
                    text = "Thinking...",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onTertiaryContainer
                )
            }
        }
    }
}

@Composable
fun ErrorMessageBubble(errorMessage: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Start
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.errorContainer,
            modifier = Modifier.widthIn(max = 300.dp)
        ) {
            Column(
                modifier = Modifier.padding(12.dp)
            ) {
                Text(
                    text = errorMessage,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }
        }
    }
}

@Composable
fun ChatInputArea(
    inputText: String,
    onInputChange: (String) -> Unit,
    onSend: () -> Unit,
    onStop: () -> Unit,
    isGenerating: Boolean,
    isLoading: Boolean,
    isEnabled: Boolean
) {
    Surface(
        tonalElevation = 3.dp
    ) {
        Column {
            // Show thin progress bar at top when loading or generating
            if (isLoading || isGenerating) {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = inputText,
                    onValueChange = onInputChange,
                    modifier = Modifier.weight(1f),
                    placeholder = { 
                        Text(
                            if (isLoading) "Processing..." 
                            else if (isGenerating) "Generating response..." 
                            else "Type a message..."
                        ) 
                    },
                    enabled = isEnabled && !isGenerating && !isLoading,
                    maxLines = 4
                )
                
                if (isGenerating) {
                    IconButton(onClick = onStop) {
                        Icon(
                            Icons.Default.Stop,
                            contentDescription = "Stop",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                } else if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    IconButton(
                        onClick = onSend,
                        enabled = isEnabled && inputText.isNotBlank()
                    ) {
                        Icon(
                            Icons.Default.Send,
                            contentDescription = "Send",
                            tint = if (isEnabled && inputText.isNotBlank()) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    }
                }
            }
        }
    }
}

private fun formatTimestamp(timestamp: Long): String {
    val sdf = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
    return sdf.format(Date(timestamp))
}
