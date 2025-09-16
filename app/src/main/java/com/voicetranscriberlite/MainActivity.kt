package com.example.voicetranscriberlite

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.example.voicetranscriberlite.ui.theme.AppTheme
import com.example.voicetranscriberlite.ui.theme.VoiceTranscriberScreen

/**
 * MainActivity inicializa o app e carrega a tela de gravação/transcrição.
 * Define propriedade JNA para evitar crashes ao inicializar o Vosk.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {

        // Importante: força JNA a não usar bibliotecas do sistema (evita crash no Vosk)
        System.setProperty("jna.nosys", "true")

        super.onCreate(savedInstanceState)

        setContent {
            AppTheme {
                // Chama a tela principal do app com gravação e transcrição
                VoiceTranscriberScreen(this)
            }
        }
    }
}
