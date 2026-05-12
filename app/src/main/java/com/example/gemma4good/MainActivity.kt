package com.example.gemma4good

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.gemma4good.ui.ChatState
import com.example.gemma4good.ui.ChatViewModel
import com.example.gemma4good.ui.theme.Gemma4GoodTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            Gemma4GoodTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    MainScreen()
                }
            }
        }
    }
}

@Composable
fun MainScreen(viewModel: ChatViewModel = viewModel()) {
    val state by viewModel.state.collectAsState()
    val messages by viewModel.messages.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(Color(0xFF1A237E), Color(0xFF000000))
                )
            )
    ) {
        // Header de Impacto
        HeaderSection()

        when (state) {
            is ChatState.ModelMissing -> DownloadScreen(onDownload = { viewModel.startDownload() })
            is ChatState.Downloading -> LoadingScreen("Baixando modelo Gemma 4 E2B do Hugging Face...\nVerifique suas notificações.")
            is ChatState.LoadingModel -> LoadingScreen("Inicializando o motor Gemma 4...")
            is ChatState.Error -> ErrorScreen((state as ChatState.Error).message) { viewModel.startDownload() }
            else -> ChatScreen(messages, state is ChatState.Generating) { viewModel.sendMessage(it) }
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
        Text(text = message, color = Color.White, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        Spacer(modifier = Modifier.height(24.dp))
        Button(onClick = onRetry) {
            Text("TENTAR NOVAMENTE")
        }
    }
}

@Composable
fun HeaderSection() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Gemma 4 Good",
            fontSize = 32.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
        Text(
            text = "Impacto Social com Gemma 4",
            fontSize = 14.sp,
            color = Color.LightGray
        )
        Divider(modifier = Modifier.padding(top = 16.dp), color = Color.Gray.copy(alpha = 0.3f))
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
            tint = Color(0xFF4FC3F7)
        )
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = "O cérebro está offline",
            fontSize = 22.sp,
            color = Color.White,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            text = "Para rodar sem internet, precisamos baixar o modelo Gemma 4 Effective 2B (aprox. 2.6GB). Recomendamos usar Wi-Fi.",
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            color = Color.LightGray,
            modifier = Modifier.padding(vertical = 16.dp)
        )
        Button(
            onClick = onDownload,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2979FF)),
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
        CircularProgressIndicator(color = Color(0xFF2979FF))
        Spacer(modifier = Modifier.height(16.dp))
        Text(text = message, color = Color.White, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
    }
}

@Composable
fun ChatScreen(messages: List<Pair<String, Boolean>>, isGenerating: Boolean, onSend: (String) -> Unit) {
    var text by remember { mutableStateOf("") }
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()

    // Auto-scroll para o fim quando o conteúdo da última mensagem muda (streaming)
    LaunchedEffect(messages.size, if (messages.isNotEmpty()) messages.last().first.length else 0) {
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
                ChatBubble(message.first, message.second)
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
            TextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Como posso ajudar hoje?") },
                shape = RoundedCornerShape(24.dp),
                colors = TextFieldDefaults.colors(
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent
                )
            )
            IconButton(
                onClick = {
                    onSend(text)
                    text = ""
                },
                enabled = !isGenerating && text.isNotBlank()
            ) {
                Icon(Icons.Default.Send, contentDescription = null, tint = Color(0xFF2979FF))
            }
        }
    }
}

@Composable
fun ChatBubble(text: String, isUser: Boolean) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        contentAlignment = if (isUser) Alignment.CenterEnd else Alignment.CenterStart
    ) {
        Surface(
            color = if (isUser) Color(0xFF2979FF) else Color(0xFF333333),
            shape = RoundedCornerShape(
                topStart = 16.dp,
                topEnd = 16.dp,
                bottomStart = if (isUser) 16.dp else 0.dp,
                bottomEnd = if (isUser) 0.dp else 16.dp
            )
        ) {
            Text(
                text = text,
                modifier = Modifier.padding(12.dp),
                color = Color.White
            )
        }
    }
}
