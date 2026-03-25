package com.example.pscmaster.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.pscmaster.data.entity.Question
import com.example.pscmaster.ui.viewmodel.InputViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManageQuestionsScreen(
    viewModel: InputViewModel,
    onNavigateBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    var searchQuery by remember { mutableStateOf("") }
    var debouncedSearchQuery by remember { mutableStateOf("") }
    
    LaunchedEffect(searchQuery) {
        kotlinx.coroutines.delay(300)
        debouncedSearchQuery = searchQuery
    }

    var selectedIds by remember { mutableStateOf(setOf<Long>()) }
    var questionToDelete by remember { mutableStateOf<Question?>(null) }
    var showBulkDeleteConfirm by remember { mutableStateOf(false) }
    var subjectToRename by remember { mutableStateOf<String?>(null) }
    var newSubjectName by remember { mutableStateOf("") }
    
    val filteredQuestions = uiState.recentQuestions.filter {
        it.questionText.contains(debouncedSearchQuery, ignoreCase = true) ||
        it.subject.contains(debouncedSearchQuery, ignoreCase = true)
    }

    // Single Delete confirmation dialog
    questionToDelete?.let { question ->
        AlertDialog(
            onDismissRequest = { questionToDelete = null },
            title = { Text("Delete Question?", fontWeight = FontWeight.Bold) },
            text = { 
                Text("Are you sure you want to delete this specific question? This action cannot be undone.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteQuestion(question)
                        questionToDelete = null
                        selectedIds = selectedIds - question.id
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) { Text("Delete", fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { questionToDelete = null }) { Text("Cancel") }
            },
            shape = RoundedCornerShape(20.dp)
        )
    }

    // Bulk Delete confirmation dialog
    if (showBulkDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showBulkDeleteConfirm = false },
            icon = { Icon(Icons.Default.DeleteSweep, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("Bulk Delete Questions?", fontWeight = FontWeight.Bold) },
            text = { 
                Text("CAUTION: You are about to delete ${selectedIds.size} selected questions. This action is permanent and cannot be reversed. Are you absolutely sure?")
            },
            confirmButton = {
                Button(
                    onClick = {
                        val toDelete = uiState.recentQuestions.filter { it.id in selectedIds }
                        viewModel.deleteQuestions(toDelete)
                        selectedIds = emptySet()
                        showBulkDeleteConfirm = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("Delete ${selectedIds.size} Items", fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { showBulkDeleteConfirm = false }) { Text("Cancel") }
            },
            shape = RoundedCornerShape(24.dp)
        )
    }

    // Rename Subject Dialog
    subjectToRename?.let { oldSubject ->
        AlertDialog(
            onDismissRequest = { subjectToRename = null },
            title = { Text("Rename Subject", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text("Rename '$oldSubject' across all questions:")
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = newSubjectName,
                        onValueChange = { newSubjectName = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("New subject name") },
                        shape = RoundedCornerShape(12.dp)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newSubjectName.isNotBlank() && newSubjectName != oldSubject) {
                            viewModel.renameSubject(oldSubject, newSubjectName)
                        }
                        subjectToRename = null
                    },
                    enabled = newSubjectName.isNotBlank() && newSubjectName != oldSubject
                ) { Text("Rename All") }
            },
            dismissButton = {
                TextButton(onClick = { subjectToRename = null }) { Text("Cancel") }
            },
            shape = RoundedCornerShape(20.dp)
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    if (selectedIds.isEmpty()) {
                        Column {
                            Text("Manage All", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                            Text(
                                "Total: ${uiState.totalQuestionsCount} Questions", 
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        Text("${selectedIds.size} Selected", fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.primary)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = {
                        if (selectedIds.isNotEmpty()) selectedIds = emptySet() else onNavigateBack()
                    }) {
                        Icon(
                            if (selectedIds.isNotEmpty()) Icons.Default.Close else Icons.AutoMirrored.Filled.ArrowBack, 
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    if (selectedIds.isNotEmpty()) {
                        IconButton(onClick = { showBulkDeleteConfirm = true }) {
                            Icon(Icons.Default.DeleteSweep, contentDescription = "Delete All", tint = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp)
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
                placeholder = { Text("Search questions or subjects...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                shape = RoundedCornerShape(12.dp),
                singleLine = true
            )

            if (filteredQuestions.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            if (searchQuery.isEmpty()) Icons.Default.Search else Icons.Default.Close,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            if (searchQuery.isEmpty()) "No questions added yet." else "No matches found.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(bottom = 24.dp)
                ) {
                    items(
                        items = filteredQuestions,
                        key = { it.id }
                    ) { question ->
                        val isSelected = selectedIds.contains(question.id)
                        Card(
                            modifier = Modifier.fillMaxWidth().animateItem(),
                            shape = RoundedCornerShape(16.dp),
                            elevation = CardDefaults.cardElevation(defaultElevation = if (isSelected) 4.dp else 2.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f) 
                                               else MaterialTheme.colorScheme.surface
                            ),
                            border = if (isSelected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
                            onClick = {
                                if (selectedIds.isNotEmpty()) {
                                    selectedIds = if (isSelected) selectedIds - question.id else selectedIds + question.id
                                }
                            }
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Checkbox(
                                            checked = isSelected,
                                            onCheckedChange = { checked ->
                                                selectedIds = if (checked) selectedIds + question.id else selectedIds - question.id
                                            }
                                        )
                                        Surface(
                                            color = MaterialTheme.colorScheme.secondaryContainer,
                                            shape = RoundedCornerShape(8.dp),
                                            onClick = {
                                                subjectToRename = question.subject
                                                newSubjectName = question.subject
                                            }
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = question.subject,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                                )
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Icon(
                                                    Icons.Default.Edit,
                                                    contentDescription = "Rename Subject",
                                                    modifier = Modifier.size(12.dp),
                                                    tint = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
                                                )
                                            }
                                        }
                                    }
                                    
                                    if (selectedIds.isEmpty()) {
                                        IconButton(
                                            onClick = { questionToDelete = question },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(
                                                Icons.Default.DeleteSweep,
                                                contentDescription = "Delete",
                                                tint = MaterialTheme.colorScheme.error,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                    }
                                }
                                
                                Spacer(modifier = Modifier.height(4.dp))
                                
                                Text(
                                    text = question.questionText,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.padding(start = 24.dp) // Align with checkbox text
                                )
                                
                                Spacer(modifier = Modifier.height(12.dp))
                                
                                Column(modifier = Modifier.padding(start = 24.dp)) {
                                    question.options.forEachIndexed { index, option ->
                                        val isCorrect = index == question.correctIndex
                                        Row(
                                            modifier = Modifier.padding(vertical = 2.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(18.dp)
                                                    .background(
                                                        if (isCorrect) MaterialTheme.colorScheme.primary 
                                                        else MaterialTheme.colorScheme.outlineVariant,
                                                        RoundedCornerShape(4.dp)
                                                    ),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                if (isCorrect) {
                                                    Text("✓", color = Color.White, style = MaterialTheme.typography.labelSmall, fontSize = 10.sp)
                                                }
                                            }
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = option,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = if (isCorrect) MaterialTheme.colorScheme.primary 
                                                        else MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
