package com.example.gemma4good

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.*
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import androidx.core.content.FileProvider
import com.example.gemma4good.data.ChatMessage
import com.example.gemma4good.ui.ChatState
import com.example.gemma4good.ui.ChatViewModel
import com.example.gemma4good.ui.theme.Gemma4GoodTheme
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import android.net.Uri

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            Gemma4GoodTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    AppNavigation()
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppNavigation(viewModel: ChatViewModel = viewModel()) {
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    var currentScreen by remember { mutableStateOf("Chat") }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                Spacer(Modifier.height(16.dp))
                Text(
                    "Gemma4Good",
                    modifier = Modifier.padding(16.dp),
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold
                )
                Divider()
                NavigationDrawerItem(
                    label = { Text("Chat") },
                    selected = currentScreen == "Chat",
                    onClick = {
                        currentScreen = "Chat"
                        scope.launch { drawerState.close() }
                    },
                    icon = { Icon(Icons.Default.Chat, contentDescription = null) },
                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                )
                NavigationDrawerItem(
                    label = { Text("Arquivos") },
                    selected = currentScreen == "Files",
                    onClick = {
                        currentScreen = "Files"
                        scope.launch { drawerState.close() }
                    },
                    icon = { Icon(Icons.Default.Folder, contentDescription = null) },
                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                )
            }
        }
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("gemma4good") },
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(Icons.Default.Menu, contentDescription = "Menu")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                )
            }
        ) { innerPadding ->
            Box(modifier = Modifier.padding(innerPadding)) {
                when (currentScreen) {
                    "Chat" -> MainScreen(viewModel)
                    "Files" -> FilesScreen(viewModel) {
                        currentScreen = "Chat"
                    }
                }
            }
        }
    }
}

@Composable
fun MainScreen(viewModel: ChatViewModel) {
    val state by viewModel.state.collectAsState()
    val messages by viewModel.messages.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = 8.dp)
    ) {
        when (state) {
            is ChatState.ModelMissing -> DownloadScreen(onDownload = { viewModel.startDownload() })
            is ChatState.Downloading -> LoadingScreen("Baixando modelo Gemma 4 E2B do Hugging Face...\nVerifique suas notificações.")
            is ChatState.LoadingModel -> LoadingScreen("Inicializando o motor Gemma 4...")
            is ChatState.Error -> ErrorScreen((state as ChatState.Error).message) { viewModel.startDownload() }
            else -> ChatScreen(
                messages = messages,
                isGenerating = state is ChatState.Generating,
                onSend = { viewModel.sendMessage(it) },
                onDocumentScanned = { text, path -> viewModel.onDocumentScanned(text, path) }
            )
        }
    }
}

@Composable
fun FilesScreen(viewModel: ChatViewModel, onUseDocument: () -> Unit) {
    val documents = viewModel.getDocumentManager().getDocuments()
    
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp)
    ) {
        item {
            Text("Meus Documentos", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(bottom = 16.dp))
        }
        
        if (documents.isEmpty()) {
            item {
                Text("Nenhum documento encontrado.")
            }
        } else {
            items(documents) { doc ->
                Card(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (doc.imagePath != null) {
                            AsyncImage(
                                model = File(doc.imagePath),
                                contentDescription = null,
                                modifier = Modifier
                                    .size(64.dp)
                                    .clip(RoundedCornerShape(8.dp)),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .size(64.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color.LightGray),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.InsertDriveFile, contentDescription = null, tint = Color.White)
                            }
                        }
                        
                        Spacer(modifier = Modifier.width(16.dp))
                        
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = "ID: ${doc.id}", fontWeight = FontWeight.Bold, maxLines = 1)
                            Text(text = "Status: ${doc.status}", color = if (doc.status == "PENDING") Color.Red else Color.Green)
                            Text(
                                text = doc.extractedText,
                                maxLines = 2,
                                color = Color.Gray,
                                fontSize = 12.sp,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                        
                        Spacer(modifier = Modifier.width(8.dp))
                        
                        IconButton(onClick = {
                            viewModel.deleteDocument(doc.id)
                        }) {
                            Icon(Icons.Default.Delete, contentDescription = "Excluir", tint = Color.Red)
                        }
                        
                        Spacer(modifier = Modifier.width(8.dp))
                        
                        Button(onClick = {
                            viewModel.useDocument(doc.id)
                            onUseDocument()
                        }) {
                            Text("Use it")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ErrorScreen(message: String, onRetry: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(Icons.Default.Info, contentDescription = null, tint = Color.Red, modifier = Modifier.size(64.dp))
        Spacer(modifier = Modifier.height(16.dp))
        Text(text = message, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        Spacer(modifier = Modifier.height(24.dp))
        Button(onClick = onRetry) {
            Text("TENTAR NOVAMENTE")
        }
    }
}

@Composable
fun DownloadScreen(onDownload: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Default.Info,
            contentDescription = null,
            modifier = Modifier.size(80.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = "O cérebro está offline",
            fontSize = 22.sp,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            text = "Para rodar sem internet, precisamos baixar o modelo Gemma 4 Effective 2B (aprox. 2.6GB). Recomendamos usar Wi-Fi.",
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            color = Color.Gray,
            modifier = Modifier.padding(vertical = 16.dp)
        )
        Button(
            onClick = onDownload,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text("BAIXAR GEMMA 4", fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun LoadingScreen(message: String) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        CircularProgressIndicator()
        Spacer(modifier = Modifier.height(16.dp))
        Text(text = message, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
    }
}

@Composable
fun ChatScreen(
    messages: List<ChatMessage>,
    isGenerating: Boolean,
    onSend: (String) -> Unit,
    onDocumentScanned: (String, String?) -> Unit
) {
    var text by remember { mutableStateOf("") }
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    val context = LocalContext.current
    var isListening by remember { mutableStateOf(false) }

    val speechRecognizer = remember { SpeechRecognizer.createSpeechRecognizer(context) }
    val textRecognizer = remember { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }

    // Uri para salvar a foto em alta resolução
    val capturedImageUri = remember {
        val file = File(context.cacheDir, "temp_scan.jpg")
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }
    
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success: Boolean ->
        if (success) {
            val file = File(context.cacheDir, "temp_scan.jpg")
            if (file.exists()) {
                val originalBitmap = BitmapFactory.decodeFile(file.absolutePath)
                if (originalBitmap != null) {
                    val bitmap = enhanceContrast(originalBitmap)
                    val image = InputImage.fromBitmap(bitmap, 0)
                    textRecognizer.process(image)
                        .addOnSuccessListener { visionText ->
                            val extractedText = visionText.text
                            val finalFile = File(context.cacheDir, "scan_${System.currentTimeMillis()}.jpg")
                            try {
                                FileOutputStream(finalFile).use { out ->
                                    originalBitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
                                }
                                onDocumentScanned(extractedText, finalFile.absolutePath)
                            } catch (e: Exception) {
                                onDocumentScanned(extractedText, null)
                            }
                        }
                        .addOnFailureListener { e ->
                            val finalFile = File(context.cacheDir, "scan_${System.currentTimeMillis()}.jpg")
                            try {
                                FileOutputStream(finalFile).use { out ->
                                    originalBitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
                                }
                                onDocumentScanned("", finalFile.absolutePath)
                            } catch (ex: Exception) {}
                        }
                }
            }
        }
    }
    
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            startListening(speechRecognizer, { isListening = it }, { text = it })
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            speechRecognizer.destroy()
        }
    }

    LaunchedEffect(messages.size, if (messages.isNotEmpty()) messages.last().text.length else 0) {
        if (messages.isNotEmpty()) {
            listState.scrollToItem(messages.size - 1)
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 16.dp),
            reverseLayout = false
        ) {
            items(messages) { message ->
                ChatBubble(message)
            }
            if (isGenerating) {
                item {
                    Text("Gemma está pensando...", color = Color.Gray, modifier = Modifier.padding(8.dp))
                }
            }
        }

        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Como posso ajudar hoje?") },
                shape = RoundedCornerShape(24.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            if (text.isBlank()) {
                IconButton(
                    onClick = { cameraLauncher.launch(capturedImageUri) },
                    enabled = !isGenerating,
                    modifier = Modifier.background(MaterialTheme.colorScheme.secondaryContainer, shape = RoundedCornerShape(24.dp))
                ) {
                    Icon(Icons.Default.CameraAlt, contentDescription = "Digitalizar")
                }
                Spacer(modifier = Modifier.width(8.dp))
                IconButton(
                    onClick = {
                        val permissionCheck = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
                        if (permissionCheck == PackageManager.PERMISSION_GRANTED) {
                            if (isListening) {
                                speechRecognizer.stopListening()
                                isListening = false
                            } else {
                                startListening(speechRecognizer, { isListening = it }, { text = it })
                            }
                        } else {
                            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        }
                    },
                    enabled = !isGenerating,
                    modifier = Modifier.background(
                        if (isListening) Color.Red.copy(alpha = 0.2f) else MaterialTheme.colorScheme.secondaryContainer,
                        shape = RoundedCornerShape(24.dp)
                    )
                ) {
                    Icon(Icons.Default.Mic, contentDescription = "Falar", tint = if (isListening) Color.Red else MaterialTheme.colorScheme.onSecondaryContainer)
                }
            } else {
                IconButton(
                    onClick = {
                        onSend(text)
                        text = ""
                    },
                    enabled = !isGenerating && text.isNotBlank(),
                    modifier = Modifier.background(MaterialTheme.colorScheme.primary, shape = RoundedCornerShape(24.dp))
                ) {
                    Icon(Icons.Default.Send, contentDescription = "Enviar", tint = MaterialTheme.colorScheme.onPrimary)
                }
            }
        }
    }
}

@Composable
fun ChatBubble(message: ChatMessage) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        contentAlignment = if (message.isUser) Alignment.CenterEnd else Alignment.CenterStart
    ) {
        Surface(
            color = if (message.isUser) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
            shape = RoundedCornerShape(
                topStart = 16.dp,
                topEnd = 16.dp,
                bottomStart = if (message.isUser) 16.dp else 0.dp,
                bottomEnd = if (message.isUser) 0.dp else 16.dp
            )
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                if (message.imagePath != null) {
                    AsyncImage(
                        model = File(message.imagePath),
                        contentDescription = "Document Thumbnail",
                        modifier = Modifier
                            .fillMaxWidth(0.6f)
                            .height(150.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .padding(bottom = 8.dp),
                        contentScale = ContentScale.Crop
                    )
                }
                Text(
                    text = message.text,
                    color = if (message.isUser) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

private fun startListening(
    speechRecognizer: SpeechRecognizer,
    onStateChanged: (Boolean) -> Unit,
    onResult: (String) -> Unit
) {
    val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE, "pt-BR")
    }

    speechRecognizer.setRecognitionListener(object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) { onStateChanged(true) }
        override fun onBeginningOfSpeech() {}
        override fun onRmsChanged(rmsdB: Float) {}
        override fun onBufferReceived(buffer: ByteArray?) {}
        override fun onEndOfSpeech() { onStateChanged(false) }
        override fun onError(error: Int) { onStateChanged(false) }
        override fun onResults(results: Bundle?) {
            onStateChanged(false)
            val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            if (!matches.isNullOrEmpty()) {
                onResult(matches[0])
            }
        }
        override fun onPartialResults(partialResults: Bundle?) {}
        override fun onEvent(eventType: Int, params: Bundle?) {}
    })

    speechRecognizer.startListening(intent)
}

fun enhanceContrast(src: Bitmap): Bitmap {
    val width = src.width
    val height = src.height
    val dest = Bitmap.createBitmap(width, height, src.config)
    val canvas = Canvas(dest)
    val paint = Paint()
    val cm = ColorMatrix(floatArrayOf(
        2f, 0f, 0f, 0f, -100f,
        0f, 2f, 0f, 0f, -100f,
        0f, 0f, 2f, 0f, -100f,
        0f, 0f, 0f, 1f, 0f
    ))
    val grayMatrix = ColorMatrix()
    grayMatrix.setSaturation(0f)
    cm.postConcat(grayMatrix)
    paint.colorFilter = ColorMatrixColorFilter(cm)
    canvas.drawBitmap(src, 0f, 0f, paint)
    return dest
}
