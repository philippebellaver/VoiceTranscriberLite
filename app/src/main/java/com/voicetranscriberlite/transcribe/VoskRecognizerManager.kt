package com.example.voicetranscriberlite.transcribe

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import org.vosk.Model
import org.vosk.Recognizer
import java.io.*
import java.util.zip.ZipInputStream
import java.util.*

/**
 * Gerencia o modelo Vosk e o reconhecimento de voz.
 * Recebe buffers de áudio em tempo real e retorna texto parcial ou final.
 */
class VoskRecognizerManager(private val context: Context) {

    private var model: Model? = null
    private var recognizer: Recognizer? = null
    private val TAG = "VoskRecognizerManager"

    /**
     * Descompacta um arquivo zip para o diretório alvo
     */
    private fun unzip(zipFile: InputStream, targetDirectory: File) {
        ZipInputStream(BufferedInputStream(zipFile)).use { zis ->
            var ze = zis.nextEntry
            while (ze != null) {
                val file = File(targetDirectory, ze.name)
                if (ze.isDirectory) {
                    file.mkdirs()
                } else {
                    file.parentFile?.mkdirs()
                    FileOutputStream(file).use { fos ->
                        zis.copyTo(fos)
                    }
                }
                ze = zis.nextEntry
            }
        }
    }

    /**
     * Prepara o modelo Vosk (carrega do assets se necessário)
     */
    fun prepareModel(onReady: () -> Unit) {
        Thread {
            try {
                val voskDir = File(context.getExternalFilesDir(null), "vosk")
                if (!voskDir.exists()) voskDir.mkdirs()

                val modelFolder = File(voskDir, "vosk-model-small-pt-0.3")

                // Se o modelo não estiver extraído, descompacta do assets
                if (!File(modelFolder, "am/final.mdl").exists()) {
                    Log.d(TAG, "Extraindo modelo do assets...")
                    context.assets.open("vosk-model-small-pt-0.3.zip").use { input ->
                        unzip(input, voskDir)
                    }
                }

                // Verifica se o arquivo essencial existe
                val finalMdl = File(modelFolder, "am/final.mdl")
                if (!finalMdl.exists()) {
                    Log.e(TAG, "Arquivo essencial do modelo não encontrado: ${finalMdl.absolutePath}")
                    return@Thread
                }

                // Carrega modelo e recognizer
                model = Model(modelFolder.absolutePath)
                recognizer = Recognizer(model, 16000f)

                Handler(Looper.getMainLooper()).post {
                    onReady()
                }
                Log.d(TAG, "Modelo Vosk pronto em: ${modelFolder.absolutePath}")

            } catch (e: Exception) {
                e.printStackTrace()
                Log.e(TAG, "Erro ao carregar modelo Vosk: ${e.message}")
            }
        }.start()
    }

    /**
     * Recebe buffer de áudio PCM16 16kHz mono e retorna texto parcial ou final
     */
    fun processAudioBuffer(buffer: ByteArray, read: Int, onResult: (String) -> Unit) {
        try {
            val accepted = recognizer?.acceptWaveForm(buffer, read) ?: false
            val resultJson = if (accepted) recognizer?.result else recognizer?.partialResult
            resultJson?.let { json ->
                // Extrai apenas o texto do JSON
                val text = Regex("\"text\"\\s*:\\s*\"(.*?)\"").find(json)?.groups?.get(1)?.value ?: ""
                if (text.isNotBlank()) {
                    onResult(text)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Formata a transcrição adicionando pontuação básica e capitalização
     */
    fun formatTranscription(text: String): String {
        if (text.isBlank()) return ""
        var t = text.trim()
        // Primeira letra maiúscula
        t = t.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
        // Pontuação simples final
        if (!t.endsWith(".") && !t.endsWith("?") && !t.endsWith("!")) t += "."
        return t
    }

    /**
     * Encerra o recognizer
     */
    fun stop() {
        try {
            recognizer?.close()
        } catch (_: Exception) {}
    }
}
