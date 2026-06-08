package com.robopine.telpotest

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Switch
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    private lateinit var deviceManager: NeoDeviceManager
    private lateinit var blockEditText: EditText
    private lateinit var keyEditText: EditText
    private lateinit var keyTypeSwitch: Switch
    private lateinit var dataEditText: EditText
    private lateinit var outputEditText: EditText
    private lateinit var statusTextView: TextView
    private lateinit var actionButtons: List<Button>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        deviceManager = NeoDeviceManager(this)
        blockEditText = findViewById(R.id.blockEditText)
        keyEditText = findViewById(R.id.keyEditText)
        keyTypeSwitch = findViewById(R.id.keyTypeSwitch)
        dataEditText = findViewById(R.id.dataEditText)
        outputEditText = findViewById(R.id.outputEditText)
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
            val uid = deviceManager.poll()
            if (uid != null) {
                getString(R.string.status_card_detected_with_uid, uid.toHexStr())
            } else {
                getString(R.string.status_card_not_detected)
            }
        }
    }

    private fun runAuthenticate() = lifecycleScope.launch {
        val block = parseBlock() ?: return@launch
        val key = parseHex(keyEditText.text.toString(), expectedBytes = 6) ?: return@launch
        val keyType = if (keyTypeSwitch.isChecked) KEY_TYPE_B else KEY_TYPE_A
        val keyTypeName = if (keyTypeSwitch.isChecked) {
            getString(R.string.key_type_b)
        } else {
            getString(R.string.key_type_a)
        }

        runAction(getString(R.string.status_authenticating)) {
            val success = withContext(Dispatchers.IO) {
                deviceManager.mifareAuthenticate(block.toByte(), keyType, key)
            }
            if (success) {
                getString(R.string.status_card_authenticated_with_key_and_block, keyTypeName, block)
            } else {
                getString(R.string.status_authentication_failed)
            }
        }
    }

    private fun runRead() = lifecycleScope.launch {
        val block = parseBlock() ?: return@launch
        runAction(getString(R.string.status_reading_block, block)) {
            val data = withContext(Dispatchers.IO) {
                deviceManager.mifareRead(block.toByte(), MIFARE_BLOCK_LENGTH)
            }
            if (data == null) {
                getString(R.string.status_card_read_failed)
            } else {
                dataEditText.setText(data.toHexStr())
                getString(R.string.status_card_read_success_with_block, block)
            }
        }
    }

    private fun runWrite() = lifecycleScope.launch {
        val block = parseBlock() ?: return@launch
        val data = parseHex(dataEditText.text.toString()) ?: return@launch
        if (data.isEmpty() || data.size % MIFARE_BLOCK_LENGTH != 0) {
            showStatus(getString(R.string.status_write_data_invalid))
            return@launch
        }

        runAction(getString(R.string.status_writing_block, block)) {
            val success = withContext(Dispatchers.IO) {
                deviceManager.mifareWrite(block.toByte(), data)
            }
            if (success) {
                getString(R.string.status_card_write_success_with_block, block)
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

    private fun parseBlock(): Int? {
        val block = blockEditText.text.toString().trim().toIntOrNull()
        if (block == null || block !in MIN_MIFARE_BLOCK..MAX_MIFARE_BLOCK) {
            showStatus(getString(R.string.status_invalid_block, MIN_MIFARE_BLOCK, MAX_MIFARE_BLOCK))
            return null
        }

        return block
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
        appendOutput(message)
    }

    private fun appendOutput(message: String) {
        val currentText = outputEditText.text.toString()
        val nextText = if (currentText.isBlank()) {
            message
        } else {
            "$currentText\n$message"
        }
        outputEditText.setText(nextText)
        outputEditText.setSelection(outputEditText.text.length)
    }

    private fun setActionsEnabled(enabled: Boolean) {
        actionButtons.forEach { it.isEnabled = enabled }
    }

    private companion object {
        private const val MIN_MIFARE_BLOCK = 0
        private const val MAX_MIFARE_BLOCK = 255
        private const val KEY_TYPE_A: Byte = 0x01
        private const val KEY_TYPE_B: Byte = 0x02
        private const val MIFARE_BLOCK_LENGTH = 16
        private val HEX_REGEX = Regex("^[0-9A-F]+$")
    }
}
