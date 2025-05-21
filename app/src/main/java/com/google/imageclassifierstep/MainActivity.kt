@file:OptIn(ExperimentalMaterial3Api::class)
package com.google.imageclassifierstep

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
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
                    TopAppBar(
                        title = { Text("Imagen a clasificar") },
                        colors = TopAppBarDefaults.topAppBarColors()
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
    }

    var resultText by remember { mutableStateOf("Presiona el botón para clasificar.") }

    Column(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        HorizontalDivider(thickness = 1.dp)
        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "FLORES",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        bitmap?.let {
            Image(
                bitmap = it.asImageBitmap(),
                contentDescription = "Imagen a clasificar",
                modifier = Modifier
                    .fillMaxWidth()
                    .height(250.dp)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(onClick = {
            launcher.launch("image/*")
        }) {
            Text("Seleccionar imagen")
        }

        Spacer(modifier = Modifier.height(8.dp))

        Button(onClick = {
            bitmap?.let { bmp ->
                val image = InputImage.fromBitmap(bmp, 0)
                val labeler = ImageLabeling.getClient(ImageLabelerOptions.DEFAULT_OPTIONS)
                labeler.process(image)
                    .addOnSuccessListener { labels ->
                        resultText = labels.joinToString("\n") {
                            "${it.text}: ${(it.confidence * 100).toInt()}%"
                        }
                    }
                    .addOnFailureListener { e ->
                        resultText = "Error: ${e.localizedMessage}"
                    }
            }
        }) {
            Text("Etiquetar")
        }

        Spacer(modifier = Modifier.height(8.dp))

        Button(onClick = {
            bitmap?.let { bmp ->
                resultText = "Procesando..."
                val image = InputImage.fromBitmap(bmp, 0)
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
            }
        }) {
            Text("Etiquetar y traducir")
        }

        Spacer(modifier = Modifier.height(16.dp))
        Text(text = resultText)
    }
}