package com.robopine.telpotest

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Switch
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
    private lateinit var keyTypeSwitch: Switch
    private lateinit var dataEditText: EditText
    private lateinit var statusTextView: TextView
    private lateinit var actionButtons: List<Button>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        deviceManager = NeoDeviceManager(this)
        keyEditText = findViewById(R.id.keyEditText)
        keyTypeSwitch = findViewById(R.id.keyTypeSwitch)
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
                    showStatus(getString(R.string.status_reader_initialized))
                    setActionsEnabled(true)
                }
                .onFailure {
                    showStatus(getString(R.string.status_reader_init_failed, it.message.orEmpty()))
                }
        }

        pollButton.setOnClickListener { runPoll() }
        authenticateButton.setOnClickListener { runAuthenticate() }
        readButton.setOnClickListener { runRead() }
        writeButton.setOnClickListener { runWrite() }
    }

    private fun runPoll() = lifecycleScope.launch {
        runAction(getString(R.string.status_polling_card)) {
            val success = deviceManager.poll()
            if (success) {
                getString(R.string.status_card_detected)
            } else {
                getString(R.string.status_card_not_detected)
            }
        }
    }

    private fun runAuthenticate() = lifecycleScope.launch {
        val key = parseHex(keyEditText.text.toString(), expectedBytes = 6) ?: return@launch
        val keyType = if (keyTypeSwitch.isChecked) KEY_TYPE_B else KEY_TYPE_A
        val keyTypeName = if (keyTypeSwitch.isChecked) {
            getString(R.string.key_type_b)
        } else {
            getString(R.string.key_type_a)
        }

        runAction(getString(R.string.status_authenticating)) {
            val success = withContext(Dispatchers.IO) {
                deviceManager.mifareAuthenticate(MIFARE_BLOCK, keyType, key)
            }
            if (success) {
                getString(R.string.status_card_authenticated_with_key, keyTypeName)
            } else {
                getString(R.string.status_authentication_failed)
            }
        }
    }

    private fun runRead() = lifecycleScope.launch {
        runAction(getString(R.string.status_reading_card)) {
            val data = withContext(Dispatchers.IO) {
                deviceManager.mifareRead(MIFARE_BLOCK, MIFARE_BLOCK_LENGTH)
            }
            if (data == null) {
                getString(R.string.status_card_read_failed)
            } else {
                dataEditText.setText(data.toHexStr())
                getString(R.string.status_card_read_success)
            }
        }
    }

    private fun runWrite() = lifecycleScope.launch {
        val data = parseHex(dataEditText.text.toString()) ?: return@launch
        if (data.isEmpty() || data.size % MIFARE_BLOCK_LENGTH != 0) {
            showStatus(getString(R.string.status_write_data_invalid))
            return@launch
        }

        runAction(getString(R.string.status_writing_card)) {
            val success = withContext(Dispatchers.IO) {
                deviceManager.mifareWrite(MIFARE_BLOCK, data)
            }
            if (success) {
                getString(R.string.status_card_write_success)
            } else {
                getString(R.string.status_card_write_failed)
            }
        }
    }

    private suspend fun runAction(workingMessage: String, action: suspend () -> String) {
        setActionsEnabled(false)
        showStatus(workingMessage)
        val message = runCatching { action() }.getOrElse {
            getString(R.string.status_operation_failed, it.message.orEmpty())
        }
        showStatus(message)
        setActionsEnabled(true)
    }

    private fun parseHex(value: String, expectedBytes: Int? = null): ByteArray? {
        val cleanValue = value.filterNot { it.isWhitespace() }.uppercase()
        if (cleanValue.isEmpty() || cleanValue.length % 2 != 0 || !cleanValue.matches(HEX_REGEX)) {
            showStatus(getString(R.string.status_invalid_hex))
            return null
        }

        val bytes = cleanValue.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        if (expectedBytes != null && bytes.size != expectedBytes) {
            showStatus(
                getString(
                    R.string.status_key_length_invalid,
                    expectedBytes,
                    expectedBytes * 2
                )
            )
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
        private const val KEY_TYPE_B: Byte = 0x02
        private const val MIFARE_BLOCK_LENGTH = 16
        private val HEX_REGEX = Regex("^[0-9A-F]+$")
    }
}
