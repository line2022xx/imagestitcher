package com.linexx.imagestitcher.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.linexx.imagestitcher.core.CrossAlign
import com.linexx.imagestitcher.core.SizeMode
import com.linexx.imagestitcher.core.StitchDirection
import com.linexx.imagestitcher.model.ImageItem
import com.linexx.imagestitcher.model.OutputFormat
import com.linexx.imagestitcher.model.StitchConfig
import com.linexx.imagestitcher.viewmodel.EditorUiState
import java.util.Locale
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(
    state: EditorUiState,
    onPick: () -> Unit,
    onRemove: (Int) -> Unit,
    onMove: (Int, Int) -> Unit,
    onClear: () -> Unit,
    onConfigChange: (StitchConfig) -> Unit,
    onExport: () -> Unit,
    onBack: () -> Unit,
) {
    var settingsOpen by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("图片拼接") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    if (state.items.isNotEmpty()) {
                        TextButton(onClick = onClear) { Text("清空") }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
        bottomBar = {
            EditorBottomBar(
                state = state,
                onSettings = { settingsOpen = true },
                onExport = onExport,
            )
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            if (state.items.isEmpty()) {
                EmptyEditor(onPick = onPick)
            } else {
                ImageGrid(
                    state = state,
                    onPick = onPick,
                    onRemove = onRemove,
                    onMove = onMove,
                )
            }

            if (state.loading) {
                LoadingOverlay()
            }

            if (state.exporting) {
                ExportingOverlay(progress = state.progress)
            }
        }
    }

    if (settingsOpen) {
        ModalBottomSheet(
            onDismissRequest = { settingsOpen = false },
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surface,
        ) {
            SettingsContent(
                state = state,
                onConfigChange = onConfigChange,
            )
        }
    }
}

@Composable
private fun EmptyEditor(onPick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(28.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "还没有选择图片",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = "支持 JPG / PNG / WebP，单次最多 100 张",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(20.dp))
        Button(onClick = onPick, shape = RoundedCornerShape(12.dp)) {
            Text("选择图片")
        }
    }
}

@Composable
private fun ImageGrid(
    state: EditorUiState,
    onPick: () -> Unit,
    onRemove: (Int) -> Unit,
    onMove: (Int, Int) -> Unit,
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        itemsIndexed(
            items = state.items,
            key = { index, item -> "${item.uri}#$index" },
        ) { index, item ->
            ImageCard(
                index = index,
                item = item,
                total = state.items.size,
                onRemove = { onRemove(index) },
                onMoveUp = { onMove(index, index - 1) },
                onMoveDown = { onMove(index, index + 1) },
            )
        }

        item {
            AddCard(onPick = onPick)
        }
    }
}

@Composable
private fun ImageCard(
    index: Int,
    item: ImageItem,
    total: Int,
    onRemove: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f),
        ) {
            AsyncImage(
                model = item.uri,
                contentDescription = "第 ${index + 1} 张",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(14.dp)),
            )

            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(6.dp)
                    .clip(CircleShape)
                    .background(Color(0x99000000))
                    .padding(horizontal = 8.dp, vertical = 2.dp),
            ) {
                Text(
                    text = "${index + 1}",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White,
                )
            }

            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(bottomStart = 14.dp, bottomEnd = 14.dp))
                    .background(Color(0x8C000000)),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SmallAction(Icons.Default.KeyboardArrowUp, "上移", index > 0, onMoveUp)
                SmallAction(Icons.Default.KeyboardArrowDown, "下移", index < total - 1, onMoveDown)
                SmallAction(Icons.Default.Close, "删除", true, onRemove)
            }
        }
    }
}

@Composable
private fun SmallAction(
    icon: ImageVector,
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.size(36.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = if (enabled) Color.White else Color(0x59FFFFFF),
            modifier = Modifier.size(20.dp),
        )
    }
}

@Composable
private fun AddCard(onPick: () -> Unit) {
    Card(
        onClick = onPick,
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(28.dp),
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "添加图片",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun EditorBottomBar(
    state: EditorUiState,
    onSettings: () -> Unit,
    onExport: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 12.dp,
    ) {
        Column(modifier = Modifier.navigationBarsPadding()) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "输出尺寸",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = sizeSummary(state),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    val plan = state.plan
                    if (plan != null && plan.downscaled) {
                        Text(
                            text = "已缩放至 ${(plan.scale * 100).roundToInt()}% 以适配内存",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
                TextButton(onClick = onSettings) { Text("设置") }
                Spacer(Modifier.width(4.dp))
                Button(
                    onClick = onExport,
                    enabled = state.items.isNotEmpty() && !state.exporting,
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Text("开始拼接")
                }
            }
        }
    }
}

private fun sizeSummary(state: EditorUiState): String {
    val plan = state.plan ?: return "—"
    val mp = String.format(Locale.US, "%.1f", plan.megapixels)
    return "${plan.canvasWidth} × ${plan.canvasHeight}  ·  $mp MP"
}

@Composable
private fun LoadingOverlay() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0x66000000)),
        contentAlignment = Alignment.Center,
    ) {
        Card(shape = RoundedCornerShape(16.dp)) {
            Row(
                modifier = Modifier.padding(horizontal = 22.dp, vertical = 18.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(22.dp),
                    strokeWidth = 2.dp,
                )
                Spacer(Modifier.width(14.dp))
                Text(
                    text = "正在读取图片…",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

@Composable
private fun ExportingOverlay(progress: Float) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0x66000000)),
        contentAlignment = Alignment.Center,
    ) {
        Card(shape = RoundedCornerShape(16.dp)) {
            Column(
                modifier = Modifier
                    .width(240.dp)
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = "正在拼接…",
                    style = MaterialTheme.typography.titleSmall,
                )
                Spacer(Modifier.height(14.dp))
                LinearProgressIndicator(
                    progress = { progress.coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    text = "${(progress * 100).roundToInt()}%",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

// region 设置面板

@Composable
private fun SettingsContent(
    state: EditorUiState,
    onConfigChange: (StitchConfig) -> Unit,
) {
    val config = state.config
    val vertical = config.direction == StitchDirection.VERTICAL
    val crossName = if (vertical) "宽度" else "高度"

    var gapDraft by remember(config.gap) { mutableFloatStateOf(config.gap.toFloat()) }
    var qualityDraft by remember(config.quality) { mutableFloatStateOf(config.quality.toFloat()) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(bottom = 24.dp),
    ) {
        Text(
            text = "拼接设置",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )

        SectionTitle("拼接方向")
        SegmentedOptions(
            options = listOf(
                StitchDirection.VERTICAL to "纵向",
                StitchDirection.HORIZONTAL to "横向",
            ),
            selected = config.direction,
            onSelect = { onConfigChange(config.copy(direction = it)) },
        )

        SectionTitle("缩放模式")
        SegmentedOptions(
            options = listOf(
                SizeMode.ORIGINAL to "直接拼接",
                SizeMode.FIT_MAX to "最大$crossName",
                SizeMode.FIT_MIN to "最小$crossName",
            ),
            selected = config.sizeMode,
            onSelect = { onConfigChange(config.copy(sizeMode = it)) },
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = when (config.sizeMode) {
                SizeMode.ORIGINAL -> "所有图片保持原始像素，窄图两侧补充背景色"
                SizeMode.FIT_MAX -> "以最${if (vertical) "宽" else "高"}的一张为基准，其余图片等比放大到该$crossName"
                SizeMode.FIT_MIN -> "以最${if (vertical) "窄" else "矮"}的一张为基准，其余图片等比缩小到该$crossName"
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        SectionTitle("对齐方式")
        SegmentedOptions(
            options = if (vertical) {
                listOf(
                    CrossAlign.START to "左对齐",
                    CrossAlign.CENTER to "居中",
                    CrossAlign.END to "右对齐",
                )
            } else {
                listOf(
                    CrossAlign.START to "顶部",
                    CrossAlign.CENTER to "居中",
                    CrossAlign.END to "底部",
                )
            },
            selected = config.crossAlign,
            onSelect = { onConfigChange(config.copy(crossAlign = it)) },
        )
        Text(
            text = if (config.sizeMode == SizeMode.ORIGINAL) {
                "尺寸不一的图片在${if (vertical) "水平" else "垂直"}方向的对齐位置"
            } else {
                "统一缩放后各图尺寸一致，此选项影响较小"
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        SectionTitle("图片间距 · ${gapDraft.roundToInt()} px")
        Slider(
            value = gapDraft,
            onValueChange = { gapDraft = it },
            onValueChangeFinished = { onConfigChange(config.copy(gap = gapDraft.roundToInt())) },
            valueRange = 0f..120f,
        )

        SectionTitle("背景色")
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            ColorSwatch(0xFFFFFFFF.toInt(), config.background) {
                onConfigChange(config.copy(background = 0xFFFFFFFF.toInt()))
            }
            ColorSwatch(0xFF000000.toInt(), config.background) {
                onConfigChange(config.copy(background = 0xFF000000.toInt()))
            }
            ColorSwatch(0xFFF2F3F5.toInt(), config.background) {
                onConfigChange(config.copy(background = 0xFFF2F3F5.toInt()))
            }
            ColorSwatch(0x00000000, config.background) {
                onConfigChange(config.copy(background = 0x00000000))
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = if (config.transparent) "透明背景，输出将自动使用 PNG" else backgroundName(config.background),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        SectionTitle("输出格式")
        SegmentedOptions(
            options = listOf(
                OutputFormat.JPEG to "JPEG（体积小）",
                OutputFormat.PNG to "PNG（无损）",
            ),
            selected = config.effectiveFormat,
            onSelect = { onConfigChange(config.copy(format = it)) },
        )

        if (config.effectiveFormat == OutputFormat.JPEG) {
            SectionTitle("JPEG 质量 · ${qualityDraft.roundToInt()}")
            Slider(
                value = qualityDraft,
                onValueChange = { qualityDraft = it },
                onValueChangeFinished = { onConfigChange(config.copy(quality = qualityDraft.roundToInt())) },
                valueRange = 60f..100f,
            )
        }

        Spacer(Modifier.height(18.dp))

        val plan = state.plan
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.primaryContainer,
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "预计输出",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                Text(
                    text = if (plan == null) "—" else "${plan.canvasWidth} × ${plan.canvasHeight} px",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                Text(
                    text = if (plan == null) {
                        "共 ${state.items.size} 张"
                    } else {
                        "约 ${String.format(Locale.US, "%.1f", plan.megapixels)} MP · 共 ${state.items.size} 张"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                if (plan != null && plan.downscaled) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "原始尺寸 ${plan.rawWidth} × ${plan.rawHeight}，为适配设备内存已自动缩放",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }
}

private fun backgroundName(color: Int): String = when (color) {
    0xFFFFFFFF.toInt() -> "白色背景"
    0xFF000000.toInt() -> "黑色背景"
    0xFFF2F3F5.toInt() -> "浅灰背景"
    else -> "自定义背景"
}

@Composable
private fun SectionTitle(text: String) {
    Spacer(Modifier.height(20.dp))
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.Medium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(8.dp))
}

@Composable
private fun <T> SegmentedOptions(
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        options.forEach { (value, label) ->
            val isSelected = value == selected
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(9.dp))
                    .background(
                        if (isSelected) MaterialTheme.colorScheme.surface else Color.Transparent
                    )
                    .clickable { onSelect(value) }
                    .padding(vertical = 9.dp, horizontal = 4.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    color = if (isSelected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
        }
    }
}

@Composable
private fun ColorSwatch(color: Int, selectedColor: Int, onClick: () -> Unit) {
    val isSelected = color == selectedColor
    val transparent = (color ushr 24) and 0xFF < 255

    Box(
        modifier = Modifier
            .size(48.dp)
            .border(
                border = BorderStroke(
                    width = if (isSelected) 2.5.dp else 1.dp,
                    color = if (isSelected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.outline
                    },
                ),
                shape = CircleShape,
            )
            .padding(4.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(CircleShape)
                .background(if (transparent) Color.White else Color(color))
                .clickable { onClick() },
        ) {
            if (transparent) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val cell = size.minDimension / 4f
                    for (row in 0..3) {
                        for (col in 0..3) {
                            if ((row + col) % 2 == 0) {
                                drawRect(
                                    color = Color(0xFFDDE0E6),
                                    topLeft = Offset(col * cell, row * cell),
                                    size = Size(cell, cell),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// endregion
