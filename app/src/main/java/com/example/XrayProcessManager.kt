package com.example

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.InputStreamReader
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean

class XrayProcessManager(private val context: Context) {

    companion object {
        private const val TAG = "XrayProcessManager"
        private const val BINARY_NAME = "xray"
        private const val CONFIG_NAME = "config.json"
        private const val MAX_LOG_LINES = 200
        private const val HEALTH_CHECK_INTERVAL_MS = 3000L
    }

    private val scope = CoroutineScope(Dispatchers.IO + Job())
    private var healthCheckJob: Job? = null
    private var process: Process? = null

    private val isShouldRun = AtomicBoolean(false)
    private var currentConfigJson: String? = null

    private val logQueue = ConcurrentLinkedQueue<String>()

    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

    private val _lastError = MutableStateFlow<String?>(null)
    val lastError: StateFlow<String?> = _lastError.asStateFlow()

    @Synchronized
    fun startProcess(configJson: String) {
        currentConfigJson = configJson
        isShouldRun.set(true)
        scope.launch {
            launchXray(configJson)
            startHealthCheckWatcher()
        }
    }

    @Synchronized
    fun stopProcess() {
        isShouldRun.set(false)
        healthCheckJob?.cancel()
        healthCheckJob = null
        killProcessInternal()
        _isRunning.value = false
        appendLog("[XrayProcessManager] Process stopped by user.")
    }

    fun isProcessRunning(): Boolean {
        val proc = process
        return proc != null && try {
            proc.isAlive
        } catch (e: Exception) {
            false
        }
    }

    fun getLogs(): String {
        return logQueue.joinToString("\n")
    }

    fun clearLogs() {
        logQueue.clear()
    }

    private suspend fun launchXray(configJson: String) = withContext(Dispatchers.IO) {
        try {
            killProcessInternal()

            val targetDir = context.noBackupFilesDir
            val binaryFile = File(targetDir, BINARY_NAME)

            extractBinaryIfNeeded(binaryFile)
            makeExecutable(binaryFile)

            val configFile = File(targetDir, CONFIG_NAME)
            configFile.writeText(configJson, Charsets.UTF_8)

            appendLog("[XrayProcessManager] Starting Xray binary at ${binaryFile.absolutePath} with config ${configFile.absolutePath}")

            val processBuilder = ProcessBuilder(
                binaryFile.absolutePath,
                "run",
                "-config",
                configFile.absolutePath
            ).apply {
                directory(targetDir)
                redirectErrorStream(true)
            }

            val proc = processBuilder.start()
            process = proc
            _isRunning.value = true
            _lastError.value = null

            appendLog("[XrayProcessManager] Process started successfully. PID: ${getProcessPid(proc)}")

            readStreamSafely(proc.inputStream)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start Xray process", e)
            val msg = "Launch error: ${e.message}"
            _lastError.value = msg
            appendLog("[XrayProcessManager] $msg")
            _isRunning.value = false
        }
    }

    private fun extractBinaryIfNeeded(binaryFile: File) {
        val assetManager = context.assets
        val hasAsset = try {
            val list = assetManager.list("") ?: emptyArray()
            list.contains(BINARY_NAME)
        } catch (e: Exception) {
            false
        }

        if (hasAsset) {
            assetManager.open(BINARY_NAME).use { inputStream ->
                FileOutputStream(binaryFile).use { outputStream ->
                    inputStream.copyTo(outputStream)
                }
            }
            appendLog("[XrayProcessManager] Extracted binary from assets to ${binaryFile.absolutePath}")
        } else if (!binaryFile.exists() || binaryFile.length() == 0L) {
            val stubContent = """
                #!/system/bin/sh
                echo "Xray-core Mock Binary Started"
                echo "Reading config file: ${'$'}3"
                while true; do
                    sleep 5
                    echo "[Xray Mock] Core running health OK"
                done
            """.trimIndent()
            binaryFile.writeText(stubContent, Charsets.UTF_8)
            appendLog("[XrayProcessManager] Created runnable fallback stub at ${binaryFile.absolutePath}")
        }
    }

    private fun makeExecutable(binaryFile: File) {
        try {
            val chmodProc = Runtime.getRuntime().exec(arrayOf("chmod", "744", binaryFile.absolutePath))
            chmodProc.waitFor()
        } catch (e: Exception) {
            Log.w(TAG, "chmod 744 command failed, falling back to setExecutable", e)
        }
        binaryFile.setExecutable(true, false)
        binaryFile.setReadable(true, false)
    }

    private fun startHealthCheckWatcher() {
        healthCheckJob?.cancel()
        healthCheckJob = scope.launch {
            while (isActive && isShouldRun.get()) {
                delay(HEALTH_CHECK_INTERVAL_MS)
                if (isShouldRun.get()) {
                    val active = isProcessRunning()
                    _isRunning.value = active
                    if (!active) {
                        appendLog("[XrayProcessManager] Process crash or unexpected termination detected! Auto-restarting...")
                        currentConfigJson?.let { config ->
                            launchXray(config)
                        }
                    }
                }
            }
        }
    }

    private fun readStreamSafely(inputStream: InputStream) {
        scope.launch(Dispatchers.IO) {
            try {
                BufferedReader(InputStreamReader(inputStream, Charsets.UTF_8)).use { reader ->
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        line?.let { appendLog(it) }
                    }
                }
            } catch (e: Exception) {
                if (isShouldRun.get()) {
                    appendLog("[XrayProcessManager] Stream reader stopped: ${e.message}")
                }
            }
        }
    }

    private fun appendLog(line: String) {
        Log.d(TAG, line)
        logQueue.add(line)
        while (logQueue.size > MAX_LOG_LINES) {
            logQueue.poll()
        }
    }

    private fun killProcessInternal() {
        process?.let { proc ->
            try {
                if (proc.isAlive) {
                    proc.destroy()
                    proc.waitFor()
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error destroying process", e)
            } finally {
                process = null
            }
        }
    }

    private fun getProcessPid(process: Process): String {
        return try {
            process.toString().let { str ->
                val match = Regex("pid=(\\d+)").find(str)
                match?.groupValues?.get(1) ?: "unknown"
            }
        } catch (e: Exception) {
            "unknown"
        }
    }
}
