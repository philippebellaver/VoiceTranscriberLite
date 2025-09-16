package com.example.voicetranscriberlite.audio

import android.content.Context
import java.io.File
import java.io.IOException

object Mp3Converter {

    // Caminho do binário LAME incluído na pasta assets/lame
    fun convert(context: Context, wavFile: File, mp3File: File): Boolean {
        try {
            val lameBinary = File(context.filesDir, "lame/lame") // garanta que o binário exista
            if (!lameBinary.exists()) {
                // Copia do assets
                context.assets.open("lame/lame").use { input ->
                    lameBinary.parentFile?.mkdirs()
                    lameBinary.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
            }

            val process = ProcessBuilder(
                lameBinary.absolutePath,
                "-V2",
                wavFile.absolutePath,
                mp3File.absolutePath
            ).redirectErrorStream(true).start()

            val exit = process.waitFor()
            return exit == 0
        } catch (e: IOException) {
            e.printStackTrace()
            return false
        }
    }
}
