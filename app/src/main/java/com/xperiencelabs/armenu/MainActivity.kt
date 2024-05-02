package com.xperiencelabs.armenu

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.AlertDialog
import androidx.compose.material.Button
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.ProgressIndicatorDefaults
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.ar.core.Config
import com.xperiencelabs.armenu.ui.theme.ARMenuTheme
import com.xperiencelabs.armenu.ui.theme.Translucent
import io.github.sceneview.ar.ARScene
import io.github.sceneview.ar.node.ArModelNode
import io.github.sceneview.ar.node.ArNode
import io.github.sceneview.ar.node.PlacementMode
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONObject
import java.io.IOException
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        checkPermission()
        setContent {
            ARMenuTheme {
                // A surface container using the 'background' color from the theme
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colors.background
                ) {
                    Box(modifier = Modifier.fillMaxSize()){
                        var showDialog by remember { mutableStateOf(false) }
                        var currentModel by remember { mutableStateOf("bmw_m4_f82") }

                        if (showDialog) {
                            ChatGPTDialog(model = currentModel) {
                                showDialog = false
                            }
                        }

                        ARScreen(currentModel)
                        Menu(modifier = Modifier.align(Alignment.BottomCenter)) {
                            currentModel = it
                            showDialog = true
                        }
                    }
                }
            }
        }
    }

    private fun checkPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), 1)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 1 && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_DENIED) {
            // Handle the case where permission is not granted
            Toast.makeText(this, "Permission denied to use the microphone", Toast.LENGTH_SHORT).show()
        }
    }
}

@Composable
fun ChatGPTDialog(model: String, onDismiss: () -> Unit) {
    val modelName = when (model) {
        "bmw_m4_f82" -> "BMW M4 F82"
        "mercedes_benz_g63_amg" -> "Mercedes-Benz G63 AMG"
        "porsche_918_spyder" -> "Porsche 918 Spyder"
        "rolls_royce_ghost" -> "Rolls-Royce Ghost"
        "aston_martin_db11" -> "Aston Martin DB11"
        else -> "Unknown model"
    }

    var text by remember { mutableStateOf("") }
    var response by remember { mutableStateOf("") }
    var isListening by remember { mutableStateOf(false) }
    val scrollState = rememberScrollState()
    var isLoading by remember { mutableStateOf(false) }
    val context = LocalContext.current
    var speechRecognizer by remember { mutableStateOf(SpeechRecognizer.createSpeechRecognizer(context)) }
    val client = remember { OkHttpClient() }
    val speechIntent = remember {
        Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        }
    }

    val tts = remember { mutableStateOf<TextToSpeech?>(null) }
    LaunchedEffect(key1 = context) {
        tts.value = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val result = tts.value?.setLanguage(Locale.US)
                if (result != TextToSpeech.LANG_COUNTRY_AVAILABLE && result != TextToSpeech.LANG_AVAILABLE) {
                    Log.e("TTS", "Language not supported")
                }
            } else {
                Log.e("TTS", "Initialization failed")
            }
        }
    }

    DisposableEffect(key1 = tts.value) {
        onDispose {
            tts.value?.stop()
            tts.value?.shutdown()
        }
    }

    DisposableEffect(key1 = speechRecognizer) {
        val listener = object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                isListening = true
                Log.d("Speech", "onReadyForSpeech")
            }

            override fun onResults(results: Bundle?) {
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                text = matches?.firstOrNull() ?: "No speech input recognized"
                isListening = false
                Log.d("Speech", "onResults: $text")
            }

            override fun onPartialResults(partialResults: Bundle?) {
                val partialText = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                if (!partialText.isNullOrBlank()) text = partialText
                Log.d("Speech", "onPartialResults: $text")
            }

            override fun onEndOfSpeech() {
                isListening = false
                Log.d("Speech", "onEndOfSpeech")
            }

            override fun onError(error: Int) {
                text = "Error occurred: $error"
                isListening = false
                Log.d("Speech", "onError: $error")
            }

            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onBeginningOfSpeech() {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        }
        speechRecognizer.setRecognitionListener(listener)
        onDispose {
            speechRecognizer.destroy()
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(scrollState)
                    .padding(bottom = 100.dp) // Increased padding at the bottom
            ) {
                TextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text("Ask $modelName") },
                    modifier = Modifier.fillMaxWidth()
                )
                if (isListening) {
                    Text("Listening...")
                } else {
                    Row(
                        horizontalArrangement = Arrangement.SpaceEvenly
                    )
                    {
                        Button(onClick = {
                            val permissionStatus = ContextCompat.checkSelfPermission(
                                context,
                                Manifest.permission.RECORD_AUDIO
                            )
                            if (SpeechRecognizer.isRecognitionAvailable(context) && permissionStatus == PackageManager.PERMISSION_GRANTED) {
                                isListening = true
                                speechRecognizer.startListening(speechIntent)
                            } else if (permissionStatus != PackageManager.PERMISSION_GRANTED) {
                                // Permission is not granted, request for permission
                                ActivityCompat.requestPermissions(
                                    context as Activity,
                                    arrayOf(Manifest.permission.RECORD_AUDIO),
                                    1
                                )
                            } else {
                                text = "Speech recognition not available or permission denied"
                            }
                        }) {
                            Text("Speak")
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                    Button(
                        onClick = {
                            tts.value?.speak(
                                response,
                                TextToSpeech.QUEUE_FLUSH,
                                null,
                                null
                            )
                        }
                    ) {
                        Text("Read Aloud")
                    }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(text = response, style = MaterialTheme.typography.body2)
                Spacer(modifier = Modifier.height(32.dp))


                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.CenterHorizontally),
                        color = MaterialTheme.colors.primary,
                        strokeWidth = ProgressIndicatorDefaults.StrokeWidth
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (text.isNotEmpty()) {
                        isLoading = true // Start loading
                        getResponse(client, model, text) { result ->
                            response = result
                            text = ""
                            isLoading = false // End loading
                        }
                    }
                }
            ) {
                Text("Send")
            }
        },
        dismissButton = {
            Button(onDismiss) {
                Text("Close")
            }
        }
    )
}

abstract class RecognitionListenerAdapter : RecognitionListener {
    override fun onReadyForSpeech(params: Bundle?) {}
    override fun onBeginningOfSpeech() {}
    override fun onRmsChanged(rmsdB: Float) {}
    override fun onBufferReceived(buffer: ByteArray?) {}
    override fun onEndOfSpeech() {}
    override fun onError(error: Int) {}
    override fun onPartialResults(partialResults: Bundle?) {}
    override fun onEvent(eventType: Int, params: Bundle?) {}
}

@Composable
fun Menu(modifier: Modifier, onClick: (String) -> Unit) {
    val itemsList = listOf(
        Food("bmw_m4_f82", R.drawable.bmw_m4_f82),
        Food("mercedes_benz_g63_amg", R.drawable.mercedes_benz_g63_amg),
        Food("porsche_918_spyder", R.drawable.porsche_918_spyder),
        Food("rolls_royce_ghost", R.drawable.rolls_royce_ghost),
        Food("aston_martin_db11", R.drawable.aston_martin_db11),
    )

    var currentIndex by remember { mutableStateOf(0) }

    fun updateIndex(offset: Int) {
        currentIndex = (currentIndex + offset + itemsList.size) % itemsList.size
        onClick(itemsList[currentIndex].name)
    }

    Row(modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceAround) {
        IconButton(onClick = { updateIndex(-1) }) {
            Icon(painter = painterResource(id = R.drawable.baseline_arrow_back_ios_24), contentDescription = "previous")
        }

        CircularImage(imageId = itemsList[currentIndex].imageId, onClick = {
            onClick(itemsList[currentIndex].name)
        })

        IconButton(onClick = { updateIndex(1) }) {
            Icon(painter = painterResource(id = R.drawable.baseline_arrow_forward_ios_24), contentDescription = "next")
        }
    }
}

@Composable
fun CircularImage(
    modifier: Modifier = Modifier,
    imageId: Int,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .size(140.dp)
            .clip(CircleShape)
            .border(width = 3.dp, color = Translucent, shape = CircleShape)
            .clickable(onClick = onClick)
    ) {
        Image(painter = painterResource(id = imageId), contentDescription = null, modifier = Modifier.size(140.dp), contentScale = ContentScale.FillBounds)
    }
}

@Composable
fun ARScreen(model:String) {
    val nodes = remember {
        mutableListOf<ArNode>()
    }
    val modelNode = remember {
        mutableStateOf<ArModelNode?>(null)
    }
    val placeModelButton = remember {
        mutableStateOf(false)
    }
    Box(modifier = Modifier.fillMaxSize()){
        ARScene(
            modifier = Modifier.fillMaxSize(),
            nodes = nodes,
            planeRenderer = true,
            onCreate = {arSceneView ->
                arSceneView.lightEstimationMode = Config.LightEstimationMode.DISABLED
                arSceneView.planeRenderer.isShadowReceiver = false
                modelNode.value = ArModelNode(arSceneView.engine,PlacementMode.INSTANT).apply {
                    loadModelGlbAsync(
                        glbFileLocation = "models/${model}.glb",
                        scaleToUnits = 0.8f
                    ){

                    }
                    onAnchorChanged = {
                        placeModelButton.value = !isAnchored
                    }
                    onHitResult = {node, hitResult ->
                        placeModelButton.value = node.isTracking
                    }

                }
                nodes.add(modelNode.value!!)
            },
            onSessionCreate = {
                planeRenderer.isVisible = false
            }
        )
        if(placeModelButton.value){
            Button(onClick = {
                modelNode.value?.anchor()
            }, modifier = Modifier.align(Alignment.Center)) {
                Text(text = "Place It")
            }
        }
    }


    LaunchedEffect(key1 = model){
        modelNode.value?.loadModelGlbAsync(
            glbFileLocation = "models/${model}.glb",
            scaleToUnits = 0.8f
        )
        Log.e("errorloading","ERROR LOADING MODEL")
    }

}

fun getResponse(client: OkHttpClient, model: String, query: String, callback: (String) -> Unit) {
    val apiKey = "sk-proj-Amm0azhlgrj1dIUlyN1iT3BlbkFJkQhgtmWKofI9dCW4YPu2"
    val url = "https://api.openai.com/v1/chat/completions"
    val content = when (model) {
        "bmw_m4_f82" -> "The BMW M4 F82, a part of BMW's M series, embodies the essence of precision engineering and performance. With its sleek design and powerful engine, the M4 F82 delivers an exhilarating driving experience, blending agility with raw power. How can I assist you today?"
        "mercedes_benz_g63_amg" -> "The Mercedes-Benz G63 AMG is the epitome of luxury and capability in the SUV world. Renowned for its iconic boxy shape and opulent interior, the G63 AMG combines off-road prowess with unmatched comfort and advanced technology, making it a standout choice for those who demand the best of both worlds. How can I assist you today?"
        "porsche_918_spyder" -> "The Porsche 918 Spyder represents the pinnacle of automotive engineering, blending cutting-edge hybrid technology with blistering performance. With its sleek and aerodynamic design, the 918 Spyder accelerates from 0 to 60 mph in just a few seconds, delivering an electrifying driving experience that pushes the boundaries of what's possible. How can I assist you today?"
        "rolls_royce_ghost" -> "The Rolls-Royce Ghost is the epitome of luxury and refinement, exuding elegance and sophistication in every detail. Crafted with the finest materials and exquisite craftsmanship, the Ghost offers a serene and luxurious driving experience, cocooning its occupants in unparalleled comfort and opulence. How can I assist you today?"
        "aston_martin_db11" -> "The Aston Martin DB11 is a masterpiece of automotive design, blending timeless elegance with exhilarating performance. With its sculpted exterior and luxurious interior, the DB11 is a true grand tourer, offering a perfect balance of comfort and performance for those who seek the ultimate driving experience. How can I assist you today?"
        else -> "Unknown model"
    }

    val requestBody = """
        {
        
            "model": "gpt-3.5-turbo",
            "messages": [{"role": "system", 
                          "content": "$content"
                          },
                          {"role": "user", 
                          "content": "$query"
                          }
            ],
            "max_tokens": 500,
            "temperature": 0.5
        }
    """.trimIndent()

    val request = Request.Builder()
        .url(url)
        .addHeader("Content-Type", "application/json")
        .addHeader("Authorization", "Bearer $apiKey")
        .post(requestBody.toRequestBody("application/json".toMediaTypeOrNull()))
        .build()

    client.newCall(request).enqueue(object : Callback {
        override fun onFailure(call: Call, e: IOException) {
            callback("Failed to get response: ${e.message}")
        }

        override fun onResponse(call: Call, response: Response) {
            response.use { resp ->
                val body = resp.body?.string()
                if (body != null) {
                    val jsonObject = JSONObject(body)
                    val jsonArray = jsonObject.getJSONArray("choices")
                    val firstChoice = jsonArray.getJSONObject(0)
                    val message = firstChoice.getJSONObject("message")
                    val textResult = message.getString("content")
                    callback(textResult)
                } else {
                    callback("Received empty response")
                }
            }
        }
    })
}


data class Food(var name:String,var imageId:Int)