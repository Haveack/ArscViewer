package org.example.project

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.Button
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.darkColors
import androidx.compose.material.lightColors
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.window.MenuBar
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.google.devrel.gmscore.tools.apk.arsc.BinaryResourceFile
import com.google.devrel.gmscore.tools.apk.arsc.TypeChunk
import io.github.vinceglb.filekit.compose.rememberFilePickerLauncher
import io.github.vinceglb.filekit.core.FileKit
import io.github.vinceglb.filekit.core.FileKitPlatformSettings
import io.github.vinceglb.filekit.core.PickerType
import io.github.vinceglb.filekit.core.pickFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.example.project.diff.ArscDiffView
import org.example.project.info.ArscFileView
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipFile

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "Arsc viewer",
    ) {
        var colors by remember { mutableStateOf(lightColors()) }
        MaterialTheme(colors = colors) {
            var showFilePicker by remember { mutableStateOf(false) }
            val files = remember { mutableStateListOf<File>() }
            MenuBar {
                Menu("File") {
                    Item("Open", onClick = {
                        showFilePicker = true
                    })
                }
                Menu("View") {
                    Item("Light/Dark", onClick = {
                        colors = if (colors.isLight) {
                            darkColors()
                        } else {
                            lightColors()
                        }
                    })
                }
            }

            val filePickerLauncher = rememberFilePickerLauncher(
                title = "Open...",
                type = PickerType.File(listOf("arsc", "apk", "zip")),
                platformSettings = FileKitPlatformSettings(parentWindow = window)
            ) {
                it?.let { files.add(it.file) }
                showFilePicker = false
            }

            if (showFilePicker) {
                filePickerLauncher.launch()
            }

            Surface(
                modifier = Modifier.fillMaxSize()
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center
                ) {
                    files.forEach { file ->
                        key(file) {
                            Column(
                                modifier = Modifier.weight(1f)
                            ) {
                                val scope = rememberCoroutineScope()
                                val diffingFilesState = remember { mutableStateOf<Pair<File, File>?>(null) }
                                diffingFilesState.value.let { diffingFiles ->
                                    if (diffingFiles == null) {
                                        SelectionContainer {
                                            ArscFileView(
                                                file = file,
                                                onEjectClick = { files.remove(file) },
                                                onDiffClick = { baseFile ->
                                                    scope.launch {
                                                        val newFile = FileKit.pickFile(
                                                            type = PickerType.File(listOf("arsc", "apk", "zip")),
                                                            platformSettings = FileKitPlatformSettings(parentWindow = window)
                                                        )
                                                        if (newFile != null) {
                                                            diffingFilesState.value = baseFile to newFile.file
                                                        }
                                                    }
                                                },
                                            )
                                        }
                                    } else {
                                        SelectionContainer {
                                            ArscDiffView(
                                                baseFile = diffingFiles.first,
                                                newFile = diffingFiles.second,
                                                onClickExit = { diffingFilesState.value = null }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    if (files.isEmpty()) {
                        Button(
                            modifier = Modifier.align(Alignment.CenterVertically),
                            onClick = { showFilePicker = true }
                        ) {
                            Text(text = "Open...")
                        }
                    }
                }
            }
        }
    }
}


sealed interface LoadedArsc {
    val resourceTable: BinaryResourceFile

    data class FromArsc(val file: File, override val resourceTable: BinaryResourceFile) : LoadedArsc
    data class FromApk(val file: File, val zipEntry: ZipEntry, override val resourceTable: BinaryResourceFile) : LoadedArsc
}

internal fun LoadedArsc.describe(): String {
    return when (this) {
        is LoadedArsc.FromArsc -> "${file.absolutePath}[${file.length().byteSize}]"
        is LoadedArsc.FromApk -> "${file.absolutePath}[${file.length().byteSize}]->${zipEntry.name}[${zipEntry.size.byteSize}]"
    }
}

internal fun LoadedArsc.describeAsAnnotated(
    fileStyle: SpanStyle = SpanStyle(),
    fileNameStyle: SpanStyle = SpanStyle(fontWeight = FontWeight.Bold),
    sizeStyle: SpanStyle = fileNameStyle,
): AnnotatedString {
    return when (this) {
        is LoadedArsc.FromArsc -> {
            buildAnnotatedString {
                withStyle(fileStyle) {
                    append(file.path.removeSuffix(file.name))
                }
                withStyle(fileNameStyle) {
                    append(file.name)
                }
                withStyle(sizeStyle) {
                    append("[${file.length().byteSize}]")
                }
            }
        }

        is LoadedArsc.FromApk -> {
            buildAnnotatedString {
                withStyle(fileStyle) {
                    append(file.path.removeSuffix(file.name))
                }
                withStyle(fileNameStyle) {
                    append(file.name)
                }
                withStyle(sizeStyle) {
                    append("[${file.length().byteSize}]")
                }
                append("->")
                withStyle(fileNameStyle) {
                    append(zipEntry.name)
                }
                withStyle(sizeStyle) {
                    append("[${zipEntry.size.byteSize}]")
                }
            }
        }
    }
}

internal suspend fun loadArsc(file: File): LoadedArsc = withContext(Dispatchers.IO) {
    val nonNullFile = file
    when (nonNullFile.extension) {
        "arsc" -> {
            nonNullFile.inputStream().use {
                LoadedArsc.FromArsc(file, BinaryResourceFile.fromInputStream(nonNullFile.inputStream()))
            }
        }

        "apk", "zip" ->
            ZipFile(nonNullFile).use { zip ->
                val arscEntry = zip.getEntry("resources.arsc")
                if (arscEntry != null) {
                    zip.getInputStream(arscEntry).use {
                        LoadedArsc.FromApk(file, arscEntry, BinaryResourceFile.fromInputStream(it))
                    }
                } else {
                    throw IllegalArgumentException("No resources.arsc found in $file")
                }
            }

        else -> {
            throw IllegalArgumentException("Unsupported file type: ${nonNullFile.extension}, should be arsc, apk or zip")
        }
    }
}

internal fun Map.Entry<Int, TypeChunk.Entry>.describe(): String {
    return "${value.typeName()}/${value.key()}"
}


internal fun Map.Entry<Int, TypeChunk.Entry>.describe(packageId: Int, typeId: Int, showValue: Boolean = false): String {
    val id = String.format("0x%02x%02x%04x", packageId, typeId, key)
    val allValues = (listOfNotNull(value.value()) + value.values().values)
    val valueStr = if (showValue) {
        val valueStrs = allValues.map { value ->
            "${value.type().name}:0x${value.data().toString(16)}"
        }
        when {
            valueStrs.isEmpty() -> ""
            valueStrs.size == 1 -> "=${valueStrs[0]}"
            else -> "=$valueStrs"
        }
    } else {
        ""
    }

    val parentStr = if (value.isComplex) {
        "parent=${value.parentEntry().toHexString(prefix = true)}"
    } else {
        ""
    }

    return "$id[${value.toByteArray().size.byteSize}] ${value.typeName()}/${value.key()}${valueStr} flags=${value.flags()} $parentStr"
}

internal fun Int.toHexString(width: Int = 2, uppercase: Boolean = false, prefix: Boolean = false): String {
    val format = if (uppercase) {
        if (prefix) {
            "0x%0${width}X"
        } else {
            "%0${width}X"
        }
    } else {
        if (prefix) {
            "0x%0${width}x"
        } else {
            "%0${width}x"
        }
    }
    return String.format(format, this)
}

internal val TypeChunk.typeConfig: String
    get() = "$typeName-$configuration"
