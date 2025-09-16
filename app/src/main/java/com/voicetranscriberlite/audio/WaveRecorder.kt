package com.example.voicetranscriberlite.audio

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Grava áudio em WAV 16kHz mono 16bit.
 * Envia buffer em tempo real via callback para Vosk.
 */
class WavRecorder(
    private val outputFile: File,
    private val onAudioBuffer: ((buffer: ByteArray, bytesRead: Int) -> Unit)? = null
) {

    private val sampleRate = 16000 // obrigatório para Vosk
    private val channelConfig = AudioFormat.CHANNEL_IN_MONO
    private val audioFormat = AudioFormat.ENCODING_PCM_16BIT

    private var audioRecord: AudioRecord? = null
    private val isRecording = AtomicBoolean(false)
    private val TAG = "WavRecorder"

    fun startRecording() {
        val bufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)
        if (bufferSize == AudioRecord.ERROR || bufferSize == AudioRecord.ERROR_BAD_VALUE) {
            throw IllegalStateException("Configuração de áudio inválida")
        }

        audioRecord = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            sampleRate,
            channelConfig,
            audioFormat,
            bufferSize
        )

        val buffer = ByteArray(bufferSize)
        audioRecord?.startRecording()
        isRecording.set(true)

        Thread {
            try {
                val fos = FileOutputStream(outputFile)
                fos.write(ByteArray(44)) // placeholder header
                var totalAudioLen = 0

                while (isRecording.get()) {
                    val read = audioRecord?.read(buffer, 0, buffer.size) ?: 0
                    if (read > 0) {
                        fos.write(buffer, 0, read)
                        totalAudioLen += read

                        // envia buffer válido para Vosk
                        onAudioBuffer?.invoke(buffer.copyOf(read), read)
                    }
                }

                fos.close()
                writeWavHeader(outputFile, totalAudioLen)
                Log.d(TAG, "Gravação finalizada: ${outputFile.absolutePath}")
            } catch (e: Exception) {
                Log.e(TAG, "Erro na gravação: ${e.message}")
                e.printStackTrace()
            }
        }.start()
    }

    fun stopRecording() {
        isRecording.set(false)
        try {
            audioRecord?.stop()
            audioRecord?.release()
        } catch (_: Exception) {}
        audioRecord = null
    }

    private fun writeWavHeader(file: File, totalAudioLen: Int) {
        val totalDataLen = totalAudioLen + 36
        val byteRate = 16 * sampleRate / 8
        val header = ByteBuffer.allocate(44)
        header.order(ByteOrder.LITTLE_ENDIAN)
        header.put("RIFF".toByteArray())
        header.putInt(totalDataLen)
        header.put("WAVE".toByteArray())
        header.put("fmt ".toByteArray())
        header.putInt(16)
        header.putShort(1) // PCM
        header.putShort(1) // mono
        header.putInt(sampleRate)
        header.putInt(byteRate)
        header.putShort(2) // bloco alinhamento
        header.putShort(16) // bits por sample
        header.put("data".toByteArray())
        header.putInt(totalAudioLen)
        val raf = RandomAccessFile(file, "rw")
        raf.seek(0)
        raf.write(header.array())
        raf.close()
    }
}
