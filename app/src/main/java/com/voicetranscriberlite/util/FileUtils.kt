package com.example.voicetranscriberlite.utils

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.widget.Toast
import androidx.core.content.FileProvider
import org.apache.poi.xwpf.usermodel.XWPFDocument
import org.apache.poi.xwpf.usermodel.ParagraphAlignment
import org.apache.poi.xwpf.usermodel.XWPFParagraph
import org.apache.poi.xwpf.usermodel.XWPFRun
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStreamWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Utilitário para salvar e compartilhar arquivos de áudio e texto.
 */
object FileUtils {

    /**
     * Gera um nome de arquivo com base na data/hora.
     */
    private fun generateFileName(prefix: String, extension: String): String {
        val sdf = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
        val timestamp = sdf.format(Date())
        return "${prefix}_${timestamp}.$extension"
    }

    /**
     * Retorna o diretório de saída (Downloads/VoiceTranscriberLite).
     */
    private fun getOutputDir(context: Context): File {
        val downloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val appDir = File(downloads, "VoiceTranscriberLite")
        if (!appDir.exists()) appDir.mkdirs()
        return appDir
    }

    /**
     * Salva texto em TXT.
     */
    fun saveTextAsTxt(context: Context, text: String): File {
        val file = File(getOutputDir(context), generateFileName("Transcription", "txt"))
        OutputStreamWriter(FileOutputStream(file), Charsets.UTF_8).use {
            it.write(text)
        }
        Toast.makeText(context, "Transcrição salva em TXT: ${file.name}", Toast.LENGTH_LONG).show()
        return file
    }

    /**
     * Salva texto em DOCX.
     */
    fun saveTextAsDocx(context: Context, text: String): File {
        val doc = XWPFDocument()
        val paragraph: XWPFParagraph = doc.createParagraph()
        paragraph.alignment = ParagraphAlignment.LEFT
        val run: XWPFRun = paragraph.createRun()
        run.setText(text)

        val file = File(getOutputDir(context), generateFileName("Transcription", "docx"))
        FileOutputStream(file).use { out ->
            doc.write(out)
        }
        Toast.makeText(context, "Transcrição salva em DOCX: ${file.name}", Toast.LENGTH_LONG).show()
        return file
    }

    /**
     * Salva texto em PDF simples.
     */
    fun saveTextAsPdf(context: Context, text: String): File {
        val file = File(getOutputDir(context), generateFileName("Transcription", "pdf"))

        // Criando PDF básico com texto puro
        val pdfDoc = com.itextpdf.text.Document()
        com.itextpdf.text.pdf.PdfWriter.getInstance(pdfDoc, FileOutputStream(file))
        pdfDoc.open()
        pdfDoc.add(com.itextpdf.text.Paragraph(text))
        pdfDoc.close()

        Toast.makeText(context, "Transcrição salva em PDF: ${file.name}", Toast.LENGTH_LONG).show()
        return file
    }

    /**
     * Compartilha um arquivo via Intent.
     */
    fun shareFile(context: Context, file: File) {
        val uri: Uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.provider",
            file
        )

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = when (file.extension.lowercase(Locale.getDefault())) {
                "txt" -> "text/plain"
                "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
                "pdf" -> "application/pdf"
                "wav" -> "audio/wav"
                "mp3" -> "audio/mpeg"
                else -> "*/*"
            }
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        context.startActivity(Intent.createChooser(intent, "Compartilhar arquivo"))
    }
}
