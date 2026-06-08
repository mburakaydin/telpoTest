package com.robopine.telpotest


import android.util.Log
import android.content.Context
import com.idtechproducts.device.Common
import com.idtechproducts.device.ErrorCode
import com.idtechproducts.device.IDTEMVData
import com.idtechproducts.device.IDTLCDData
import com.idtechproducts.device.IDTMSRData
import com.idtechproducts.device.IDT_NEO2
import com.idtechproducts.device.OnReceiverListener
import com.idtechproducts.device.OnReceiverListenerLCD
import com.idtechproducts.device.OnReceiverListenerPIN
import com.idtechproducts.device.OnReceiverListenerPINRequest
import com.idtechproducts.device.ReaderInfo.DEVICE_TYPE
import com.idtechproducts.device.ResDataStruct
import com.idtechproducts.device.StructConfigParameters
import com.idtechproducts.device.audiojack.tools.FirmwareUpdateTool
import com.idtechproducts.device.audiojack.tools.FirmwareUpdateToolMsg
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

fun ByteArray.toHexStr(): String {
    return this.joinToString("") { "%02X".format(it) }
}

class NeoDeviceManager(
    private val context: Context,
) : OnReceiverListener, OnReceiverListenerPIN, OnReceiverListenerPINRequest, OnReceiverListenerLCD,
    FirmwareUpdateToolMsg
{
    public var device: IDT_NEO2? = null
    val PID_KIOSK_V = 0x4691
    private var neoReaderType: Int = PID_KIOSK_V
    private var fwTool: FirmwareUpdateTool? = null

    private val VID_IDTECH = 0x0ACD
    val TAG = "NeoDeviceManager"
    suspend fun init() {
        val res: Boolean = withContext(Dispatchers.Main) {
            device = IDT_NEO2(
                this@NeoDeviceManager,
                this@NeoDeviceManager,
                this@NeoDeviceManager,
                this@NeoDeviceManager,
                context
            )
            device?.log_setVerboseLoggingEnable(false)

            fwTool = FirmwareUpdateTool(this@NeoDeviceManager, context)

            if (device == null) {
                Log.e(TAG, "Failed to initizalize idtech reader: $neoReaderType")
                return@withContext false
            }
            Log.d(TAG, "Successfully initialized idtech reader: $neoReaderType")


            Log.d(TAG, "SDK version: ${device?.config_getSDKVersion()}")

            if (device?.device_setDeviceType(DEVICE_TYPE.DEVICE_NEO2_USB, neoReaderType) ?: false) {
                device?.device_setNEOGen(3)
            } else {
                Log.e(TAG, "Failed to set device type. Please disconnect first.")
            }

            Log.d(TAG, "Set fwtool")
            device?.setIDT_Device(fwTool)

            true
        }
        Log.d(TAG, "Registering idtech listeners")
        if (device?.device_getDeviceType() != DEVICE_TYPE.DEVICE_NEO2_BT) {
            device?.registerListen()
        }
        Log.e(TAG, "Registered Listener...")
    }

    /**
     * Called when a device is connected.
     */
    override fun deviceConnected() {
        Log.e(TAG,"IDTCallback: ***Idtech USB Device Connected***")
    }

    /**
     * Called when a device is disconnected.
     * If a transaction is ongoing, it cancels the coroutine.
     */
    override fun deviceDisconnected() {
        Log.e(TAG,"IDTCallback: ***Idtech USB Device disconnected***")
    }

    /**
     * Called when EMV transaction data is available.
     * Resumes the coroutine with the result.
     */
    override fun emvTransactionData(emvData: IDTEMVData) {
        Log.d(TAG, "emvTransactionData received")
    }

    /**
     * Called when MSR data is read.
     */
    override fun swipeMSRData(card: IDTMSRData) {
        Log.d(TAG, "MSR data received: ${card.result} ${card.unencryptedTags}")
    }

    /**
     * Called during firmware update progress.
     */
    override fun onReceiveMsgUpdateFirmwareProgress(nProgressValue: Int) {
        Log.d(TAG, "Firmware update progress: $nProgressValue%")
    }

    /**
     * Called after firmware update result is returned.
     */
    override fun onReceiveMsgUpdateFirmwareResult(result: Int) {
        val msg = device?.device_getResponseCodeString(result)
        Log.d(TAG, "Firmware update result: $result - $msg")
    }

    /**
     * Called when PIN pad returns data.
     */
    override fun pinpadData(data: ByteArray?) {
        Log.d(TAG, "PIN pad data received: ${data?.toHexStr()}")
    }

    override fun msgBatteryLow() {
        Log.d(TAG, "Battery low warning received")
    }

    override fun msgToConnectDevice() {
        Log.d(TAG, "Message to connect device received")
    }

    override fun dataInOutMonitor(data: ByteArray?, isIncoming: Boolean) {
        val direction = if (isIncoming)
            "<<<"
        else
            ">>>"
        Log.d("ReaderMessages", "[NEO]:$direction:${data?.toHexStr()}")
    }

    override fun LoadXMLConfigFailureInfo(index: Int, strMessage: String?) {
        Log.e(TAG, "Failed to load XML config: $strMessage")
    }

    override fun ICCNotifyInfo(dataNotify: ByteArray?, strMessage: String?) {
        Log.d(TAG, "ICC notify: $strMessage")
    }

    override fun autoConfigCompleted(profile: StructConfigParameters?) {
        Log.d(TAG, "Auto config completed")
    }

    override fun autoConfigProgress(progressValue: Int) {
        Log.d(TAG, "Auto config progress: $progressValue")
    }

    override fun msgRKICompleted(MACResult: String) {
        Log.d(TAG, "RKI completed: $MACResult")
    }

    override fun msgAudioVolumeAjustFailed() {
        Log.d(TAG, "Audio volume adjust failed")
    }

    override fun lcdDisplay(mode: Int, lines: Array<String>, timeout: Int) {
        Log.d(TAG, "LCD display (mode=$mode): ${lines.joinToString()}")
    }

    override fun lcdData(lcdData: IDTLCDData?) {
        Log.d(TAG, "LCD data received: ${lcdData?.screenName}")
    }

    override fun timeout(errorCode: Int) {
        Log.d(TAG, "timeout received, Error code: 0x${errorCode.toString(16).uppercase()}")
    }

    override fun pinRequest(
        var1: Int,
        var2: ByteArray?,
        var3: ByteArray?,
        var4: Int,
        var5: Int,
        var6: String?
    ) {
        return
    }


    override fun lcdDisplay(
        mode: Int,
        lines: Array<String?>,
        timeout: Int,
        languageCode: ByteArray,
        messageId: Byte
    ) {
        Log.d(TAG, "LCD display (extended, mode=$mode): ${lines.filterNotNull().joinToString()}")
    }

    override fun ctlsEvent(event: Byte, scheme: Byte, data: Byte) {
        Log.d(TAG, "CTLS event received: event=$event, scheme=$scheme, data=$data")

    }
    override fun onReceiveMsgChallengeResult(returnCode: Int, data: ByteArray?) {
        Log.d(TAG, "Challenge result received: $returnCode")
        // Not called for UniPay Firmware update
    }


    suspend fun poll(): ByteArray? {

        val responseData = ResDataStruct()

        device?.icc_passthroughOnICC()

        val result = device?.device_pollForToken(1000, responseData) ?: -1
        if(result != 0) {
            Log.d("POLL", "Poll failed: ${device?.device_getResponseCodeString(result)}")
            delay(500)
            return null
        }

        Log.d("POLL", "Poll success: ${device?.device_getResponseCodeString(result)}")
        Log.d("POLL", "Poll data: ${responseData.resData.toHexStr()}")

        val uid = parseUid(responseData.resData)
        if (uid == null) {
            Log.d("POLL", "UID not found in poll response")
        } else {
            Log.d("POLL", "UID: ${uid.toHexStr()}")
        }

        return uid
    }

    private fun parseUid(data: ByteArray): ByteArray? {
        val dfec0fPayload = extractDfec0fValue(data) ?: return null

        if (dfec0fPayload.size < 5) {
            return null
        }

        val uidLen = dfec0fPayload[4].toInt() and 0xFF
        val uidStart = 5
        val uidEnd = uidStart + uidLen

        if (uidLen <= 0 || uidEnd > dfec0fPayload.size) {
            return null
        }

        return dfec0fPayload.copyOfRange(uidStart, uidEnd)
    }

    private fun extractDfec0fValue(data: ByteArray): ByteArray? {
        val tag0 = 0xDF.toByte()
        val tag1 = 0xEC.toByte()
        val tag2 = 0x0F.toByte()

        for (i in 0 until data.size - 3) {
            if (data[i] == tag0 && data[i + 1] == tag1 && data[i + 2] == tag2) {
                val length = data[i + 3].toInt() and 0xFF
                val valueStart = i + 4
                val valueEnd = valueStart + length

                return if (valueEnd <= data.size) {
                    data.copyOfRange(valueStart, valueEnd)
                } else {
                    null
                }
            }
        }

        return null
    }


    fun mifareAuthenticate(block: Byte, keyType: Byte, key: ByteArray): Boolean {

        var crc: Boolean = true

        var keyTypeNeo: Byte = 0x01
        when (keyType.toInt()) {
            0x01 -> keyTypeNeo = 0x01.toByte()
            0x02 -> keyTypeNeo = 0x02.toByte()
            0x41 -> keyTypeNeo = 0x01.toByte()
            0x42 -> keyTypeNeo = 0x02.toByte()
            else -> keyTypeNeo = 0x01.toByte()
        }


        val dataString = buildString {
            append(String.format("%02X", block))
            append(String.format("%02X", keyTypeNeo))
            key.forEach { append(String.format("%02X", it)) }
        }
        var response = ResDataStruct()

        Log.d("mifareAuthenticate", "Mifare Authenticate Called...  Keytype:${keyType}")
        val ret = device?.device_sendDataCommand("2C06", crc, dataString, response, 1000)
        if (ret != ErrorCode.SUCCESS) {
            Log.d(
                "MifareAuthenticate",
                "Authenticate Failed: ${device?.device_getResponseCodeString(response.statusCode)}"
            )
            return false
        }
        return true
    }

    fun mifareRead(block: Byte, length: Int): ByteArray? {
        var crc: Boolean = true
        var response = ResDataStruct()
        val blockCount = length / 16

        val paramValue: Byte = ((0x30) or (blockCount.toInt() and 0x0F)).toByte()
        val dataString = buildString {
            append(String.format("%02X", paramValue))
            append(String.format("%02X", block))
        }

        Log.d("mifareRead", "Mifare Read Called... dataString: $dataString")

        val ret = device?.device_sendDataCommand("2C07", crc, dataString, response, 1000)
        if (ret != ErrorCode.SUCCESS) {
            Log.e(
                "mifareRead",
                "mifareRead Failed: ${device?.device_getResponseCodeString(response.statusCode)}"
            )
            return null
        }
        Log.d("mifareRead", "Mifare Read Success...${response.statusCode}")

        return response.resData
    }

    fun mifareWrite(block: Byte, data: ByteArray): Boolean {
        var crc: Boolean = true
        var response = ResDataStruct()

        val paramValue: Byte = ((0x30) or ((data.size / 16).toInt() and 0x0F)).toByte()
        val dataString = buildString {
            append(String.format("%02X", paramValue))
            append(String.format("%02X", block))
            append(data.toHexStr())
        }


        Log.d("mifareWrite", "Mifare Read Called... dataString: $dataString")

        val ret = device?.device_sendDataCommand("2C08", crc, dataString, response, 1000)
        if (ret != ErrorCode.SUCCESS) {
            Log.e(
                "mifareWrite",
                "mifareRead Failed: ${device?.device_getResponseCodeString(response.statusCode)}"
            )
            return false
        }
        Log.d("mifareWrite", "Mifare Write Success...${response.statusCode}")

        return true

    }


}
