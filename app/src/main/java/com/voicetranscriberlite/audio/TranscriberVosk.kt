package com.example.voicetranscriberlite.audio

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

/**
 * Classe responsável por carregar o modelo Vosk e processar buffers de áudio.
 * Copia automaticamente o modelo dos assets para a memória interna do app.
 * Usa arquivos .so locais e inicializa o reconhecedor sem crash.
 */
class TranscriberVosk(context: Context) {

    private val modelName = "vosk-model-small-pt-0.3"
    private val modelPath: String = copyModelToInternalStorage(context)
    private val model: Model
    private val recognizer: Recognizer
    private val transcription = StringBuilder()
    private val scope = CoroutineScope(Dispatchers.IO)

    init {
        Log.d("VoskModel", "Inicializando modelo Vosk em: $modelPath")
        model = try {
            Model(modelPath)
        } catch (e: IOException) {
            throw RuntimeException("Não foi possível criar o modelo Vosk", e)
        }
        recognizer = Recognizer(model, 16000f)
        Log.d("VoskModel", "Modelo Vosk carregado com sucesso!")
    }

    /**
     * Processa buffer de áudio PCM e retorna transcrição via callback.
     */
    fun processAudioBuffer(buffer: ByteArray, bytesRead: Int, onUpdate: (String) -> Unit) {
        scope.launch {
            val resultJson = if (recognizer.acceptWaveForm(buffer, bytesRead)) {
                recognizer.result
            } else {
                recognizer.partialResult
            }

            val text = try {
                JSONObject(resultJson).optString("text", "")
            } catch (_: Exception) {
                ""
            }

            if (text.isNotEmpty()) {
                transcription.append(text).append(" ")
                onUpdate(transcription.toString())
            }
        }
    }

    /**
     * Fecha o reconhecedor (libera recursos).
     */
    fun stopTranscription() {
        recognizer.close()
    }

    /**
     * Retorna a transcrição acumulada.
     */
    fun getPartialTranscription(): String = transcription.toString()

    /**
     * Copia o modelo do assets para internal storage (se não existir ainda).
     */
    private fun copyModelToInternalStorage(context: Context): String {
        val outDir = File(context.filesDir, modelName)

        // Se já existe, retorna caminho
        if (outDir.exists() && outDir.list()?.isNotEmpty() == true) {
            Log.d("VoskModel", "Modelo já existe em internal storage")
            return outDir.absolutePath
        }

        Log.d("VoskModel", "Copiando modelo do assets para internal storage...")

        try {
            copyAssetFolder(context, modelName, outDir)
        } catch (e: IOException) {
            throw RuntimeException("Falha ao copiar modelo Vosk do assets", e)
        }

        return outDir.absolutePath
    }

    /**
     * Copia recursivamente uma pasta de assets para internal storage.
     */
    @Throws(IOException::class)
    private fun copyAssetFolder(context: Context, assetFolder: String, outFolder: File) {
        val assetManager = context.assets
        val files = assetManager.list(assetFolder) ?: return

        if (!outFolder.exists()) outFolder.mkdirs()

        for (fileName in files) {
            val assetPath = "$assetFolder/$fileName"
            val outFile = File(outFolder, fileName)
            val subFiles = assetManager.list(assetPath)
            if (subFiles != null && subFiles.isNotEmpty()) {
                // Subpasta
                copyAssetFolder(context, assetPath, outFile)
            } else {
                // Arquivo
                assetManager.open(assetPath).use { input ->
                    FileOutputStream(outFile).use { output ->
                        input.copyTo(output)
                    }
                }
            }
        }
    }
}
