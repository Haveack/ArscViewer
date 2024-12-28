package org.example.project

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Button
import androidx.compose.material.Divider
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.AwtWindow
import androidx.compose.ui.window.MenuBar
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.google.devrel.gmscore.tools.apk.arsc.BinaryResourceFile
import com.google.devrel.gmscore.tools.apk.arsc.Chunk
import com.google.devrel.gmscore.tools.apk.arsc.ChunkWithChunks
import com.google.devrel.gmscore.tools.apk.arsc.PackageChunk
import com.google.devrel.gmscore.tools.apk.arsc.ResourceTableChunk
import com.google.devrel.gmscore.tools.apk.arsc.StringPoolChunk
import com.google.devrel.gmscore.tools.apk.arsc.StringPoolChunk.StringPoolStyle
import com.google.devrel.gmscore.tools.apk.arsc.TypeChunk
import com.google.devrel.gmscore.tools.apk.arsc.TypeSpecChunk
import io.github.vinceglb.filekit.compose.rememberFilePickerLauncher
import io.github.vinceglb.filekit.core.FileKitPlatformSettings
import io.github.vinceglb.filekit.core.PickerType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.awt.FileDialog
import java.awt.Frame
import java.io.File
import java.util.zip.ZipFile

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "Arsc viewer",
    ) {
        var showFilePicker by remember { mutableStateOf(false) }
        val files = remember { mutableStateListOf<File>() }
        MenuBar {
            Menu("File") {
                Item("Open", onClick = {
                    showFilePicker = true
                })
            }
        }

        val filePickerLauncher = rememberFilePickerLauncher(
            title = "Open...",
            type = PickerType.File(listOf("arsc", "apk", "zip")),
            platformSettings = FileKitPlatformSettings(parentWindow = this.window)
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
            Row(horizontalArrangement = Arrangement.Center) {
                files.forEach { file ->
                    key(file) {
                        val scrollState = rememberScrollState()
                        Column(
                            modifier = Modifier.weight(1f).verticalScroll(scrollState),
                        ) {
                            ArscFileView(file, onEjectClick = { files.remove(file) }, modifier = Modifier.padding(8.dp))
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

@Composable
private fun ArscFileView(file: File, onEjectClick: (File) -> Unit, modifier: Modifier = Modifier) {
    var fileDesc by remember { mutableStateOf("${file.absolutePath}[${file.length().size}]") }
    var chunks by remember { mutableStateOf<List<Chunk>>(emptyList()) }
    LaunchedEffect(file) {
        val nonNullFile = file
        withContext(Dispatchers.IO) {
            when (nonNullFile.extension) {
                "arsc" -> {
                    nonNullFile.inputStream().use {
                        val arscFile = BinaryResourceFile.fromInputStream(nonNullFile.inputStream())
                        chunks = arscFile.chunks
                    }
                }

                "apk", "zip" -> {
                    ZipFile(nonNullFile).use { zip ->
                        val arscEntry = zip.getEntry("resources.arsc")
                        if (arscEntry != null) {
                            chunks = BinaryResourceFile.fromInputStream(zip.getInputStream(arscEntry)).chunks
                            fileDesc = "${file.absolutePath}[${file.length().size}]->resources.arsc[${arscEntry.size.size}]"
                        } else {
                            chunks = emptyList()
                        }
                    }
                }

                else -> {
                    chunks = emptyList()
                }
            }
        }
    }

    Column(modifier = modifier) {
        Button(onClick = { onEjectClick(file) }) { Text("Eject") }
        Text(fileDesc, style = MaterialTheme.typography.h6)

        chunks.forEach {
            ChunkView(
                modifier = Modifier.border(1.dp, Color.Black).fillMaxWidth().padding(8.dp),
                chunk = it
            )
        }

        if (chunks.isEmpty()) {
            Text("No chunks, is this the right file?")
        }
    }
}

@Composable
private fun ChunkView(chunk: Chunk, modifier: Modifier = Modifier) {
    SelectionContainer {
        when (chunk) {
            is ResourceTableChunk -> ResourceTableChunkView(chunk, modifier)
            is PackageChunk -> PackageChunkView(chunk, modifier)
            is ChunkWithChunks -> ChunkWithChunksView(chunk, modifier)
            is StringPoolChunk -> StringPoolChunkView(chunk, modifier)
            is TypeChunk -> TypeChunkView(chunk, modifier)
            is TypeSpecChunk -> TypeSpecChunkView(chunk, modifier)
            else -> Text("Unknown chunk ${chunk.javaClass.simpleName} size=${chunk.originalChunkSize}")
        }
    }
}

@Composable
private fun ResourceTableChunkView(chunk: ResourceTableChunk, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
    ) {
        Text("RESOURCE TABLE[${chunk.originalChunkSize.size}]", style = MaterialTheme.typography.h6)
        Text("Header[${chunk.headerSize.size}] packageCount=${chunk.packages.size}")
        Text("Payload[${(chunk.originalChunkSize - chunk.headerSize).size}]")

        chunk.chunks.values.forEach {
            when (it) {
                is StringPoolChunk -> StringPoolChunkView(
                    modifier = Modifier.border(1.dp, Color.Black).padding(8.dp),
                    chunk = it,
                    name = "Global string pool"
                )
                else -> ChunkView(
                    modifier = Modifier.border(1.dp, Color.Black).padding(8.dp),
                    chunk = it,
                )
            }
        }
    }
}

@Composable
private fun PackageChunkView(chunk: PackageChunk, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
    ) {
        Text("PACKAGE[${chunk.originalChunkSize.size}] ${chunk.packageName}", style = MaterialTheme.typography.h6)
        Text("Header[${chunk.headerSize.size}] id=${String.format("0x%02x", chunk.id)} name=${chunk.packageName}")
        Text("Payload[${(chunk.originalChunkSize - chunk.headerSize).size}]")
        Text("Resource type count=${chunk.typeSpecChunks.size}")

        StringPoolChunkView(
            modifier = Modifier.border(1.dp, Color.Black).fillMaxWidth().padding(8.dp),
            chunk = chunk.keyStringPool,
            name = "Key string pool"
        )
        StringPoolChunkView(
            modifier = Modifier.border(1.dp, Color.Black).fillMaxWidth().padding(8.dp),
            chunk = chunk.typeStringPool,
            name = "Type string pool"
        )

        chunk.typeSpecChunks.forEach {
            ResourceTypeView(
                modifier = Modifier.border(1.dp, Color.Black).fillMaxWidth().padding(8.dp),
                typeSpecChunk = it,
                typeChunks = chunk.getTypeChunks(
                    it.typeName
                )
            )
        }
    }
}

@Composable
private fun ResourceTypeView(typeSpecChunk: TypeSpecChunk, typeChunks: Collection<TypeChunk>, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
    ) {
        val totalSize = (typeSpecChunk.originalChunkSize + typeChunks.sumOf { it.originalChunkSize }).size
        Text("Resource Type[$totalSize]: ${typeSpecChunk.typeName}", style = MaterialTheme.typography.h6)
        Text("Config count=${typeChunks.size}")
        Text("Entry count=${typeSpecChunk.resourceCount}")

        var expanded by remember { mutableStateOf(false) }
        Button(onClick = { expanded = !expanded }) {
            Text(if (expanded) "Collapse" else "Expand")
        }

        if (expanded) {
            TypeSpecChunkView(typeSpecChunk, modifier = modifier)

            typeChunks.forEach {
                TypeChunkView(it, modifier = modifier)
            }
        }
    }
}

@Composable
private fun TypeChunkView(chunk: TypeChunk, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
    ) {
        val sizeText = remember(chunk) {
            val bytes = chunk.toByteArray()
            if (bytes.size == chunk.originalChunkSize) {
                "[${chunk.originalChunkSize.size}]"
            } else {
                "$[${chunk.originalChunkSize.size}][*${bytes.size.size}]"
            }
        }
        Text("TYPE$sizeText: ${chunk.typeName}-${chunk.configuration}", style = MaterialTheme.typography.h6)
        Row {
            val entries = remember(chunk) { chunk.entries.entries.toList() }
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text("Header[${chunk.headerSize.size}] id=${String.format("0x%02x", chunk.id)}, entryCount=${chunk.totalEntryCount} config=${chunk.configuration}")
                Text("Payload[${(chunk.originalChunkSize - chunk.headerSize).size}]")
                Text("Entry count=${entries.size}")
                Text("Null entry count=${chunk.totalEntryCount - entries.size}")
                Text("Null entry size=${((chunk.totalEntryCount - entries.size) * 4).size}")
                Text("Total entry count=${chunk.totalEntryCount}")
            }

            Column(
                modifier = Modifier.border(1.dp, Color.Black).weight(1f)
            ) {
                Text("Entries[${entries.size}]")
                var filter by remember { mutableStateOf("") }
                TextField(
                    modifier = Modifier.fillMaxWidth(),
                    value = filter,
                    onValueChange = { filter = it }
                )

                var filteredEntries by remember { mutableStateOf(emptyList<MutableMap.MutableEntry<Int, TypeChunk.Entry>>()) }
                LaunchedEffect(filter, entries) {
                    withContext(Dispatchers.Default) {
                        filteredEntries = entries.filter { it.describe(chunk.packageChunk.id, chunk.id).contains(filter, ignoreCase = true) }
                    }
                }
                LazyColumn(
                    modifier = Modifier.heightIn(max = 400.dp),
                    contentPadding = PaddingValues(2.dp)
                ) {
                    itemsIndexed(filteredEntries) { index, it ->
                        if (index > 0) {
                            Divider()
                        }
                        val text = remember(it, chunk) { it.describe(chunk.packageChunk.id, chunk.id) }
                        Text(text)
                    }
                }
            }
        }
    }
}

@Composable
private fun StringPoolChunkView(chunk: StringPoolChunk, modifier: Modifier = Modifier, name: String = "") {
    Column(
        modifier = modifier,
    ) {
        Text("STRING POOL[${chunk.originalChunkSize.size}]: $name", style = MaterialTheme.typography.h6)

        Row {
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text("Header[${chunk.headerSize.size}] stringCount=${chunk.stringCount}, styleCount=${chunk.styleCount}, stringType=${chunk.stringType}, isSorted=${chunk.isSorted}")
                Text("Payload[${(chunk.originalChunkSize - chunk.headerSize).size}]")
            }

            if (chunk.stringCount > 0) {
                Column(
                    modifier = Modifier.border(1.dp, Color.Black).weight(1f)
                ) {
                    Text("String[${chunk.stringCount}]", style = MaterialTheme.typography.subtitle1)
                    var filter by remember { mutableStateOf("") }
                    TextField(modifier = Modifier.fillMaxWidth(), value = filter, onValueChange = { filter = it })

                    var strings by remember { mutableStateOf(emptyList<String>()) }
                    LaunchedEffect(filter, chunk) {
                        withContext(Dispatchers.Default) {
                            strings = (0 until chunk.stringCount)
                                .map { chunk.getString(it) }
                                .filter { it.contains(filter, ignoreCase = true) }
                        }
                    }
                    LazyColumn(
                        modifier = Modifier.heightIn(max = 200.dp),
                        contentPadding = PaddingValues(2.dp)
                    ) {
                        itemsIndexed(strings) { index, it ->
                            if (index > 0) {
                                Divider()
                            }
                            Text(it)
                        }
                    }
                }
            }

            if (chunk.styleCount > 0) {
                Column(
                    modifier = Modifier.border(1.dp, Color.Black).weight(1f)
                ) {
                    Text("Style[${chunk.styleCount}]", style = MaterialTheme.typography.subtitle1)
                    var filter by remember { mutableStateOf("") }
                    TextField(modifier = Modifier.fillMaxWidth(), value = filter, onValueChange = { filter = it })
                    var styles by remember { mutableStateOf(emptyList<StringPoolStyle>()) }
                    LaunchedEffect(filter, chunk) {
                        withContext(Dispatchers.Default) {
                            styles = (0 until chunk.styleCount)
                                .map { chunk.getStyle(it) }
                                .filter { it.toString().contains(filter, ignoreCase = true) }
                        }
                    }
                    LazyColumn(
                        modifier = Modifier.heightIn(max = 200.dp),
                        contentPadding = PaddingValues(2.dp)
                    ) {
                        itemsIndexed(styles) { index, it ->
                            if (index > 0) {
                                Divider()
                            }
                            Text(it.toString())
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ChunkWithChunksView(chunk: ChunkWithChunks, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
    ) {
        Text("${chunk.javaClass.simpleName}[${chunk.originalChunkSize.size}]", style = MaterialTheme.typography.h6)
        Text("Header[${chunk.headerSize.size}]")
        Text("Payload[${(chunk.originalChunkSize - chunk.headerSize).size}]")
        chunk.chunks.values.forEach {
            ChunkView(
                modifier = Modifier.border(1.dp, Color.Black).fillMaxWidth().padding(8.dp),
                chunk = it
            )
        }
    }
}

@Composable
private fun TypeSpecChunkView(chunk: TypeSpecChunk, modifier: Modifier = Modifier, configCount: Int? = null) {
    Column(
        modifier = modifier,
    ) {
        Text("TYPE SPEC[${chunk.originalChunkSize.size}]: ${chunk.typeName}", style = MaterialTheme.typography.h6)
        Text("Header[${chunk.headerSize.size}]: id=${String.format("0x%02x", chunk.id)} resCount=${chunk.resourceCount}")
        Text("Payload[${(chunk.originalChunkSize - chunk.headerSize).size}]")

        if (configCount != null) {
            Text("Config count=$configCount")
        }
    }
}

private fun MutableMap.MutableEntry<Int, TypeChunk.Entry>.describe(packageId: Int, typeId: Int): String {
    val id = String.format("0x%02x%02x%04x", packageId, typeId, key)
    val allValues = (listOfNotNull(value.value()) + value.values().values)
    val valueStrs = allValues.map { value ->
        "${value.type().name}:0x${value.data().toString(16)}"
    }
    val valueStr = when {
        valueStrs.isEmpty() -> ""
        valueStrs.size == 1 -> valueStrs[0]
        else -> valueStrs.toString()
    }
    val parentStr = if (value.isComplex) {
        "parent=${value.parentEntry().toHexString(prefix = true)}"
    } else {
        ""
    }
    return "$id[${value.toByteArray().size.size}] ${value.typeName()}/${value.key()}=${valueStr} flags=${value.flags()} $parentStr"
}

private fun Int.toHexString(width: Int = 2, uppercase: Boolean = false, prefix: Boolean = false): String {
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
