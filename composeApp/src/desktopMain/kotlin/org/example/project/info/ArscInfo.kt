package org.example.project.info

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.TooltipArea
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material.Icon
import androidx.compose.material.LocalTextStyle
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.TextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.unit.dp
import com.google.devrel.gmscore.tools.apk.arsc.BinaryResourceValue
import com.google.devrel.gmscore.tools.apk.arsc.Chunk
import com.google.devrel.gmscore.tools.apk.arsc.ChunkWithChunks
import com.google.devrel.gmscore.tools.apk.arsc.PackageChunk
import com.google.devrel.gmscore.tools.apk.arsc.ResourceTableChunk
import com.google.devrel.gmscore.tools.apk.arsc.StringPoolChunk
import com.google.devrel.gmscore.tools.apk.arsc.TypeChunk
import com.google.devrel.gmscore.tools.apk.arsc.TypeSpecChunk
import org.example.project.byteSize
import org.example.project.describe
import org.example.project.describeAsAnnotated
import org.example.project.loadArsc
import org.example.project.typeConfig
import java.io.File

@Composable
internal fun ArscFileView(file: File, onEjectClick: (File) -> Unit, onDiffClick: (baseFile: File) -> Unit, modifier: Modifier = Modifier) {
    var fileDesc by remember { mutableStateOf(buildAnnotatedString { append("${file.absolutePath}[${file.length().byteSize}]") }) }
    var chunks by remember { mutableStateOf<List<Chunk>>(emptyList()) }
    var error by remember { mutableStateOf<Throwable?>(null) }
    LaunchedEffect(file) {
        error = runCatching {
            val loadedArsc = loadArsc(file)
            chunks = loadedArsc.resourceTable.chunks
            fileDesc = loadedArsc.describeAsAnnotated()
        }.exceptionOrNull()
    }

    Column(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            Button(onClick = { onEjectClick(file) }) { Text("Eject") }
            Button(onClick = { onDiffClick(file) }) { Text("Diff with...") }
        }

        Text(fileDesc)

        Divider()

        Column(
            modifier = Modifier.verticalScroll(rememberScrollState())
        ) {
            chunks.forEach {
                ChunkView(
                    modifier = Modifier.border(1.dp, MaterialTheme.colors.onBackground).fillMaxWidth().padding(8.dp),
                    chunk = it
                )
            }
        }
    }

    if (error != null) {
        Text("Error: ${error?.message}")
    }
}

@Composable
internal fun ChunkView(chunk: Chunk, modifier: Modifier = Modifier) {
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

@Composable
internal fun ResourceTableChunkView(chunk: ResourceTableChunk, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text("RESOURCE TABLE[${chunk.originalChunkSize.byteSize}]", style = MaterialTheme.typography.h6)
        Text("Header[${chunk.headerSize.byteSize}] packageCount=${chunk.packages.size}")
        Text("Payload[${(chunk.originalChunkSize - chunk.headerSize).byteSize}]")

        chunk.chunks.values.forEach { childChunk ->
            when (childChunk) {
                is StringPoolChunk -> {
                    StringPoolChunkView(
                        modifier = Modifier.border(1.dp, MaterialTheme.colors.onBackground).padding(8.dp),
                        chunk = childChunk,
                        name = "Global string pool",
                        titleTip = "Global string pool contains values that can be referenced by resource entries",
                        extraContent = {
                            var notUsedStrings by remember { mutableStateOf(emptyList<String>()) }
                            LaunchedEffect(chunk) {
                                val stringIndexes = chunk.packages
                                    .asSequence()
                                    .flatMap { it.typeChunks }
                                    .flatMap { it.entries.values }
                                    .flatMap { if (it.isComplex) it.values().values else listOf(it.value()) }
                                    .filter { it.type() == BinaryResourceValue.Type.STRING }
                                    .map { it.data() }
                                    .toSet()

                                val notUsedStringIndexes = (0 until chunk.stringPool.stringCount).toSet() - stringIndexes
                                notUsedStrings = notUsedStringIndexes.map { chunk.stringPool.getString(it) }
                            }
                            if (notUsedStrings.isNotEmpty()) {
                                NotUsedStringsView(
                                    modifier = Modifier.border(1.dp, MaterialTheme.colors.onBackground).fillMaxWidth().padding(8.dp),
                                    title = "Not used strings",
                                    titleTip = "Not used strings are values that are not referenced by any of the resource entries",
                                    strings = notUsedStrings
                                )
                            }
                        }
                    )
                }

                else -> ChunkView(
                    modifier = Modifier.border(1.dp, MaterialTheme.colors.primary).padding(8.dp),
                    chunk = childChunk,
                )
            }
        }
    }
}

@Composable
internal fun PackageChunkView(chunk: PackageChunk, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
    ) {
        Text("PACKAGE[${chunk.originalChunkSize.byteSize}] ${chunk.packageName}", style = MaterialTheme.typography.h6)
        Text("Header[${chunk.headerSize.byteSize}] id=${String.format("0x%02x", chunk.id)} name=${chunk.packageName}")
        Text("Payload[${(chunk.originalChunkSize - chunk.headerSize).byteSize}]")
        Text("Resource type count=${chunk.typeSpecChunks.size}")

        StringPoolChunkView(
            modifier = Modifier.border(1.dp, MaterialTheme.colors.onBackground).fillMaxWidth().padding(8.dp),
            chunk = chunk.keyStringPool,
            name = "Key string pool",
            titleTip = "Key string pool contains resource keys, that is, resource identifiers",
            extraContent = {
                var notUsedKeys by remember { mutableStateOf(emptyList<String>()) }
                LaunchedEffect(chunk) {
                    val keyIndexes = chunk.typeChunks
                        .asSequence()
                        .flatMap { it.entries.values }
                        .map { it.keyIndex() }
                        .toSet()

                    val notUsedKeyIndexes = (0 until chunk.keyStringPool.stringCount).toSet() - keyIndexes
                    notUsedKeys = notUsedKeyIndexes.map { chunk.keyStringPool.getString(it) }
                }
                if (notUsedKeys.isNotEmpty()) {
                    NotUsedStringsView(
                        modifier = Modifier.border(1.dp, MaterialTheme.colors.onBackground).fillMaxWidth().padding(8.dp),
                        title = "Not used keys",
                        titleTip = "Not used key are resource identifiers not used by any of the resource entries",
                        strings = notUsedKeys
                    )
                }
            }
        )

        StringPoolChunkView(
            modifier = Modifier.border(1.dp, MaterialTheme.colors.onBackground).fillMaxWidth().padding(8.dp),
            chunk = chunk.typeStringPool,
            name = "Type string pool",
            titleTip = "Type string pool contains resource type names"
        )

        chunk.typeSpecChunks.forEach {
            ResourceTypeView(
                modifier = Modifier.border(1.dp, MaterialTheme.colors.onBackground).fillMaxWidth().padding(8.dp),
                typeSpecChunk = it,
                typeChunks = chunk.getTypeChunks(
                    it.typeName
                )
            )
        }
    }
}

@Composable
internal fun NotUsedStringsView(title: String, strings: List<String>, modifier: Modifier = Modifier, titleTip: String? = null) {
    Row(modifier = modifier) {
        Column(modifier = Modifier.weight(1f)) {
            val size = remember { strings.sumOf { it.encodeToByteArray().size } }
            TextWithTip(text = "$title[${size.byteSize}]", tip = titleTip, style = MaterialTheme.typography.h6)
        }

        StringListWithFilter(
            modifier = Modifier.border(1.dp, MaterialTheme.colors.onBackground).weight(1f).heightIn(max = 250.dp).padding(8.dp),
            title = "Strings",
            strings = strings
        )
    }
}

@Composable
internal fun StringListWithFilter(title: String, strings: List<String>, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        var filteredStrings by remember { mutableStateOf(emptyList<String>()) }

        val filteredCountText = if (filteredStrings.size != strings.size) "${filteredStrings.size}/" else ""
        Text("$title[$filteredCountText${strings.size}]")
        var filter by remember { mutableStateOf("") }
        TextField(
            modifier = Modifier.fillMaxWidth(),
            value = filter,
            onValueChange = { filter = it },
            singleLine = true
        )

        LaunchedEffect(filter, strings) {
            filteredStrings = strings.filter { it.contains(filter, ignoreCase = true) }
        }

        SelectionContainer {
            LazyColumn {
                itemsIndexed(filteredStrings) { index, it ->
                    if (index > 0) {
                        Divider()
                    }
                    Text(it)
                }
            }
        }
    }
}

@Composable
internal fun ResourceTypeView(typeSpecChunk: TypeSpecChunk, typeChunks: Collection<TypeChunk>, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
    ) {
        val totalSize = (typeSpecChunk.originalChunkSize + typeChunks.sumOf { it.originalChunkSize }).byteSize
        Text("Resource Type[$totalSize]: ${typeSpecChunk.typeName}", style = MaterialTheme.typography.h6)
        Text("Config count=${typeChunks.size}: ${typeChunks.joinToString { it.typeConfig }}")
        Text("Entry count per config=${typeSpecChunk.resourceCount}")

        val totalEntryCount = typeChunks.sumOf { it.totalEntryCount }
        val entryCount = remember(typeChunks) { typeChunks.sumOf { it.entries.size } }
        Text("Resolved entry count from all configs=$entryCount")
        val nullEntryCount = totalEntryCount - entryCount
        TextWithTip(
            text = "Null entry count from all configs=$nullEntryCount[${(nullEntryCount * 4).byteSize}]",
            tip = "Null entries are entries that only have offset (4B in size, not including values that might be referenced by them) but not payload, resourceShrinking can only remove payload but not offset, because removing offset will change the resource ID value"
        )
        Text("Total entry count from all configs=$totalEntryCount")

        val allKeys = remember(typeChunks) {
            typeChunks.map { it.entries.keys }.fold(mutableSetOf<Int>()) { acc, keys ->
                acc.addAll(keys)
                acc
            }
        }
        val notUsedKeys = remember(typeSpecChunk) { (0 until typeSpecChunk.resourceCount).toSet() - allKeys }
        val notUsedEntryCount = notUsedKeys.size * typeChunks.size
        TextWithTip(
            text = "Removable null entries from all configs=${notUsedEntryCount}[${(notUsedEntryCount * 4).byteSize}]",
            tip = "Removable null entries are entries that have no payload in any of the configurations"
        )

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
internal fun TypeChunkView(chunk: TypeChunk, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
    ) {
        val sizeText = remember(chunk) {
            val bytes = chunk.toByteArray()
            if (bytes.size == chunk.originalChunkSize) {
                "[${chunk.originalChunkSize.byteSize}]"
            } else {
                "$[${chunk.originalChunkSize.byteSize}][*${bytes.size.byteSize}]"
            }
        }
        TextWithTip(
            text = "TYPE$sizeText: ${chunk.typeName}-${chunk.configuration}",
            tip = "Type chunk can be divided into 3 parts, header(id, resCount, config), offsets(4B int for each entry), and resource values(each one have variable length, one entry can have multiple values(isComplex=true))",
            style = MaterialTheme.typography.h6
        )
        Row {
            val entries = remember(chunk) { chunk.entries.entries.toList() }
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    "Header[${chunk.headerSize.byteSize}] id=${
                        String.format(
                            "0x%02x",
                            chunk.id
                        )
                    }, entryCount=${chunk.totalEntryCount} config=${chunk.configuration}"
                )
                Text("Payload[${(chunk.originalChunkSize - chunk.headerSize).byteSize}]")
                Text("Resolved entry count=${entries.size}")
                val nullEntryCount = chunk.totalEntryCount - entries.size
                TextWithTip(
                    text = "Null entry count=${chunk.totalEntryCount - entries.size}[${(nullEntryCount * 4).byteSize}]",
                    tip = "Null entries are entries that only have offset (4B in size, not including values that might be referenced by them) but not payload, resourceShrinking can only remove payload but not offset, because removing offset will change the resource ID value"
                )
                Text("Total entry count=${chunk.totalEntryCount}")
            }

            StringListWithFilter(
                modifier = Modifier.border(1.dp, MaterialTheme.colors.onBackground).weight(1f).heightIn(max = 400.dp).padding(8.dp),
                title = "Resolved entries",
                strings = entries.map { it.describe(chunk.packageChunk.id, chunk.id) }
            )
        }
    }
}

@Composable
internal fun StringPoolChunkView(
    chunk: StringPoolChunk,
    modifier: Modifier = Modifier,
    name: String = "",
    titleTip: String? = null,
    extraContent: @Composable (() -> Unit)? = null,
) {
    Column(
        modifier = modifier,
    ) {
        TextWithTip(text = "STRING POOL[${chunk.originalChunkSize.byteSize}]: $name", style = MaterialTheme.typography.h6, tip = titleTip)

        Row {
            Column(modifier = Modifier.weight(1f)) {
                Text("Header[${chunk.headerSize.byteSize}] stringCount=${chunk.stringCount}, styleCount=${chunk.styleCount}, stringType=${chunk.stringType}, isSorted=${chunk.isSorted}")
                Text("Payload[${(chunk.originalChunkSize - chunk.headerSize).byteSize}]")
            }

            if (chunk.stringCount > 0) {
                StringListWithFilter(
                    modifier = Modifier.border(1.dp, MaterialTheme.colors.onBackground).weight(1f).heightIn(max = 400.dp).padding(8.dp),
                    title = "Strings",
                    strings = (0 until chunk.stringCount).map { chunk.getString(it) }
                )
            }

            if (chunk.styleCount > 0) {
                StringListWithFilter(
                    modifier = Modifier.border(1.dp, MaterialTheme.colors.onBackground).weight(1f).heightIn(max = 400.dp).padding(8.dp),
                    title = "Styles",
                    strings = (0 until chunk.styleCount).map { chunk.getStyle(it).toString() }
                )
            }
        }

        if (extraContent != null) {
            extraContent()
        }
    }
}

@Composable
internal fun ChunkWithChunksView(chunk: ChunkWithChunks, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
    ) {
        Text("${chunk.javaClass.simpleName}[${chunk.originalChunkSize.byteSize}]", style = MaterialTheme.typography.h6)
        Text("Header[${chunk.headerSize.byteSize}]")
        Text("Payload[${(chunk.originalChunkSize - chunk.headerSize).byteSize}]")
        chunk.chunks.values.forEach {
            ChunkView(
                modifier = Modifier.border(1.dp, MaterialTheme.colors.onBackground).fillMaxWidth().padding(8.dp),
                chunk = it
            )
        }
    }
}

@Composable
internal fun TypeSpecChunkView(chunk: TypeSpecChunk, modifier: Modifier = Modifier, configCount: Int? = null) {
    Column(
        modifier = modifier,
    ) {
        Text("TYPE SPEC[${chunk.originalChunkSize.byteSize}]: ${chunk.typeName}", style = MaterialTheme.typography.h6)
        Text("Header[${chunk.headerSize.byteSize}]: id=${String.format("0x%02x", chunk.id)} resCount=${chunk.resourceCount}")
        Text("Payload[${(chunk.originalChunkSize - chunk.headerSize).byteSize}]")
        if (configCount != null) {
            Text("Config count=$configCount")
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun TextWithTip(text: String, modifier: Modifier = Modifier, tip: String? = null, style: TextStyle = LocalTextStyle.current) {
    if (tip != null) {
        TooltipArea(
            modifier = modifier,
            tooltip = {
                Surface(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colors.onBackground)
                ) {
                    Text(tip)
                }
            }
        ) {
            Row {
                Text(modifier = Modifier.alignByBaseline(), text = text, style = style)
                Icon(modifier = Modifier.alignByBaseline(), imageVector = Icons.Outlined.Info, contentDescription = "info")
            }
        }
    } else {
        Text(modifier = modifier, text = text, style = style)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun TextWithTip(text: AnnotatedString, modifier: Modifier = Modifier, tip: AnnotatedString? = null, style: TextStyle = LocalTextStyle.current) {
    if (tip != null) {
        TooltipArea(
            modifier = modifier,
            tooltip = {
                Surface(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colors.onBackground)
                ) {
                    Text(tip)
                }
            }
        ) {
            Row {
                Text(modifier = Modifier.alignByBaseline(), text = text, style = style)
                Icon(modifier = Modifier.alignByBaseline(), imageVector = Icons.Outlined.Info, contentDescription = "info")
            }
        }
    } else {
        Text(modifier = modifier, text = text, style = style)
    }
}
