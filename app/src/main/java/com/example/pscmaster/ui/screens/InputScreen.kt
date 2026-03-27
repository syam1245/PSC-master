package com.example.pscmaster.ui.screens

import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.pscmaster.ui.viewmodel.GeneratedQuestion
import com.example.pscmaster.ui.viewmodel.InputViewModel
import kotlinx.coroutines.launch
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InputScreen(
    viewModel: InputViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToManage: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let { 
            viewModel.importCsv(it)
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            // Permission granted
        } else {
            scope.launch { snackbarHostState.showSnackbar("Microphone permission is required for voice input") }
        }
    }

    val isSpeechAvailable = remember {
        SpeechRecognizer.isRecognitionAvailable(context)
    }

    var speechTarget by remember { mutableStateOf<Int?>(null) }
    val speechRecognizer = remember {
        if (isSpeechAvailable) SpeechRecognizer.createSpeechRecognizer(context) else null
    }
    
    val recognitionListener = remember {
        object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onError(error: Int) {}
            override fun onResults(results: Bundle?) {
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (!matches.isNullOrEmpty()) {
                    val text = matches[0]
                    when (speechTarget) {
                        null -> viewModel.updateQuestionText(text)
                        in 0..3 -> viewModel.updateOption(speechTarget!!, text)
                        100 -> viewModel.updateExplanation(text)
                    }
                }
            }
            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        }
    }

    DisposableEffect(Unit) {
        speechRecognizer?.setRecognitionListener(recognitionListener)
        onDispose { speechRecognizer?.destroy() }
    }

    val startSpeech = { target: Int? ->
        val hasPermission = androidx.core.content.ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.RECORD_AUDIO
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED

        if (!hasPermission) {
            permissionLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
        } else if (isSpeechAvailable && speechRecognizer != null) {
            speechTarget = target
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ml-IN")
                putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak now...")
            }
            try {
                speechRecognizer.startListening(intent)
            } catch (e: Exception) {
                scope.launch { snackbarHostState.showSnackbar("Speech recognizer error: ${e.message}") }
            }
        }
    }

    var expanded by remember { mutableStateOf(false) }
    val pscSubjects = listOf(
        "Kerala History", "Indian History", "Geography", "Constitution", 
        "General Science", "Current Affairs", "Mathematics", "English", "Malayalam"
    )
    val allSubjects = (pscSubjects + uiState.availableSubjects).distinct()
 
    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    LaunchedEffect(uiState.infoMessage) {
        uiState.infoMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearInfo()
        }
    }

    if (uiState.showGenerationDialog) {
        GeneratedQuestionsDialog(
            questions = uiState.generatedQuestions,
            onDismiss = { viewModel.discardGeneratedQuestions() },
            onSave = { indices -> 
                viewModel.saveSelectedQuestions(indices)
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Add Question", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { filePickerLauncher.launch("text/*") }) {
                        Icon(Icons.Default.UploadFile, contentDescription = "Import CSV")
                    }
                    TextButton(onClick = onNavigateToManage) {
                        Icon(Icons.Default.SettingsSuggest, contentDescription = null)
                        Spacer(Modifier.width(4.dp))
                        Text("Manage")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            Surface(
                tonalElevation = 8.dp,
                shadowElevation = 8.dp
            ) {
                Button(
                    onClick = { viewModel.saveQuestion() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                        .height(56.dp),
                    shape = RoundedCornerShape(12.dp),
                    enabled = uiState.questionText.isNotBlank() && uiState.options.all { it.isNotBlank() }
                ) {
                    Icon(Icons.Default.Save, contentDescription = "Save")
                    Spacer(Modifier.width(8.dp))
                    Text("Save Question", style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    ) { padding ->
        val scrollState = rememberScrollState()
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
                .verticalScroll(scrollState)
        ) {
            // AI Generation Card
            Card(
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.3f)
                )
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("AI Smart Generator", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        
                        if (uiState.isGenerating) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        } else {
                            TextButton(
                                onClick = { viewModel.generateAiQuestions() },
                                enabled = uiState.subject.isNotBlank(),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                            ) {
                                Text("Generate 5")
                            }
                        }
                    }
                    Text(
                        "Automatically generate 5 questions based on the subject below.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }

            // Main Content Area
            ExposedDropdownMenuBox(
                expanded = expanded,
                onExpandedChange = { expanded = !expanded },
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
            ) {
                OutlinedTextField(
                    value = uiState.subject,
                    onValueChange = { viewModel.updateSubject(it) },
                    label = { Text("Topic / Subject") },
                    placeholder = { Text("e.g. History") },
                    modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryEditable, true).fillMaxWidth(),
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true
                )
                ExposedDropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false }
                ) {
                    val filteredSubjects = allSubjects.filter { it.contains(uiState.subject, ignoreCase = true) }
                    filteredSubjects.forEach { selectionOption ->
                        DropdownMenuItem(
                            text = { Text(selectionOption) },
                            onClick = {
                                viewModel.updateSubject(selectionOption)
                                expanded = false
                            }
                        )
                    }
                }
            }

            OutlinedTextField(
                value = uiState.questionText,
                onValueChange = { viewModel.updateQuestionText(it) },
                label = { Text("Question Content") },
                placeholder = { Text("Enter question in English...") },
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                trailingIcon = {
                    if (isSpeechAvailable) {
                        IconButton(onClick = { startSpeech(null) }) {
                            Icon(Icons.Default.Mic, contentDescription = "Voice Input", tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                },
                shape = RoundedCornerShape(12.dp),
                minLines = 3
            )

            // Options Area
            Text(
                "Options (Mark the one correct answer)", 
                style = MaterialTheme.typography.labelMedium, 
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 4.dp, bottom = 4.dp)
            )

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    InputOptionItem(
                        index = 0,
                        value = uiState.options[0],
                        isSelected = uiState.correctIndex == 0,
                        onValueChange = { viewModel.updateOption(0, it) },
                        onSelect = { viewModel.updateCorrectIndex(0) },
                        onMicClick = { startSpeech(0) },
                        isSpeechAvailable = isSpeechAvailable,
                        modifier = Modifier.weight(1f)
                    )
                    InputOptionItem(
                        index = 1,
                        value = uiState.options[1],
                        isSelected = uiState.correctIndex == 1,
                        onValueChange = { viewModel.updateOption(1, it) },
                        onSelect = { viewModel.updateCorrectIndex(1) },
                        onMicClick = { startSpeech(1) },
                        isSpeechAvailable = isSpeechAvailable,
                        modifier = Modifier.weight(1f)
                    )
                }
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    InputOptionItem(
                        index = 2,
                        value = uiState.options[2],
                        isSelected = uiState.correctIndex == 2,
                        onValueChange = { viewModel.updateOption(2, it) },
                        onSelect = { viewModel.updateCorrectIndex(2) },
                        onMicClick = { startSpeech(2) },
                        isSpeechAvailable = isSpeechAvailable,
                        modifier = Modifier.weight(1f)
                    )
                    InputOptionItem(
                        index = 3,
                        value = uiState.options[3],
                        isSelected = uiState.correctIndex == 3,
                        onValueChange = { viewModel.updateOption(3, it) },
                        onSelect = { viewModel.updateCorrectIndex(3) },
                        onMicClick = { startSpeech(3) },
                        isSpeechAvailable = isSpeechAvailable,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            OutlinedTextField(
                value = uiState.explanation,
                onValueChange = { viewModel.updateExplanation(it) },
                label = { Text("Explanation (Optional)") },
                placeholder = { Text("Why is this the correct answer?") },
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                trailingIcon = {
                    if (isSpeechAvailable) {
                        IconButton(onClick = { startSpeech(100) }) {
                            Icon(Icons.Default.Mic, contentDescription = "Voice Input", tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                },
                shape = RoundedCornerShape(12.dp),
                minLines = 2
            )

            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
fun InputOptionItem(
    index: Int,
    value: String,
    isSelected: Boolean,
    onValueChange: (String) -> Unit,
    onSelect: () -> Unit,
    onMicClick: () -> Unit,
    isSpeechAvailable: Boolean,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        placeholder = { Text("Option ${index + 1}", style = MaterialTheme.typography.bodySmall) },
        leadingIcon = {
            RadioButton(
                selected = isSelected,
                onClick = onSelect,
                modifier = Modifier.size(24.dp)
            )
        },
        trailingIcon = {
            if (isSpeechAvailable) {
                IconButton(onClick = onMicClick) {
                    Icon(Icons.Default.Mic, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                }
            }
        },
        shape = RoundedCornerShape(12.dp),
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyMedium,
        colors = OutlinedTextFieldDefaults.colors(
            unfocusedContainerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surface,
            focusedContainerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f) else MaterialTheme.colorScheme.surface,
            unfocusedBorderColor = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
        )
    )
}

@Composable
fun GeneratedQuestionsDialog(
    questions: List<GeneratedQuestion>,
    onDismiss: () -> Unit,
    onSave: (Set<Int>) -> Unit
) {
    var selectedIndices by remember { mutableStateOf(questions.indices.toSet()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { 
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text("AI Results")
            }
        },
        text = {
            Box(modifier = Modifier.heightIn(max = 400.dp)) {
                LazyColumn {
                    itemsIndexed(questions) { index, question ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Checkbox(
                                checked = selectedIndices.contains(index),
                                onCheckedChange = { checked ->
                                    selectedIndices = if (checked) selectedIndices + index else selectedIndices - index
                                }
                            )
                            Column(modifier = Modifier.padding(start = 4.dp)) {
                                Text(text = question.question, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                                Text(
                                    text = "Correct: ${question.options.getOrElse(question.correctIndex) { "?" }}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { onSave(selectedIndices) }) {
                Text("Add (${selectedIndices.size})")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Discard")
            }
        }
    )
}
