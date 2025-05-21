@file:OptIn(ExperimentalMaterial3Api::class)
package com.google.imageclassifierstep

import android.graphics.BitmapFactory
import android.os.Bundle

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent

import androidx.compose.foundation.Image

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults

import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions

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
                // Aquí tu contenido, desplazado automáticamente abajo del AppBar
                Content(Modifier.padding(innerPadding))
            }
        }
    }
}

@Composable
fun Content(modifier: Modifier = Modifier) {

    val context = LocalContext.current
    val bitmap = remember {
        BitmapFactory.decodeResource(context.resources, R.drawable.flower1)
    }
    val traducciones = mapOf(
        "Petal" to "Pétalo",
        "Flower" to "Flor",
        "Plant" to "Planta",
        "Insect" to "Insecto"

    )
    var resultText by remember { mutableStateOf("") }

    Column(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    )  {
        Divider(thickness = 1.dp)
        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Imagen a clasificar",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier
                .padding(bottom = 8.dp)
        )
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = "Imagen a clasificar",
            modifier = Modifier
                .fillMaxWidth()
                .height(250.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = {
            val image = InputImage.fromBitmap(bitmap, 0)
            val labeler = ImageLabeling.getClient(ImageLabelerOptions.DEFAULT_OPTIONS)
            labeler.process(image)
                .addOnSuccessListener { labels ->
                    resultText = labels.joinToString("\n") {
                        val traduccion = traducciones[it.text] ?: it.text
                        "$traduccion: ${(it.confidence * 100).toInt()}%"
                    }
                }
                .addOnFailureListener { e ->
                    resultText = "Error: ${e.localizedMessage}"
                }
        }) {
            Text("Etiquetar")
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text(text = resultText)
    }
}