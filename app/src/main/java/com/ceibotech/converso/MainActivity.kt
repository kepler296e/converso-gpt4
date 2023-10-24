package com.ceibotech.converso

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.widget.addTextChangedListener
import com.ceibotech.converso.databinding.ActivityMainBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import com.google.gson.Gson
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Headers
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var toggle: ActionBarDrawerToggle

    // Speech to Text
    private lateinit var speechRecognizer: SpeechRecognizer
    private lateinit var recognitionListener: RecognitionListener

    // Text to Speech
    private lateinit var textToSpeech: TextToSpeech

    // OpenAI and OkHttp
    private lateinit var okHttpClient: OkHttpClient
    private lateinit var chatAdapter: ChatAdapter
    private val messages: ArrayList<Message> = ArrayList()

    // Mic or typing
    private var isTyping = false

    companion object {
        private const val RECORD_AUDIO_PERMISSION_CODE = 1
        lateinit var auth: FirebaseAuth
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (savedInstanceState == null) {
            // go to sign in if user is not logged in
            auth = Firebase.auth
            if (auth.currentUser == null) {
                val intent = Intent(this, AuthActivity::class.java)
                startActivity(intent)
                finish()
            }
        }

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        toggle = ActionBarDrawerToggle(this, binding.drawerLayout, R.string.open, R.string.close)
        binding.drawerLayout.addDrawerListener(toggle)
        toggle.syncState()

        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        binding.emailTextView.text = auth.currentUser?.email
        val usage = 3141
        val cost = usage * 0.002 / 1000
        binding.usageTextView.text = "Usage: $usage tokens\nCost: $${String.format("%.2f", cost)}"

        binding.signOutTextView.setOnClickListener { signOut() }

        // set okhttp timeout to 20 seconds
        okHttpClient = OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)
            .build()

        /* Mic vs. Typing */
        binding.messageEditText.addTextChangedListener { editable ->
            isTyping = editable.toString().isNotEmpty()
            if (isTyping) {
                binding.micButton.setImageResource(R.drawable.ic_send_24)
            } else {
                binding.micButton.setImageResource(R.drawable.ic_mic_24)
            }
        }

        binding.micButton.setOnClickListener {
            if (isTyping) {
                // send user message
                val userContent = binding.messageEditText.text.toString()
                if (userContent.isNotEmpty()) {
                    addMessageToChatRecyclerView(Message("user", userContent))
                    sendChatToOpenAIAndRetrieveResponse()
                    binding.messageEditText.text?.clear()
                }
            } else {
                // check record audio permission
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                    startListening()
                } else {
                    ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), RECORD_AUDIO_PERMISSION_CODE)
                }
            }

        }

        /* Speech to Text */
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
        recognitionListener = object : RecognitionListener {
            override fun onReadyForSpeech(p0: Bundle?) {
                binding.micButton.background = ContextCompat.getDrawable(this@MainActivity, R.drawable.bg_rounded_64_red)
            }

            override fun onBeginningOfSpeech() {}

            override fun onRmsChanged(p0: Float) {
                // change mic button size given the volume of the user's voice
                if (p0 < 2 || p0 > 8) return
                binding.micButton.scaleX = p0 / 2
                binding.micButton.scaleY = p0 / 2

            }

            override fun onBufferReceived(p0: ByteArray?) {}

            override fun onEndOfSpeech() {
                binding.micButton.background = ContextCompat.getDrawable(this@MainActivity, R.drawable.bg_rounded_64_blue)
                // restore microphone button size
                binding.micButton.scaleX = 1f
                binding.micButton.scaleY = 1f
            }

            override fun onError(p0: Int) {}

            override fun onResults(p0: Bundle?) {
                val result = p0?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (result != null) {
                    // set results as message edit text text
                    binding.messageEditText.setText(result[0])
                }
            }

            override fun onPartialResults(p0: Bundle?) {}
            override fun onEvent(p0: Int, p1: Bundle?) {}
        }
        speechRecognizer.setRecognitionListener(recognitionListener)

        /* Text to Speech */
        textToSpeech = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val result = textToSpeech.setLanguage(java.util.Locale.US)
                if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    Log.e("MainActivity", "onCreate: Language not supported")
                }
            } else {
                Log.e("MainActivity", "onCreate: Text to speech initialization failed")
            }
        }

        // chat recycler view
        chatAdapter = ChatAdapter(messages)
        binding.chatRecyclerView.adapter = chatAdapter
    }

    private fun addMessageToChatRecyclerView(message: Message) {
        messages.add(message)

        chatAdapter.notifyItemInserted(messages.size - 1)
        binding.chatRecyclerView.scrollToPosition(messages.size - 1)

        // speak message if assistant
        if (message.role == "assistant" && message.content != "...") {
            textToSpeech.speak(message.content, TextToSpeech.QUEUE_FLUSH, null, null)
        }
    }

    private fun removeLastMessageFromChatRecyclerView() {
        messages.removeLast()
        chatAdapter.notifyItemRemoved(messages.size)
    }

    private fun sendChatToOpenAIAndRetrieveResponse() {
        // typing message
        addMessageToChatRecyclerView(Message("assistant", "..."))

        val headers = Headers.Builder()
            .add("Authorization", "Bearer ${binding.OpenAIAPIKeyEditText.text}")
            .add("Content-Type", "application/json")
            .build()

        // messages without typing message and with system message at first
        val messagesOpenAI = ArrayList(messages)
        val systemContent = binding.customInstructionsEditText.text.toString()
        if (systemContent.isNotEmpty()) {
            messagesOpenAI.add(0, Message("system", systemContent))
        }
        messagesOpenAI.removeLast()

        val requestData = mapOf(
            "model" to "gpt-3.5-turbo",
            "messages" to messagesOpenAI
        )

        val jsonMediaType = "application/json; charset=utf-8".toMediaType()
        val requestBody = Gson().toJson(requestData).toRequestBody(jsonMediaType)

        val request = Request.Builder()
            .url("https://api.openai.com/v1/chat/completions")
            .headers(headers)
            .post(requestBody)
            .build()

        okHttpClient.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                // Handle failure
                e.printStackTrace()
            }

            override fun onResponse(call: Call, response: Response) {
                // Handle response here
                if (response.isSuccessful) {
                    val responseBody = response.body?.string()
                    Log.d("MainActivity", "onResponse: $responseBody")

                    val responseJson = Gson().fromJson(responseBody, ResponseJson::class.java)
                    val responseMessage = responseJson.choices?.get(0)?.message?.content
                    if (responseMessage != null) {
                        runOnUiThread {
                            removeLastMessageFromChatRecyclerView()
                            addMessageToChatRecyclerView(Message("assistant", responseMessage))
                        }
                    }

                } else {
                    println("Request failed with code: ${response.code}")
                    println("Response message: ${response.message}")
                }
            }
        })
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if ((requestCode == RECORD_AUDIO_PERMISSION_CODE) && (grantResults.isNotEmpty()) && (grantResults[0] == PackageManager.PERMISSION_GRANTED)) {
            startListening()
        } else {
            // user wants to speak but denied record audio permission ¯\_(ツ)_/¯
        }
    }

    private fun startListening() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-US")
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        speechRecognizer.startListening(intent)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (toggle.onOptionsItemSelected(item)) {
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onDestroy() {
        super.onDestroy()
        speechRecognizer.destroy()
        textToSpeech.stop()
        textToSpeech.shutdown()
    }

    override fun onPause() {
        super.onPause()
        speechRecognizer.stopListening()
        textToSpeech.stop()
    }

    private fun signOut() {
        auth.signOut()
        val intent = Intent(this, AuthActivity::class.java)
        startActivity(intent)
        finish()
    }
}