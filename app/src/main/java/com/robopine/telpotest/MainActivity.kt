package com.robopine.telpotest

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    private lateinit var deviceManager: NeoDeviceManager
    private lateinit var keyEditText: EditText
    private lateinit var dataEditText: EditText
    private lateinit var statusTextView: TextView
    private lateinit var actionButtons: List<Button>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        deviceManager = NeoDeviceManager(this)
        keyEditText = findViewById(R.id.keyEditText)
        dataEditText = findViewById(R.id.dataEditText)
        statusTextView = findViewById(R.id.statusTextView)

        val pollButton = findViewById<Button>(R.id.pollButton)
        val authenticateButton = findViewById<Button>(R.id.authenticateButton)
        val readButton = findViewById<Button>(R.id.readButton)
        val writeButton = findViewById<Button>(R.id.writeButton)
        actionButtons = listOf(pollButton, authenticateButton, readButton, writeButton)
        setActionsEnabled(false)

        lifecycleScope.launch {
            runCatching { deviceManager.init() }
                .onSuccess {
                    showStatus("Reader initialized. Please place a MIFARE card and press Poll Card.")
                    setActionsEnabled(true)
                }
                .onFailure {
                    showStatus("Reader initialization failed: ${it.message}")
                }
        }

        pollButton.setOnClickListener { runPoll() }
        authenticateButton.setOnClickListener { runAuthenticate() }
        readButton.setOnClickListener { runRead() }
        writeButton.setOnClickListener { runWrite() }
    }

    private fun runPoll() = lifecycleScope.launch {
        runAction("Polling card...") {
            val success = deviceManager.poll()
            if (success) "Card detected." else "No card detected."
        }
    }

    private fun runAuthenticate() = lifecycleScope.launch {
        val key = parseHex(keyEditText.text.toString(), expectedBytes = 6) ?: return@launch
        runAction("Authenticating...") {
            val success = withContext(Dispatchers.IO) {
                deviceManager.mifareAuthenticate(MIFARE_BLOCK, KEY_TYPE_A, key)
            }
            if (success) "Card authenticated." else "Authentication failed."
        }
    }

    private fun runRead() = lifecycleScope.launch {
        runAction("Reading card...") {
            val data = withContext(Dispatchers.IO) {
                deviceManager.mifareRead(MIFARE_BLOCK, MIFARE_BLOCK_LENGTH)
            }
            if (data == null) {
                "Card read failed."
            } else {
                dataEditText.setText(data.toHexStr())
                "Card read successfully."
            }
        }
    }

    private fun runWrite() = lifecycleScope.launch {
        val data = parseHex(dataEditText.text.toString()) ?: return@launch
        if (data.isEmpty() || data.size % MIFARE_BLOCK_LENGTH != 0) {
            showStatus("Write data must be a non-empty hex value in 16-byte blocks.")
            return@launch
        }

        runAction("Writing card...") {
            val success = withContext(Dispatchers.IO) {
                deviceManager.mifareWrite(MIFARE_BLOCK, data)
            }
            if (success) "Card written successfully." else "Card write failed."
        }
    }

    private suspend fun runAction(workingMessage: String, action: suspend () -> String) {
        setActionsEnabled(false)
        showStatus(workingMessage)
        val message = runCatching { action() }.getOrElse { "Operation failed: ${it.message}" }
        showStatus(message)
        setActionsEnabled(true)
    }

    private fun parseHex(value: String, expectedBytes: Int? = null): ByteArray? {
        val cleanValue = value.filterNot { it.isWhitespace() }.uppercase()
        if (cleanValue.isEmpty() || cleanValue.length % 2 != 0 || !cleanValue.matches(HEX_REGEX)) {
            showStatus("Please enter a valid hex value.")
            return null
        }

        val bytes = cleanValue.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        if (expectedBytes != null && bytes.size != expectedBytes) {
            showStatus("Key must be $expectedBytes bytes (${expectedBytes * 2} hex characters).")
            return null
        }

        return bytes
    }

    private fun showStatus(message: String) {
        statusTextView.text = message
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    private fun setActionsEnabled(enabled: Boolean) {
        actionButtons.forEach { it.isEnabled = enabled }
    }

    private companion object {
        private const val MIFARE_BLOCK: Byte = 4
        private const val KEY_TYPE_A: Byte = 0x01
        private const val MIFARE_BLOCK_LENGTH = 16
        private val HEX_REGEX = Regex("^[0-9A-F]+$")
    }
}
