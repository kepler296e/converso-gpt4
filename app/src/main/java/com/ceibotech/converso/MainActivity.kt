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
import android.view.MenuItem
import android.widget.Toast
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.widget.addTextChangedListener
import com.ceibotech.converso.databinding.ActivityMainBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.ktx.auth
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ktx.database
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

    // Text to Speech
    private lateinit var textToSpeech: TextToSpeech

    // OpenAI and OkHttp
    private lateinit var okHttpClient: OkHttpClient
    private lateinit var chatAdapter: MessageAdapter
    private val messages: ArrayList<Message> = ArrayList()

    // Database
    private var tokenUsage: Int = 0

    companion object {
        private const val RECORD_AUDIO_PERMISSION_CODE = 1
        private const val OKHTTP_TIMEOUT_SECONDS = 20L
        private const val TOKEN_COST = 0.000002

        // Firebase
        lateinit var auth: FirebaseAuth
        lateinit var database: FirebaseDatabase
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        if (savedInstanceState == null) {

            auth = Firebase.auth
            database = Firebase.database

            // go to sign in if user is not logged in
            if (auth.currentUser == null) {
                startActivity(Intent(this, AuthActivity::class.java))
                finish()
            }

            val userRef = database.getReference("users/${auth.currentUser?.uid}/token_usage")
            userRef.get()
                .addOnSuccessListener {
                    if (it.exists()) {
                        tokenUsage = it.getValue(Int::class.java)!! // if token_usage exists, it must be an Int
                        updateUsageTextView()
                    } else {
                        // create user in database
                        tokenUsage = 0
                        userRef.setValue(tokenUsage)
                        updateUsageTextView()
                    }
                }
                .addOnFailureListener {
                    Toast.makeText(this, it.message, Toast.LENGTH_LONG).show()
                }
        }

        // fill user data
        binding.emailTextView.text = auth.currentUser?.email
        updateUsageTextView() // tokenUsage could be restored from savedInstanceState to save database reads
        binding.signOutTextView.setOnClickListener { signOut() }

        // drawer layout
        toggle = ActionBarDrawerToggle(this, binding.drawerLayout, R.string.open, R.string.close)
        binding.drawerLayout.addDrawerListener(toggle)
        toggle.syncState()
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        // okhttp
        okHttpClient = OkHttpClient.Builder()
            .connectTimeout(OKHTTP_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(OKHTTP_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .writeTimeout(OKHTTP_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .build()

        // user message
        var isUserTyping = false
        binding.messageEditText.addTextChangedListener { editable ->
            isUserTyping = editable.toString().isNotEmpty()
            if (isUserTyping) {
                binding.micButton.setImageResource(R.drawable.ic_send_24)
            } else {
                binding.micButton.setImageResource(R.drawable.ic_mic_24)
            }
        }
        binding.micButton.setOnClickListener {
            if (isUserTyping) {
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
        val recognitionListener = object : RecognitionListener {
            override fun onReadyForSpeech(p0: Bundle?) {
                binding.micButton.background = ContextCompat.getDrawable(this@MainActivity, R.drawable.bg_rounded_64_red)
            }

            override fun onBeginningOfSpeech() {}

            override fun onRmsChanged(p0: Float) {
                // resize micButton given rms value
                val scale = p0 / 2
                if (scale < 1 || scale > 4) return
                binding.micButton.scaleX = scale
                binding.micButton.scaleY = scale

            }

            override fun onBufferReceived(p0: ByteArray?) {}

            override fun onEndOfSpeech() {
                binding.micButton.background = ContextCompat.getDrawable(this@MainActivity, R.drawable.bg_rounded_64_blue)
                // restore micButton size
                binding.micButton.scaleX = 1f
                binding.micButton.scaleY = 1f
            }

            override fun onError(p0: Int) {}

            override fun onResults(p0: Bundle?) {
                val result = p0?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (result != null) binding.messageEditText.setText(result[0])
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
                    Toast.makeText(this, "Text to speech language not supported", Toast.LENGTH_LONG).show()
                }
            } else {
                Toast.makeText(this, "Text to speech initialization failed", Toast.LENGTH_LONG).show()
            }
        }

        // chat recycler view
        chatAdapter = MessageAdapter(messages)
        binding.chatRecyclerView.adapter = chatAdapter

        // clear button
        binding.clearButton.setOnClickListener {
            messages.clear()
            binding.chatRecyclerView.removeAllViews()
        }
    }

    private fun updateUsageTextView() {
        binding.tokenUsageTextView.text = getString(R.string.token_usage, tokenUsage, tokenUsage * TOKEN_COST)
    }

    private fun addMessageToChatRecyclerView(message: Message) {
        messages.add(message)

        chatAdapter.notifyItemInserted(messages.size - 1)
        binding.chatRecyclerView.scrollToPosition(messages.size - 1)

        // speak message if it's from the assistant
        if (message.role == "assistant" && message.content != "...") {
            textToSpeech.speak(message.content, TextToSpeech.QUEUE_FLUSH, null, null)
        }
    }

    private fun removeLastMessageFromChatRecyclerView() {
        messages.removeLast()
        chatAdapter.notifyItemRemoved(messages.size)
    }

    private fun sendChatToOpenAIAndRetrieveResponse() {
        // assistant typing message "..."
        addMessageToChatRecyclerView(Message("assistant", "..."))

        val headers = Headers.Builder()
            .add("Authorization", "Bearer ${binding.OpenAIAPIKeyEditText.text}")
            .add("Content-Type", "application/json")
            .build()

        // messages without "..." and with the system message as first
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
                // if the request fails, show the error message to the user
                runOnUiThread {
                    removeLastMessageFromChatRecyclerView()
                    Toast.makeText(this@MainActivity, e.message, Toast.LENGTH_LONG).show()
                }
            }

            override fun onResponse(call: Call, response: Response) {
                if (response.isSuccessful) {
                    val responseBody = response.body?.string()
                    val responseJson = Gson().fromJson(responseBody, ResponseJson::class.java)

                    val assistantContent = responseJson.choices[0].message.content
                    tokenUsage += responseJson.usage.total_tokens

                    runOnUiThread {
                        removeLastMessageFromChatRecyclerView()
                        textToSpeech.stop()
                        addMessageToChatRecyclerView(Message("assistant", assistantContent))
                        updateUsageTextView()
                    }

                    // update tokenUsage
                    val userRef = database.getReference("users/${auth.currentUser?.uid}/token_usage")
                    userRef.get()
                        .addOnSuccessListener {
                            userRef.setValue(tokenUsage)
                        }
                        .addOnFailureListener {
                            Log.e("Error updating tokenUsage", it.message!!)
                        }
                } else {
                    // if the response fails, show the error message to the user
                    runOnUiThread {
                        removeLastMessageFromChatRecyclerView()
                        Toast.makeText(this@MainActivity, response.message, Toast.LENGTH_LONG).show()
                    }
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
        startActivity(Intent(this, AuthActivity::class.java))
        finish()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        // save messages and tokenUsage
        outState.putParcelableArrayList("messages", messages)
        outState.putInt("tokenUsage", tokenUsage)
    }

    override fun onRestoreInstanceState(savedInstanceState: Bundle) {
        super.onRestoreInstanceState(savedInstanceState)
        // restore message
        val savedMessages = savedInstanceState.getParcelableArrayList<Message>("messages")
        if (savedMessages != null) {
            messages.addAll(savedMessages)
            chatAdapter.notifyItemRangeInserted(0, messages.size)
        }
        // restore tokenUsage
        tokenUsage = savedInstanceState.getInt("tokenUsage")
        updateUsageTextView()
    }
}