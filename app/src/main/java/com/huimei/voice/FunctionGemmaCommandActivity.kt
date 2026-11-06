package com.huimei.voice

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.DialogInterface
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.SystemClock
import android.view.View
import android.widget.EditText
import android.widget.RadioGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.huimei.voice.functiongemma.FunctionGemmaDeviceSupport
import com.huimei.voice.functiongemma.FunctionGemmaEngine
import com.huimei.voice.functiongemma.FunctionGemmaModelDownloader
import com.huimei.voice.functiongemma.FunctionGemmaModelManifest
import com.huimei.voice.functiongemma.FunctionGemmaModelStore
import com.huimei.voice.functiongemma.HybridCommandResult
import com.huimei.voice.functiongemma.HybridCommandRouter
import com.huimei.voice.functiongemma.HybridVoiceCommandController
import com.huimei.voice.functiongemma.HybridVoiceCommandListener
import com.huimei.voice.functiongemma.HybridVoiceState
import com.huimei.voice.functiongemma.LatestOperationGate
import com.huimei.voice.functiongemma.LiteRtFunctionGemmaEngine
import com.huimei.voice.model.VoiceLanguage
import com.huimei.voice.recognition.TimeSource
import java.io.InputStream
import java.net.URL
import java.util.Date
import kotlin.math.min

class FunctionGemmaCommandActivity : AppCompatActivity(), HybridVoiceCommandListener {
    private val worker: java.util.concurrent.ExecutorService =
        java.util.concurrent.Executors.newSingleThreadExecutor()
    private val operationGate: LatestOperationGate = LatestOperationGate()
    private val logTime = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.ROOT)

    private lateinit var modelStore: FunctionGemmaModelStore
    private var engine: FunctionGemmaEngine? = null
    private var router: HybridCommandRouter? = null
    private var voiceController: HybridVoiceCommandController? = null
    private var selectedLanguage: VoiceLanguage = VoiceLanguage.CHINESE

    @kotlin.concurrent.Volatile
    private var engineReady = false
    private var busy = false
    private var listening = false
    private var hasLogLines = false

    private lateinit var deviceStatus: TextView
    private lateinit var modelStatus: TextView
    private lateinit var status: TextView
    private lateinit var progress: TextView
    private lateinit var log: TextView
    private lateinit var textInput: EditText
    private lateinit var urlInput: EditText
    private lateinit var listenButton: android.widget.Button
    private lateinit var parseButton: android.widget.Button
    private lateinit var downloadButton: android.widget.Button
    private lateinit var importButton: android.widget.Button
    private lateinit var deleteButton: android.widget.Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_functiongemma_command)
        bindViews()
        bindActions()

        try {
            modelStore = FunctionGemmaModelStore(
                java.io.File(getFilesDir(), "functiongemma"),
                FunctionGemmaModelManifest.MODEL
            )
        } catch (error: java.io.IOException) {
            showFatal("无法创建模型目录：" + FunctionGemmaCommandActivity.Companion.safeMessage(error))
            return
        }

        if (!FunctionGemmaDeviceSupport.isSupported()) {
            deviceStatus.setText(R.string.functiongemma_device_unsupported)
            showFatal("当前系统或 CPU ABI 不支持 LiteRT-LM")
            return
        }

        deviceStatus.setText(R.string.functiongemma_device_supported)
        voiceController = HybridVoiceCommandController(this, this)
        updateControls()
        checkAndInitializeModel()
    }

    private fun bindViews() {
        deviceStatus = findViewById<TextView>(R.id.functiongemma_device_status)
        modelStatus = findViewById<TextView>(R.id.functiongemma_model_status)
        status = findViewById<TextView>(R.id.functiongemma_status)
        progress = findViewById<TextView>(R.id.functiongemma_progress)
        log = findViewById<TextView>(R.id.functiongemma_log)
        textInput = findViewById<EditText>(R.id.functiongemma_text_input)
        urlInput = findViewById<EditText>(R.id.functiongemma_url_input)
        listenButton = findViewById<android.widget.Button>(R.id.functiongemma_listen_button)
        parseButton = findViewById<android.widget.Button>(R.id.functiongemma_parse_button)
        downloadButton = findViewById<android.widget.Button>(R.id.functiongemma_download_button)
        importButton = findViewById<android.widget.Button>(R.id.functiongemma_import_button)
        deleteButton = findViewById<android.widget.Button>(R.id.functiongemma_delete_button)
    }

    private fun bindActions() {
        (findViewById<android.view.View?>(R.id.functiongemma_language_group) as RadioGroup)
            .setOnCheckedChangeListener(RadioGroup.OnCheckedChangeListener listener@{ group: RadioGroup?, checkedId: Int ->
                val language: VoiceLanguage = if (checkedId == R.id.functiongemma_language_english)
                    VoiceLanguage.ENGLISH
                else
                    VoiceLanguage.CHINESE
                if (language == selectedLanguage) {
                    return@listener
                }
                selectedLanguage = language
                if (busy && engineReady) {
                    operationGate.next()
                    setBusy(false)
                    status.setText(R.string.functiongemma_status_ready)
                    appendLog("语言已切换，忽略上一次指令的迟到结果")
                }
                if (listening && voiceController != null) {
                    voiceController!!.switchLanguage(selectedLanguage)
                }
                updateControls()
            })
        listenButton!!.setOnClickListener(android.view.View.OnClickListener { view: android.view.View? -> toggleListening() })
        parseButton!!.setOnClickListener(android.view.View.OnClickListener { view: android.view.View? ->
            routeText(
                textInput.getText().toString(), 0L
            )
        })
        downloadButton!!.setOnClickListener(android.view.View.OnClickListener { view: android.view.View? -> downloadModel() })
        importButton!!.setOnClickListener(android.view.View.OnClickListener { view: android.view.View? -> chooseModelFile() })
        deleteButton!!.setOnClickListener(android.view.View.OnClickListener { view: android.view.View? -> confirmDeleteModel() })
        findViewById<android.view.View>(R.id.functiongemma_clear_log_button)
            .setOnClickListener(android.view.View.OnClickListener { view: android.view.View? -> clearLog() })
    }

    private fun checkAndInitializeModel() {
        if (!modelStore.installedFile().isFile()) {
            showModelMissing()
            return
        }
        modelStatus.setText(R.string.functiongemma_model_checking)
        setBusy(true)
        val token = operationGate.next()
        worker.execute(java.lang.Runnable {
            if (!modelStore.isInstalled()) {
                postIfCurrent(token, java.lang.Runnable { this.showModelMissing() })
                return@Runnable
            }
            initializeVerifiedModel(token)
        })
    }

    private fun initializeVerifiedModel(token: kotlin.Long) {
        postIfCurrent(
            token,
            java.lang.Runnable { modelStatus.setText(R.string.functiongemma_model_loading) })
        closeEngine()
        val candidate: LiteRtFunctionGemmaEngine = LiteRtFunctionGemmaEngine(
            java.io.File(getCacheDir(), "functiongemma")
        )
        try {
            candidate.initialize(modelStore.installedFile())
        } catch (error: kotlin.Throwable) {
            candidate.close()
            postIfCurrent(token, java.lang.Runnable {
                setBusy(false)
                engineReady = false
                modelStatus.setText("模型初始化失败")
                appendLog(
                    "错误：模型初始化失败：" + FunctionGemmaCommandActivity.Companion.safeMessage(
                        error
                    )
                )
                updateControls()
            })
            return
        }
        if (!operationGate.isCurrent(token)) {
            candidate.close()
            return
        }
        engine = candidate
        router =
            HybridCommandRouter(candidate, TimeSource { android.os.SystemClock.elapsedRealtime() })
        engineReady = true
        runOnUiThread(java.lang.Runnable {
            if (!operationGate.isCurrent(token)) {
                return@Runnable
            }
            setBusy(false)
            modelStatus.setText(R.string.functiongemma_model_ready)
            status.setText(R.string.functiongemma_status_ready)
            progress.setText("")
            appendLog("FunctionGemma 模型初始化完成")
            updateControls()
        })
    }

    private fun routeText(text: kotlin.String?, utteranceId: kotlin.Long) {
        val trimmed = if (text == null) "" else text.trim { it <= ' ' }
        if (trimmed.isEmpty()) {
            appendLog("请输入需要解析的文字")
            completeVoiceInference(utteranceId)
            return
        }
        if (!engineReady || router == null) {
            appendLog("模型尚未就绪，无法解析")
            completeVoiceInference(utteranceId)
            return
        }

        val language: VoiceLanguage = selectedLanguage
        val activeRouter: HybridCommandRouter = router!!
        val token = operationGate.next()
        status.setText("状态：指令理解中")
        setBusy(true)
        worker.execute(java.lang.Runnable {
            val result: HybridCommandResult = activeRouter.route(language, trimmed)
            postIfCurrent(token, java.lang.Runnable {
                setBusy(false)
                status.setText(R.string.functiongemma_status_ready)
                appendResult(language, trimmed, result)
                updateControls()
                completeVoiceInference(utteranceId)
            })
        })
    }

    private fun appendResult(
        language: VoiceLanguage,
        transcript: kotlin.String?,
        result: HybridCommandResult
    ) {
        if (result.event().isPresent()) {
            appendLog(
                (language.displayName
                        + " [" + result.route().name + "] "
                        + transcript + " -> " + result.event().get().name
                        + "（" + result.elapsedMillis() + " ms）")
            )
        } else {
            val calls = if (result.inference().isPresent())
                result.inference().get().toolCalls().toString()
            else
                "[]"
            val raw = if (result.inference().isPresent())
                FunctionGemmaCommandActivity.Companion.oneLine(result.inference().get().rawOutput())
            else
                ""
            appendLog(
                (language.displayName
                        + " [REJECTED] " + transcript + "（" + result.reason()
                        + "，calls=" + calls + "，raw=" + raw + "）")
            )
        }
    }

    private fun downloadModel() {
        val url: java.net.URL?
        try {
            url = java.net.URL(urlInput.getText().toString().trim { it <= ' ' })
        } catch (error: java.net.MalformedURLException) {
            appendLog("请输入有效的 HTTPS 下载地址")
            return
        }
        stopListening()
        engineReady = false
        setBusy(true)
        status.setText("状态：正在下载模型")
        val token = operationGate.next()
        worker.execute(java.lang.Runnable {
            closeEngine()
            try {
                FunctionGemmaModelDownloader().download(
                    url,
                    modelStore,
                    FunctionGemmaModelDownloader.ProgressListener { downloaded: kotlin.Long, declared: kotlin.Long ->
                        postDownloadProgress(
                            token, downloaded, declared
                        )
                    })
                if (operationGate.isCurrent(token)) {
                    initializeVerifiedModel(token)
                }
            } catch (error: java.io.IOException) {
                postIfCurrent(token, java.lang.Runnable {
                    setBusy(false)
                    status.setText("状态：下载失败")
                    appendLog(
                        "模型下载或校验失败：" + FunctionGemmaCommandActivity.Companion.safeMessage(
                            error
                        )
                    )
                    updateControls()
                })
            }
        })
    }

    private fun postDownloadProgress(
        token: kotlin.Long,
        downloaded: kotlin.Long,
        declared: kotlin.Long
    ) {
        runOnUiThread(java.lang.Runnable {
            if (!operationGate.isCurrent(token)) {
                return@Runnable
            }
            if (declared > 0L) {
                val percent: kotlin.Long = min(100L, downloaded * 100L / declared)
                progress.setText("下载进度：" + percent + "%")
            } else {
                progress.setText(
                    kotlin.String.format(
                        java.util.Locale.ROOT, "已下载：%.1f MiB", downloaded / 1048576.0
                    )
                )
            }
        })
    }

    private fun chooseModelFile() {
        val intent: Intent = Intent(Intent.ACTION_OPEN_DOCUMENT)
        intent.addCategory(Intent.CATEGORY_OPENABLE)
        intent.setType("application/octet-stream")
        startActivityForResult(intent, FunctionGemmaCommandActivity.Companion.IMPORT_MODEL_REQUEST)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != FunctionGemmaCommandActivity.Companion.IMPORT_MODEL_REQUEST || resultCode != Activity.RESULT_OK || data == null || data.getData() == null) {
            return
        }
        importModel(data.getData()!!)
    }

    private fun importModel(uri: android.net.Uri) {
        stopListening()
        engineReady = false
        setBusy(true)
        status.setText("状态：正在导入并校验模型")
        val token = operationGate.next()
        worker.execute(java.lang.Runnable {
            closeEngine()
            try {
                getContentResolver().openInputStream(uri).use { input ->
                    if (input == null) {
                        throw java.io.IOException("无法读取所选文件")
                    }
                    modelStore.install(input)
                    if (operationGate.isCurrent(token)) {
                        initializeVerifiedModel(token)
                    }
                }
            } catch (error: java.io.IOException) {
                postIfCurrent(token, java.lang.Runnable {
                    setBusy(false)
                    status.setText("状态：导入失败")
                    appendLog(
                        "模型导入或校验失败：" + FunctionGemmaCommandActivity.Companion.safeMessage(
                            error
                        )
                    )
                    updateControls()
                })
            }
        })
    }

    private fun confirmDeleteModel() {
        android.app.AlertDialog.Builder(this)
            .setTitle(R.string.functiongemma_delete_title)
            .setMessage(R.string.functiongemma_delete_message)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(
                android.R.string.ok,
                DialogInterface.OnClickListener { dialog: DialogInterface?, which: Int -> deleteModel() })
            .show()
    }

    private fun deleteModel() {
        stopListening()
        engineReady = false
        setBusy(true)
        val token = operationGate.next()
        worker.execute(java.lang.Runnable {
            closeEngine()
            try {
                modelStore.delete()
                postIfCurrent(token, java.lang.Runnable {
                    appendLog("本地 FunctionGemma 模型已删除")
                    showModelMissing()
                })
            } catch (error: java.io.IOException) {
                postIfCurrent(token, java.lang.Runnable {
                    setBusy(false)
                    appendLog(
                        "删除模型失败：" + FunctionGemmaCommandActivity.Companion.safeMessage(
                            error
                        )
                    )
                    updateControls()
                })
            }
        })
    }

    private fun toggleListening() {
        if (listening) {
            stopListening()
            return
        }
        if (!engineReady || voiceController == null) {
            appendLog("模型尚未就绪，无法开始语音指令")
            return
        }
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                kotlin.arrayOf<kotlin.String>(android.Manifest.permission.RECORD_AUDIO),
                FunctionGemmaCommandActivity.Companion.RECORD_AUDIO_PERMISSION_REQUEST
            )
            return
        }
        listening = true
        voiceController!!.start(selectedLanguage)
        updateControls()
    }

    private fun stopListening() {
        if (voiceController != null) {
            voiceController!!.stop()
        }
        listening = false
        updateControls()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: kotlin.Array<kotlin.String?>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == FunctionGemmaCommandActivity.Companion.RECORD_AUDIO_PERMISSION_REQUEST && grantResults.size > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            toggleListening()
        } else if (requestCode == FunctionGemmaCommandActivity.Companion.RECORD_AUDIO_PERMISSION_REQUEST) {
            appendLog("麦克风权限被拒绝")
        }
    }

    private fun showModelMissing() {
        engineReady = false
        setBusy(false)
        modelStatus.setText(R.string.functiongemma_model_missing)
        status.setText(R.string.functiongemma_status_waiting)
        progress.setText("")
        updateControls()
    }

    private fun setBusy(busy: kotlin.Boolean) {
        this.busy = busy
        parseButton!!.setEnabled(!busy && engineReady)
        listenButton!!.setEnabled(!busy && engineReady)
        downloadButton!!.setEnabled(!busy)
        importButton!!.setEnabled(!busy)
        deleteButton!!.setEnabled(
            !busy && ::modelStore.isInitialized && modelStore.installedFile().isFile()
        )
    }

    private fun updateControls() {
        parseButton!!.setEnabled(!busy && engineReady)
        listenButton!!.setEnabled(!busy && engineReady)
        listenButton!!.setText(
            if (listening)
                R.string.functiongemma_stop_listening
            else
                R.string.functiongemma_start_listening
        )
        downloadButton!!.setEnabled(!busy)
        importButton!!.setEnabled(!busy)
        deleteButton!!.setEnabled(
            !busy && ::modelStore.isInitialized && modelStore.installedFile().isFile()
        )
    }

    private fun showFatal(message: kotlin.String?) {
        engineReady = false
        status.setText("状态：" + message)
        downloadButton!!.setEnabled(false)
        importButton!!.setEnabled(false)
        deleteButton!!.setEnabled(false)
        listenButton!!.setEnabled(false)
        parseButton!!.setEnabled(false)
        appendLog("错误：" + message)
    }

    private fun clearLog() {
        hasLogLines = false
        log.setText(R.string.functiongemma_log_empty)
    }

    private fun appendLog(message: kotlin.String?) {
        val line = "[" + logTime.format(java.util.Date()) + "] " + message
        if (!hasLogLines) {
            log.setText(line)
            hasLogLines = true
        } else {
            log.append("\n" + line)
        }
    }

    private fun postIfCurrent(token: kotlin.Long, action: java.lang.Runnable) {
        runOnUiThread(java.lang.Runnable {
            if (operationGate.isCurrent(token)) {
                action.run()
            }
        })
    }

    private fun completeVoiceInference(utteranceId: kotlin.Long) {
        if (utteranceId != 0L && voiceController != null) {
            voiceController!!.completeInference(utteranceId)
        }
    }

    private fun closeEngine() {
        val active: FunctionGemmaEngine? = engine
        engine = null
        router = null
        engineReady = false
        if (active != null) {
            active.close()
        }
    }

    override fun onStateChanged(state: HybridVoiceState, message: kotlin.String) {
        listening = state != HybridVoiceState.STOPPED
        status.setText("状态：" + message)
        updateControls()
    }

    override fun onWake(language: VoiceLanguage, remainingMillis: kotlin.Long) {
        status.setText(
            kotlin.String.format(
                java.util.Locale.ROOT,
                "状态：已唤醒，请说指令（%.1f 秒）",
                remainingMillis / 1000.0
            )
        )
    }

    override fun onTranscript(
        language: VoiceLanguage,
        transcript: kotlin.String,
        utteranceId: kotlin.Long
    ) {
        appendLog(language.displayName + " 语音转写：" + transcript)
        routeText(transcript, utteranceId)
    }

    override fun onWakeTimeout(language: VoiceLanguage) {
        appendLog(language.displayName + " 唤醒窗口超时")
    }

    override fun onDiagnostic(message: kotlin.String) {
        appendLog(message)
    }

    override fun onError(message: kotlin.String) {
        listening = false
        appendLog("语音识别错误：" + message)
        updateControls()
    }

    override fun onDestroy() {
        operationGate.close()
        if (voiceController != null) {
            voiceController!!.close()
        }
        worker.execute(java.lang.Runnable { this.closeEngine() })
        worker.shutdown()
        super.onDestroy()
    }

    companion object {
        private const val RECORD_AUDIO_PERMISSION_REQUEST = 4101
        private const val IMPORT_MODEL_REQUEST = 4102

        private fun safeMessage(error: kotlin.Throwable): kotlin.String? {
            return if (error.message == null) error.javaClass.getSimpleName() else error.message
        }

        private fun oneLine(value: kotlin.String?): kotlin.String {
            val normalized = if (value == null) "" else value.replace('\n', ' ').replace('\r', ' ')
            return if (normalized.length <= 300) normalized else normalized.substring(0, 300) + "…"
        }
    }
}
