package com.example.voicetranscriberlite.ui.theme

import android.Manifest
import android.content.Context
import android.content.Intent
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.voicetranscriberlite.audio.Mp3Converter
import com.example.voicetranscriberlite.transcribe.VoskRecognizerManager
import com.example.voicetranscriberlite.ui.components.AudioWaveformCanvas
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.apache.poi.xwpf.usermodel.XWPFDocument
import java.io.*
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.abs
import com.itextpdf.text.Document
import com.itextpdf.text.Paragraph
import com.itextpdf.text.pdf.PdfWriter

@Composable
fun VoiceTranscriberScreen(context: Context) {
    val scope = rememberCoroutineScope()

    // --- PERMISSÕES ---
    var hasPermission by remember { mutableStateOf(false) }
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { granted -> hasPermission = granted }
    )

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) !=
                android.content.pm.PackageManager.PERMISSION_GRANTED
            ) {
                permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            } else hasPermission = true
        } else hasPermission = true
    }

    // --- TRANSCRIBER ---
    val transcriber = remember { VoskRecognizerManager(context) }
    var modelReady by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        transcriber.prepareModel { modelReady = true }
    }

    // --- ESTADOS ---
    var isRecording by remember { mutableStateOf(false) }
    var isPaused by remember { mutableStateOf(false) }
    var transcription by remember { mutableStateOf("") }
    val transcriptionBuffer = StringBuilder()
    var amplitudes by remember { mutableStateOf(List(50) { 0f }) }
    var lastSavedFile by remember { mutableStateOf<File?>(null) }
    val scrollState = rememberScrollState()

    // --- AUDIO RECORD ---
    val bufferSize = AudioRecord.getMinBufferSize(
        16000, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT
    )

    val audioRecord = remember {
        AudioRecord(
            MediaRecorder.AudioSource.MIC,
            16000,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            bufferSize
        )
    }

    val tempFiles = remember { mutableStateListOf<File>() }

    fun createTempWav(): File {
        val file = File(context.getExternalFilesDir(Environment.DIRECTORY_MUSIC),
            "recording_part_${timestamp()}.wav")
        tempFiles.add(file)
        return file
    }

    // --- PROCESSAMENTO OTIMIZADO ---
    fun processAudioFast(buffer: ByteArray, read: Int) {
        // Atualiza onda de áudio
        val newAmp = buffer.map { abs(it.toInt()) }.average().toFloat()
        amplitudes = amplitudes.drop(1) + newAmp

        // Processa buffer via Vosk
        transcriber.processAudioBuffer(buffer, read) { text ->
            if (text.isNotBlank() && !text.contains("{")) {
                val formattedText = transcriber.formatTranscription(text) // pontuação e espaços
                if (formattedText.isNotEmpty()) {
                    transcriptionBuffer.append(if (transcriptionBuffer.isEmpty()) formattedText else " $formattedText")
                    Handler(Looper.getMainLooper()).post {
                        transcription = transcriptionBuffer.toString()
                        scope.launch { scrollState.animateScrollTo(scrollState.maxValue) }
                    }
                }
            }
        }
    }

    // --- FUNÇÃO TOGGLE RECORD ---
    fun toggleRecording() {
        if (!hasPermission) {
            Toast.makeText(context, "Permissão de gravação necessária", Toast.LENGTH_SHORT).show()
            return
        }
        if (!modelReady) {
            Toast.makeText(context, "Modelo ainda carregando...", Toast.LENGTH_SHORT).show()
            return
        }

        if (!isRecording) {
            isRecording = true
            isPaused = false
            val wavFile = createTempWav()
            audioRecord.startRecording()

            scope.launch(Dispatchers.IO) {
                FileOutputStream(wavFile).use { fos ->
                    val buffer = ByteArray(bufferSize)
                    while (isRecording) {
                        if (!isPaused) {
                            val read = audioRecord.read(buffer, 0, buffer.size)
                            if (read > 0) {
                                fos.write(buffer, 0, read)
                                processAudioFast(buffer, read)
                            }
                        } else Thread.sleep(100)
                    }
                }
            }
        } else {
            isPaused = !isPaused
            Toast.makeText(context, if (isPaused) "Gravação pausada" else "Gravação retomada",
                Toast.LENGTH_SHORT).show()
        }
    }

    // --- FUNÇÃO STOP RECORD ---
    fun stopRecording() {
        if (!isRecording) return
        isRecording = false
        isPaused = false
        try { audioRecord.stop() } catch (_: Exception) {}
        transcriber.stop()
        Toast.makeText(context, "Gravação parada", Toast.LENGTH_SHORT).show()
    }

    // --- FUNÇÕES DE SALVAR ---
    fun saveAudioAsMp3() {
        try {
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            if (!downloadsDir.exists()) downloadsDir.mkdirs()
            val finalWav = File(downloadsDir, "recording_${timestamp()}.wav")
            FileOutputStream(finalWav).use { fos ->
                tempFiles.forEach { temp -> FileInputStream(temp).use { fis -> fis.copyTo(fos) } }
            }

            val mp3File = File(downloadsDir, "recording_${timestamp()}.mp3")
            if (Mp3Converter.convert(context, finalWav, mp3File)) {
                Toast.makeText(context, "Áudio salvo em ${mp3File.absolutePath}", Toast.LENGTH_LONG).show()
                lastSavedFile = mp3File
            }
            tempFiles.clear()
        } catch (e: Exception) { e.printStackTrace() }
    }

    fun saveDocx() {
        try {
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            if (!downloadsDir.exists()) downloadsDir.mkdirs()
            val doc = XWPFDocument()
            val para = doc.createParagraph()
            val run = para.createRun()
            run.setText(transcription.ifEmpty { "Sem transcrição disponível." })
            val file = File(downloadsDir, "transcription_${timestamp()}.docx")
            FileOutputStream(file).use { doc.write(it) }
            Toast.makeText(context, "DOCX salvo em ${file.absolutePath}", Toast.LENGTH_LONG).show()
            lastSavedFile = file
        } catch (e: Exception) { e.printStackTrace() }
    }

    fun savePdf() {
        try {
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            if (!downloadsDir.exists()) downloadsDir.mkdirs()
            val document = Document()
            val file = File(downloadsDir, "transcription_${timestamp()}.pdf")
            PdfWriter.getInstance(document, FileOutputStream(file))
            document.open()
            document.add(Paragraph(transcription.ifEmpty { "Sem transcrição disponível." }))
            document.close()
            Toast.makeText(context, "PDF salvo em ${file.absolutePath}", Toast.LENGTH_LONG).show()
            lastSavedFile = file
        } catch (e: Exception) { e.printStackTrace() }
    }

    fun shareLastFile() {
        val file = lastSavedFile ?: run {
            Toast.makeText(context, "Nenhum arquivo para compartilhar", Toast.LENGTH_SHORT).show()
            return
        }
        val uri = Uri.fromFile(file)
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "*/*"
            putExtra(Intent.EXTRA_STREAM, uri)
        }
        context.startActivity(Intent.createChooser(shareIntent, "Compartilhar via"))
    }

    // --- UI PREMIUM ---
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF121212))
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // --- TRILHA DE ÁUDIO COM GRADIENTE ---
        val gradientBrush = Brush.horizontalGradient(
            colors = listOf(Color(0xFF4CAF50), Color(0xFFFFC107), Color(0xFFF44336))
        )
        AudioWaveformCanvas(
            amplitudes,
            Modifier
                .fillMaxWidth()
                .height(150.dp)
                .background(gradientBrush)
                .border(1.dp, Color.Gray, RoundedCornerShape(8.dp))
                .padding(4.dp)
        )

        Spacer(modifier = Modifier.height(20.dp))

        // --- TEXTO TRANSCRITO ---
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .background(Color.Black.copy(alpha=0.1f), RoundedCornerShape(8.dp))
                .verticalScroll(scrollState)
                .padding(12.dp)
        ) {
            Text(
                text = transcription.ifEmpty { "Transcrição aparecerá aqui..." },
                color = Color.White,
                fontSize = 16.sp,
                lineHeight = 22.sp
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        // --- BOTÕES DE GRAVAÇÃO ---
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            val recordButtonColor by animateColorAsState(
                targetValue = if (!isRecording || isPaused) Color(0xFF4CAF50) else Color(0xFFFFC107)
            )
            Box(
                modifier = Modifier
                    .size(100.dp, 50.dp)
                    .shadow(6.dp, RoundedCornerShape(12.dp))
                    .background(recordButtonColor, RoundedCornerShape(12.dp))
                    .clickable { toggleRecording() },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = when {
                        !isRecording -> "Gravar"
                        isPaused -> "Continuar"
                        else -> "Pausar"
                    },
                    color = Color.Black
                )
            }

            Button(
                onClick = { stopRecording() },
                enabled = isRecording,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF44336)),
                modifier = Modifier
                    .height(50.dp)
                    .weight(1f)
            ) { Text("Parar") }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // --- BOTÕES DE SALVAR ---
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = { saveAudioAsMp3() }) { Text("Salvar Áudio") }
            Button(onClick = { savePdf() }) { Text("Salvar PDF") }
            Button(onClick = { saveDocx() }) { Text("Salvar DOCX") }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // --- BOTÃO DE COMPARTILHAR ---
        Button(
            onClick = { shareLastFile() },
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.Share, contentDescription = "Compartilhar")
            Spacer(modifier = Modifier.width(8.dp))
            Text("Compartilhar")
        }
    }
}

fun timestamp(): String = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
