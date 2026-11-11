package com.linexx.imagestitcher.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.linexx.imagestitcher.core.ImageLoader
import com.linexx.imagestitcher.core.MediaStoreSaver
import com.linexx.imagestitcher.core.SourceSize
import com.linexx.imagestitcher.core.StitchPlan
import com.linexx.imagestitcher.core.StitchPlanner
import com.linexx.imagestitcher.core.Stitcher
import com.linexx.imagestitcher.model.ImageItem
import com.linexx.imagestitcher.model.StitchConfig
import com.linexx.imagestitcher.model.StitchResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class EditorUiState(
    val items: List<ImageItem> = emptyList(),
    val config: StitchConfig = StitchConfig(),
    val plan: StitchPlan? = null,
    val loading: Boolean = false,
    val exporting: Boolean = false,
    val progress: Float = 0f,
    val result: StitchResult? = null,
    val savedUri: Uri? = null,
    val message: String? = null,
)

class EditorViewModel(application: Application) : AndroidViewModel(application) {

    private val _state = MutableStateFlow(EditorUiState())
    val state: StateFlow<EditorUiState> = _state.asStateFlow()

    private val stitcher = Stitcher(application)

    // region 图片集合

    fun addImages(uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            _state.update { it.copy(loading = true) }
            val context = getApplication<Application>()
            val probed = withContext(Dispatchers.IO) {
                uris.mapNotNull { uri ->
                    runCatching { ImageLoader.probe(context, uri) }.getOrNull()
                }
            }
            _state.update { current ->
                val merged = current.items + probed
                val limited = if (merged.size > MAX_ITEMS) merged.take(MAX_ITEMS) else merged
                val notice = when {
                    probed.isEmpty() -> "所选图片无法读取，请换一批试试"
                    merged.size > MAX_ITEMS -> "最多支持 $MAX_ITEMS 张，已保留前 $MAX_ITEMS 张"
                    else -> current.message
                }
                refresh(
                    current.copy(
                        items = limited,
                        loading = false,
                        result = null,
                        savedUri = null,
                        message = notice,
                    )
                )
            }
        }
    }

    fun removeAt(index: Int) {
        _state.update { current ->
            if (index !in current.items.indices) {
                current
            } else {
                val list = current.items.toMutableList().apply { removeAt(index) }
                refresh(current.copy(items = list, result = null, savedUri = null))
            }
        }
    }

    fun move(from: Int, to: Int) {
        _state.update { current ->
            val list = current.items
            if (from !in list.indices || to !in list.indices || from == to) {
                current
            } else {
                val mutable = list.toMutableList()
                mutable.add(to, mutable.removeAt(from))
                refresh(current.copy(items = mutable))
            }
        }
    }

    fun clearAll() {
        _state.update { current -> EditorUiState(config = current.config) }
    }

    // endregion

    fun updateConfig(config: StitchConfig) {
        _state.update { current ->
            refresh(current.copy(config = config, result = null, savedUri = null))
        }
    }

    private fun refresh(state: EditorUiState): EditorUiState {
        if (state.items.isEmpty()) return state.copy(plan = null)
        val sources = state.items.map { SourceSize(it.width, it.height) }
        val plan = StitchPlanner.plan(sources, state.config.toOptions(), pixelBudget())
        return state.copy(plan = plan)
    }

    /**
     * 画布可用的像素预算。
     * 取堆上限的 35%（每个像素 4 字节），其余留给单张源图与编码缓冲。
     */
    private fun pixelBudget(): Long {
        val maxMemory = Runtime.getRuntime().maxMemory()
        return (maxMemory * 0.35 / 4.0).toLong().coerceAtLeast(4_000_000L)
    }

    // region 导出

    fun export() {
        val snapshot = _state.value
        val plan = snapshot.plan ?: return
        if (snapshot.items.isEmpty() || snapshot.exporting) return

        viewModelScope.launch {
            _state.update {
                it.copy(exporting = true, progress = 0f, result = null, savedUri = null, message = null)
            }

            val context = getApplication<Application>()
            val outcome = withContext(Dispatchers.IO) {
                runCatching {
                    val dir = File(context.cacheDir, "exports").apply { mkdirs() }
                    val now = System.currentTimeMillis()
                    dir.listFiles()?.forEach { stale ->
                        if (now - stale.lastModified() > ONE_HOUR) stale.delete()
                    }

                    val format = snapshot.config.effectiveFormat
                    val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
                    val file = File(dir, "stitch_$stamp.${format.extension}")

                    var lastPercent = -1
                    val ok = stitcher.render(
                        items = snapshot.items,
                        plan = plan,
                        background = snapshot.config.background,
                        format = format,
                        quality = snapshot.config.quality,
                        output = file,
                        onProgress = { progress ->
                            val percent = (progress * 100).toInt()
                            if (percent != lastPercent) {
                                lastPercent = percent
                                _state.update { it.copy(progress = progress) }
                            }
                        },
                    )

                    if (ok && file.exists() && file.length() > 0L) {
                        StitchResult(
                            file = file,
                            width = plan.canvasWidth,
                            height = plan.canvasHeight,
                            mime = format.mime,
                            sizeBytes = file.length(),
                        )
                    } else {
                        file.delete()
                        null
                    }
                }.getOrNull()
            }

            if (outcome == null) {
                _state.update {
                    it.copy(exporting = false, progress = 0f, message = "拼接失败：请减少图片数量或尺寸后重试")
                }
            } else {
                _state.update { it.copy(exporting = false, progress = 1f, result = outcome) }
            }
        }
    }

    fun saveToGallery() {
        val result = _state.value.result ?: return
        viewModelScope.launch {
            val context = getApplication<Application>()
            val uri = withContext(Dispatchers.IO) {
                runCatching {
                    MediaStoreSaver.save(context, result.file, result.file.name, result.mime)
                }.getOrNull()
            }
            _state.update {
                it.copy(
                    savedUri = uri,
                    message = if (uri == null) {
                        "保存失败，请检查存储权限"
                    } else {
                        "已保存到相册 Pictures/ImageStitcher"
                    },
                )
            }
        }
    }

    // endregion

    fun consumeMessage() {
        _state.update { it.copy(message = null) }
    }

    companion object {
        const val MAX_ITEMS = 100
        private const val ONE_HOUR = 60 * 60 * 1000L
    }
}
