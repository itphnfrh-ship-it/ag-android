package com.cruisepass.verify.data

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import java.lang.reflect.Proxy

/**
 * Hardware barcode engines that live in the terminal firmware.
 * Urovo DT40 exposes android.device.ScanManager.
 * MERTECH MovFast S55 (xcheng) sends com.xcheng.scanner.action.BARCODE_DECODING_BROADCAST
 * and, when the firmware class is present, XcBarcodeScanner.
 */
class HardwareScanner(context: Context) {
    private val appContext = context.applicationContext
    private val scanClass: Class<*>? = runCatching { Class.forName(SCAN_MANAGER) }.getOrNull()
    private val xcClass: Class<*>? = runCatching { Class.forName(XC_SCANNER) }.getOrNull()
    private var manager: Any? = null
    private var receiver: BroadcastReceiver? = null
    private var previousOutputMode: Int? = null
    private var openedByUs = false
    private var xcReady = false

    val available: Boolean = scanClass != null || xcClass != null || looksLikeScannerTerminal(appContext)

    var onResult: ((String) -> Unit)? = null

    fun open() {
        openUrovo()
        openXc()
        ensureReceiver()
    }

    private fun openUrovo() {
        val type = scanClass ?: return
        val scan = manager ?: type.newScanManager() ?: return
        manager = scan
        val alreadyOn = scan.call("getScannerState") as? Boolean == true
        if (!alreadyOn) {
            val turnedOn = scan.call("openScanner") as? Boolean == true
            val nowOn = scan.call("getScannerState") as? Boolean == true
            if (!turnedOn && !nowOn) return
            openedByUs = true
        }
        if (scan.call("getTriggerLockState") as? Boolean == true) {
            scan.call("unlockTrigger")
        }
        scan.call("enableAllSymbologies", arrayOf(Boolean::class.javaPrimitiveType!!), arrayOf(true))
        if (previousOutputMode == null) {
            previousOutputMode = scan.call("getOutputMode") as? Int
        }
        scan.call("switchOutputMode", arrayOf(Int::class.javaPrimitiveType!!), arrayOf(OUTPUT_INTENT))
    }

    private fun openXc() {
        val type = xcClass ?: return
        if (xcReady) return
        val callbackType = runCatching { Class.forName(XC_RESULT) }.getOrNull() ?: return
        val callback = Proxy.newProxyInstance(callbackType.classLoader, arrayOf(callbackType)) { _, method, args ->
            when (method.name) {
                "onResult" -> {
                    val code = args?.firstOrNull() as? String
                    if (!code.isNullOrEmpty()) onResult?.invoke(code)
                    null
                }
                "hashCode" -> 0
                "equals" -> false
                "toString" -> "ScannerResult"
                else -> null
            }
        }
        val started = runCatching {
            type.getMethod("init", Context::class.java, callbackType).invoke(null, appContext, callback)
        }.isSuccess
        xcReady = started
    }

    private fun ensureReceiver() {
        if (receiver != null) return
        if (scanClass == null && !looksLikeMovFast() && xcClass == null) return
        val listener = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val code = intent.barcodeText() ?: return
                onResult?.invoke(code)
            }
        }
        val filter = IntentFilter().apply {
            addAction(scanClass?.constant("ACTION_DECODE") ?: ACTION_DECODE)
            addAction(ACTION_SCAN_RESULT)
            addAction(XC_ACTION)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            appContext.registerReceiver(listener, filter, Context.RECEIVER_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            appContext.registerReceiver(listener, filter)
        }
        receiver = listener
    }

    fun startDecode() {
        if (receiver == null) open()
        manager?.call("startDecode")
        if (xcReady) {
            runCatching { xcClass?.getMethod("startScan")?.invoke(null) }
        }
    }

    fun close() {
        manager?.call("stopDecode")
        if (xcReady) {
            runCatching { xcClass?.getMethod("stopScan")?.invoke(null) }
            runCatching { xcClass?.getMethod("deInit", Context::class.java)?.invoke(null, appContext) }
            xcReady = false
        }
        receiver?.let { listener ->
            runCatching { appContext.unregisterReceiver(listener) }
        }
        receiver = null
        val scan = manager ?: return
        previousOutputMode?.let { mode ->
            scan.call("switchOutputMode", arrayOf(Int::class.javaPrimitiveType!!), arrayOf(mode))
        }
        previousOutputMode = null
        if (openedByUs) {
            scan.call("closeScanner")
            openedByUs = false
        }
    }

    private fun Class<*>.newScanManager(): Any? = runCatching { getDeclaredConstructor().newInstance() }.getOrNull()

    private fun Class<*>.constant(name: String): String? =
        runCatching { getField(name).get(null) as? String }.getOrNull()

    private fun Any.call(name: String, types: Array<Class<*>> = emptyArray(), args: Array<out Any?> = emptyArray()): Any? =
        runCatching { javaClass.getMethod(name, *types).invoke(this, *args) }.getOrNull()

    private fun Intent.barcodeText(): String? {
        val tags = listOf(
            scanClass?.constant("BARCODE_STRING_TAG"),
            "EXTRA_BARCODE_DECODING_DATA",
            "barcode_string",
            "barcode_data",
            "data",
            "value",
            "EXTRA_BARCODE_DECODING_SYMBOL",
        )
        for (tag in tags) {
            if (tag == null) continue
            val text = getStringExtra(tag)
            if (!text.isNullOrEmpty()) return text
        }
        val bytes = getByteArrayExtra(scanClass?.constant("DECODE_DATA_TAG") ?: "barcode") ?: return null
        val declared = getIntExtra(scanClass?.constant("BARCODE_LENGTH_TAG") ?: "length", bytes.size)
        val length = declared.coerceIn(0, bytes.size)
        if (length == 0) return null
        return String(bytes, 0, length, Charsets.UTF_8)
    }

    private companion object {
        const val SCAN_MANAGER = "android.device.ScanManager"
        const val ACTION_DECODE = "android.intent.ACTION_DECODE_DATA"
        const val ACTION_SCAN_RESULT = "android.intent.action.SCANRESULT"
        const val XC_SCANNER = "com.xcheng.scanner.XcBarcodeScanner"
        const val XC_RESULT = "com.xcheng.scanner.ScannerResult"
        const val XC_ACTION = "com.xcheng.scanner.action.BARCODE_DECODING_BROADCAST"
        const val OUTPUT_INTENT = 0

        fun looksLikeMovFast(): Boolean {
            val identity = listOf(Build.MANUFACTURER, Build.BRAND, Build.MODEL, Build.DEVICE, Build.PRODUCT)
            return identity.any { value ->
                value.contains("movfast", ignoreCase = true) ||
                    value.contains("mertech", ignoreCase = true) ||
                    value.contains("xcheng", ignoreCase = true) ||
                    value.equals("s55", ignoreCase = true)
            }
        }

        fun looksLikeScannerTerminal(context: Context): Boolean {
            val identity = listOf(Build.MANUFACTURER, Build.BRAND, Build.MODEL, Build.DEVICE)
            if (identity.any { value ->
                    value.contains("urovo", ignoreCase = true) ||
                        value.contains("dt40", ignoreCase = true) ||
                        value.contains("dt-40", ignoreCase = true)
                }
            ) {
                return true
            }
            if (looksLikeMovFast()) return true
            return context.packageManager.systemAvailableFeatures.any { feature ->
                val name = feature.name ?: return@any false
                name.contains("barcode", ignoreCase = true) || name.contains("scanner", ignoreCase = true)
            }
        }
    }
}
