@file:OptIn(ExperimentalMaterial3Api::class)
package com.google.imageclassifierstep

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.animation.Crossfade
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.DocumentsContract
import android.provider.MediaStore
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions
import androidx.compose.material3.HorizontalDivider
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.TranslatorOptions

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            Scaffold(
                topBar = {
                    CenterAlignedTopAppBar(
                        title = { Text("ETIQUETACIÓN") },
                        colors = TopAppBarDefaults.centerAlignedTopAppBarColors()
                    )
                }
            ) { innerPadding ->
                Content(Modifier.padding(innerPadding))
            }
        }
    }
}

@Composable
fun Content(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var mode by remember { mutableStateOf("none") }
    var resultText by remember { mutableStateOf("Presiona el botón para clasificar.") }
    var folderUri by remember { mutableStateOf<Uri?>(null) }
    val contentResolver = context.contentResolver
    val imageUri = remember { mutableStateOf<Uri?>(null) }
    val bitmap = remember(imageUri.value) {
        imageUri.value?.let { uri ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val source = ImageDecoder.createSource(context.contentResolver, uri)
                ImageDecoder.decodeBitmap(source)
            } else {
                @Suppress("DEPRECATION")
                MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
            }
        }
    }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        imageUri.value = uri
        resultText = ""
    }

    val folderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        folderUri = uri
        resultText = "Carpeta seleccionada: ${uri?.path}"
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        AnimatedVisibility(
            visible = bitmap == null,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Text(
                text = "SELECCIONE UNA IMAGEN",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }

        Crossfade(
            targetState = bitmap,
            animationSpec = tween(durationMillis = 600)
        ) { currentBitmap ->
            currentBitmap?.let {
                Image(
                    bitmap = it.asImageBitmap(),
                    contentDescription = "IMAGEN SELECCIONADA",
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(250.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (mode != "folder") {
            Button(onClick = {
                launcher.launch("image/*")
                mode = "image"
            }) {
                Text("IMAGEN")
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (bitmap != null) {
            Spacer(modifier = Modifier.height(8.dp))
            Button(onClick = {
                resultText = "Procesando..."
                val image = InputImage.fromBitmap(bitmap, 0)
                val labeler = ImageLabeling.getClient(ImageLabelerOptions.DEFAULT_OPTIONS)

                val options = TranslatorOptions.Builder()
                    .setSourceLanguage(TranslateLanguage.ENGLISH)
                    .setTargetLanguage(TranslateLanguage.SPANISH)
                    .build()
                val translator = Translation.getClient(options)

                translator.downloadModelIfNeeded()
                    .addOnSuccessListener {
                        labeler.process(image)
                            .addOnSuccessListener { labels ->
                                val traducciones = mutableListOf<String>()
                                var pending = labels.size

                                for (label in labels) {
                                    translator.translate(label.text)
                                        .addOnSuccessListener { traduccion ->
                                            traducciones.add("$traduccion: ${(label.confidence * 100).toInt()}%")
                                            pending--
                                            if (pending == 0) {
                                                resultText = traducciones.joinToString("\n")
                                            }
                                        }
                                        .addOnFailureListener {
                                            traducciones.add("${label.text}: ${(label.confidence * 100).toInt()}%")
                                            pending--
                                            if (pending == 0) {
                                                resultText = traducciones.joinToString("\n")
                                            }
                                        }
                                }
                            }
                            .addOnFailureListener {
                                resultText = "Error al clasificar la imagen."
                            }
                    }
                    .addOnFailureListener {
                        resultText = "No se pudo descargar el modelo de traducción."
                    }
            }) {
                Text("Etiquetar imagen")
            }
        }


        if (bitmap == null) {
            Button(onClick = {
                folderPickerLauncher.launch(null)
                mode = "folder"
            }) {
                Text("Seleccionar carpeta")
            }
        }

        if (folderUri != null) {
            Spacer(modifier = Modifier.height(8.dp))
            Button(onClick = {
                resultText = "Procesando imágenes..."

                val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(
                    folderUri,
                    DocumentsContract.getTreeDocumentId(folderUri)
                )

                val options = TranslatorOptions.Builder()
                    .setSourceLanguage(TranslateLanguage.ENGLISH)
                    .setTargetLanguage(TranslateLanguage.SPANISH)
                    .build()
                val translator = Translation.getClient(options)

                translator.downloadModelIfNeeded()
                    .addOnSuccessListener {
                        val labeler = ImageLabeling.getClient(ImageLabelerOptions.DEFAULT_OPTIONS)
                        val resultados = mutableListOf<String>()

                        val cursor = contentResolver.query(childrenUri, arrayOf(
                            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                            DocumentsContract.Document.COLUMN_MIME_TYPE
                        ), null, null, null)

                        val imagenUris = mutableListOf<Uri>()

                        cursor?.use {
                            while (it.moveToNext()) {
                                val documentId = it.getString(0)
                                val mimeType = it.getString(2)
                                if (mimeType.startsWith("image/")) {
                                    val docUri = DocumentsContract.buildDocumentUriUsingTree(folderUri, documentId)
                                    imagenUris.add(docUri)
                                }
                            }
                        }

                        if (imagenUris.isEmpty()) {
                            resultText = "No se encontraron imágenes."
                            return@addOnSuccessListener
                        }

                        var procesadas = 0
                        for (uri in imagenUris) {
                            try {
                                val inputStream = contentResolver.openInputStream(uri)
                                val bmp = BitmapFactory.decodeStream(inputStream)
                                inputStream?.close()
                                val image = InputImage.fromBitmap(bmp, 0)

                                labeler.process(image)
                                    .addOnSuccessListener { labels ->
                                        val traducciones = mutableListOf<String>()
                                        var pendientes = labels.size
                                        if (pendientes == 0) {
                                            resultados.add("Imagen:\nSin etiquetas")
                                            procesadas++
                                            if (procesadas == imagenUris.size) {
                                                resultText = resultados.joinToString("\n\n")
                                            }
                                        }

                                        for (label in labels) {
                                            translator.translate(label.text)
                                                .addOnSuccessListener { traducido ->
                                                    traducciones.add("$traducido: ${(label.confidence * 100).toInt()}%")
                                                    pendientes--
                                                    if (pendientes == 0) {
                                                        resultados.add("Imagen:\n${traducciones.joinToString("\n")}")
                                                        procesadas++
                                                        if (procesadas == imagenUris.size) {
                                                            resultText = resultados.joinToString("\n\n")
                                                        }
                                                    }
                                                }
                                                .addOnFailureListener {
                                                    traducciones.add("${label.text}: ${(label.confidence * 100).toInt()}%")
                                                    pendientes--
                                                    if (pendientes == 0) {
                                                        resultados.add("Imagen:\n${traducciones.joinToString("\n")}")
                                                        procesadas++
                                                        if (procesadas == imagenUris.size) {
                                                            resultText = resultados.joinToString("\n\n")
                                                        }
                                                    }
                                                }
                                        }
                                    }
                                    .addOnFailureListener {
                                        resultados.add("Imagen: Error al clasificar")
                                        procesadas++
                                        if (procesadas == imagenUris.size) {
                                            resultText = resultados.joinToString("\n\n")
                                        }
                                    }
                            } catch (e: Exception) {
                                resultados.add("Error al abrir una imagen.")
                                procesadas++
                                if (procesadas == imagenUris.size) {
                                    resultText = resultados.joinToString("\n\n")
                                }
                            }
                        }
                    }
                    .addOnFailureListener {
                        resultText = "Error al descargar el modelo de traducción."
                    }
            }) {
                Text("Etiquetar carpeta seleccionada")
            }
        }

        // El botón que quieres que aparezca solo si mode != "none"
        if (mode != "none") {
            Button(onClick = {
                mode = "none"           // Al reiniciar, cambiar modo a none
                imageUri.value = null
                folderUri = null
                resultText = "Presiona el botón para clasificar."
            }) {
                Text("Cambiar selección")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        Crossfade(targetState = resultText) { text ->
            Text(text = text)
        }
    }
}
