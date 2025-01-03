package org.example.project.diff

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Button
import androidx.compose.material.Divider
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.unit.dp
import com.google.devrel.gmscore.tools.apk.arsc.PackageChunk
import com.google.devrel.gmscore.tools.apk.arsc.ResourceTableChunk
import com.google.devrel.gmscore.tools.apk.arsc.StringPoolChunk
import com.google.devrel.gmscore.tools.apk.arsc.TypeChunk
import com.google.devrel.gmscore.tools.apk.arsc.TypeSpecChunk
import org.example.project.LoadedArsc
import org.example.project.byteSize
import org.example.project.describe
import org.example.project.describeAsAnnotated
import org.example.project.info.StringListWithFilter
import org.example.project.info.TextWithTip
import org.example.project.loadArsc
import org.example.project.typeConfig
import java.io.File

internal data class LoadedArscDiff(val baseArsc: LoadedArsc, val newArsc: LoadedArsc)

@Composable
internal fun ArscDiffView(baseFile: File, newFile: File, onClickExit: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            Button(onClick = onClickExit) { Text("Exit diff") }
        }

        var loadedDiff by remember { mutableStateOf<LoadedArscDiff?>(null) }
        var error by remember { mutableStateOf<Throwable?>(null) }
        LaunchedEffect(baseFile, newFile) {
            error = runCatching {
                val baseArsc = loadArsc(baseFile)
                val newArsc = loadArsc(newFile)

                loadedDiff = LoadedArscDiff(baseArsc, newArsc)
            }.exceptionOrNull()
        }

        if (error != null) {
            Text("Error: ${error?.message}")
        }

        loadedDiff.let { diff ->
            if (diff != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(modifier = Modifier.weight(1f), text = diff.baseArsc.describeAsAnnotated())
                    Text(text = " <=> ")
                    Text(modifier = Modifier.weight(1f), text = diff.newArsc.describeAsAnnotated())
                }

                Divider()

                val baseResTable = diff.baseArsc.resourceTable.chunks.filterIsInstance<ResourceTableChunk>().first()
                val newResTable = diff.newArsc.resourceTable.chunks.filterIsInstance<ResourceTableChunk>().first()
                Box(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    ResourceTableDiffView(
                        modifier = Modifier.border(1.dp, MaterialTheme.colors.onBackground).padding(8.dp),
                        baseChunk = baseResTable,
                        newChunk = newResTable
                    )
                }
            }
        }
    }
}

@Composable
internal fun ResourceTableDiffView(baseChunk: ResourceTableChunk, newChunk: ResourceTableChunk, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        val sizeDiff = newChunk.originalChunkSize - baseChunk.originalChunkSize
        val titleText = remember(baseChunk, newChunk) {
            buildAnnotatedString {
                append("RESOURCE TABLE[${baseChunk.originalChunkSize.byteSize} <=> ${newChunk.originalChunkSize.byteSize}]")
                append("[")
                append(sizeDiff.byteSize.toAnnotatedDiffString())
                append("]")
            }
        }
        Text(
            titleText,
            style = MaterialTheme.typography.h6
        )

        val headerSizeDiff = newChunk.headerSize - baseChunk.headerSize
        val headerText = remember(baseChunk, newChunk) {
            buildAnnotatedString {
                append("Header[${baseChunk.headerSize.byteSize} <=> ${newChunk.headerSize.byteSize}]")
                append("[")
                append(headerSizeDiff.byteSize.toAnnotatedDiffString())
                append("]")
            }
        }
        Text(headerText)

        StringPoolDiffView(
            modifier = Modifier.border(1.dp, MaterialTheme.colors.onBackground).padding(8.dp),
            baseChunk = baseChunk.stringPool,
            newChunk = newChunk.stringPool,
            name = "Global String pool"
        )

        if (baseChunk.packages.size == 1 && newChunk.packages.size == 1) {
            PackageDiffView(
                modifier = Modifier.border(1.dp, MaterialTheme.colors.onBackground).padding(8.dp),
                baseChunk = baseChunk.packages.first(),
                newChunk = newChunk.packages.first()
            )
        } else {
            val basePackageNames = baseChunk.packages.map { it.packageName }
            val newPackageNames = newChunk.packages.map { it.packageName }

            val allPackageNames = basePackageNames.toSet() + newPackageNames.toSet()

            allPackageNames.forEach { packageName ->
                PackageDiffView(
                    modifier = Modifier.border(1.dp, MaterialTheme.colors.onBackground).padding(8.dp),
                    baseChunk = baseChunk.getPackage(packageName),
                    newChunk = newChunk.getPackage(packageName),
                )
            }
        }
    }
}

@Composable
internal fun TypeConfigDiffView(baseChunk: TypeChunk?, newChunk: TypeChunk?, modifier: Modifier = Modifier) {
    Row(modifier = modifier.heightIn(max = 400.dp)) {
        val baseEntries = baseChunk?.entries ?: emptyMap()
        val newEntries = newChunk?.entries ?: emptyMap()
        Column(modifier = Modifier.weight(1f)) {
            val baseSize = baseChunk?.originalChunkSize
            val newSize = newChunk?.originalChunkSize
            val sizeDiff = (newSize ?: 0) - (baseSize ?: 0)
            val name = baseChunk?.let { "${it.typeName}-${it.configuration}" } ?: newChunk?.let { "${it.typeName}-${it.configuration}" }
            val titleText = remember(baseSize, newSize) {
                buildAnnotatedString {
                    append("RESOURCE TYPE[${baseSize?.byteSize} <=> ${newSize?.byteSize}]")
                    append("[")
                    append(sizeDiff.byteSize.toAnnotatedDiffString())
                    append("]:$name")
                }
            }
            Text(
                titleText,
                style = MaterialTheme.typography.h6
            )

            val headerSizeDiff = (newChunk?.headerSize ?: 0) - (baseChunk?.headerSize ?: 0)
            val headerText = remember(baseChunk, newChunk) {
                buildAnnotatedString {
                    append("Header[${baseChunk?.headerSize?.byteSize} <=> ${newChunk?.headerSize?.byteSize}]")
                    append("[")
                    append(headerSizeDiff.byteSize.toAnnotatedDiffString())
                    append("]")
                }
            }
            Text(headerText)

            val entryCountText = remember(baseEntries, newEntries) {
                buildAnnotatedString {
                    append("Entry count=[${baseEntries.size} <=> ${newEntries.size}]")
                    append("[")
                    append((newEntries.size - baseEntries.size).toAnnotatedDiffString())
                    append("]")
                }
            }
            TextWithTip(
                text = entryCountText,
                tip = buildAnnotatedString { append("When arsc is optimized by aapt with identifier obfuscate and value dedupe, " +
                        "multiple entries can point to same value, for example, 100 bool entries can point to same 2 values (true or false), " +
                        "so even if entries counts are the same, their size can differ") }
            )

            val baseNullEntries = baseChunk?.let { it.totalEntryCount - baseEntries.size }
            val newNullEntries = newChunk?.let { it.totalEntryCount - newEntries.size }
            val nullEntryDiff = (newNullEntries ?: 0) - (baseNullEntries ?: 0)
            val nullEntryCountText = remember(baseNullEntries, newNullEntries) {
                buildAnnotatedString {
                    append("Null entry count=[${baseNullEntries} <=> ${newNullEntries}]")
                    append("[")
                    append(nullEntryDiff.toAnnotatedDiffString())
                    append("]")
                }
            }
            Text(nullEntryCountText)

            val totalEntryDiff = (newChunk?.totalEntryCount ?: 0) - (baseChunk?.totalEntryCount ?: 0)
            val totalEntryText = remember(baseChunk, newChunk) {
                buildAnnotatedString {
                    append("Total entry count=[${baseChunk?.totalEntryCount} <=> ${newChunk?.totalEntryCount}]")
                    append("[")
                    append(totalEntryDiff.toAnnotatedDiffString())
                    append("]")
                }
            }
            Text(totalEntryText)
        }

        var diffEntries by remember { mutableStateOf(false) }
        if (diffEntries) {

            // diff entries
            val baseEntryDescList = remember { baseEntries.map { it.describe() } }

            val newEntryDescList = remember { newEntries.map { it.describe() } }
            val addedEntries = remember { newEntryDescList - baseEntryDescList.toSet() }
            val removedEntries = remember { baseEntryDescList - newEntryDescList.toSet() }

            StringListWithFilter(
                modifier = Modifier.border(1.dp, MaterialTheme.colors.onBackground).weight(1f).heightIn(max = 400.dp).padding(8.dp),
                title = "Removed",
                strings = removedEntries
            )

            StringListWithFilter(
                modifier = Modifier.border(1.dp, MaterialTheme.colors.onBackground).weight(1f).heightIn(max = 400.dp).padding(8.dp),
                title = "Added",
                strings = addedEntries
            )
        } else {
            Button(onClick = { diffEntries = true }) {
                Text("Diff entries")
            }
        }
    }
}

@Composable
internal fun PackageDiffView(baseChunk: PackageChunk?, newChunk: PackageChunk?, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        val sizeDiff = (newChunk?.originalChunkSize ?: 0) - (baseChunk?.originalChunkSize ?: 0)
        val titleText = remember(baseChunk, newChunk) {
            buildAnnotatedString {
                append("PACKAGE[${baseChunk?.originalChunkSize?.byteSize} <=> ${newChunk?.originalChunkSize?.byteSize}]")
                append("[")
                append(sizeDiff.byteSize.toAnnotatedDiffString())
                append("]:${baseChunk?.packageName ?: newChunk?.packageName}")
            }
        }
        Text(
            titleText,
            style = MaterialTheme.typography.h6
        )

        val headerSizeDiff = (newChunk?.headerSize ?: 0) - (baseChunk?.headerSize ?: 0)
        val headerText = remember(baseChunk, newChunk) {
            buildAnnotatedString {
                append("Header[${baseChunk?.headerSize?.byteSize} <=> ${newChunk?.headerSize?.byteSize}]")
                append("[")
                append(headerSizeDiff.byteSize.toAnnotatedDiffString())
                append("]")
            }
        }
        Text(headerText)

        StringPoolDiffView(
            modifier = Modifier.border(1.dp, MaterialTheme.colors.onBackground).padding(8.dp),
            baseChunk = baseChunk?.keyStringPool,
            newChunk = newChunk?.keyStringPool,
            name = "Key string pool"
        )

        StringPoolDiffView(
            modifier = Modifier.border(1.dp, MaterialTheme.colors.onBackground).padding(8.dp),
            baseChunk = baseChunk?.typeStringPool,
            newChunk = newChunk?.typeStringPool,
            name = "Type string pool"
        )

        ((baseChunk?.typeSpecChunks?.toList() ?: emptyList()) + (newChunk?.typeSpecChunks?.toList() ?: emptyList()))
            .map { it.typeName }
            .toSet()
            .forEach { type ->
                val baseTypeSpec = remember { kotlin.runCatching { baseChunk?.getTypeSpecChunk(type) }.getOrNull() }
                val newTypeSpec = remember { runCatching { newChunk?.getTypeSpecChunk(type) }.getOrNull() }
                val baseTypeChunks = baseChunk?.getTypeChunks(type)?.toList() ?: emptyList()
                val newTypeChunks = newChunk?.getTypeChunks(type)?.toList() ?: emptyList()
                ResourceTypeDiffView(
                    modifier = Modifier.border(1.dp, MaterialTheme.colors.onBackground).fillMaxWidth().padding(8.dp),
                    baseChunk = baseTypeSpec,
                    baseTypeChunks = baseTypeChunks,
                    newChunk = newTypeSpec,
                    newTypeChunks = newTypeChunks
                )
            }
    }
}

@Composable
internal fun ResourceTypeDiffView(
    baseChunk: TypeSpecChunk?,
    baseTypeChunks: List<TypeChunk>,
    newChunk: TypeSpecChunk?,
    newTypeChunks: List<TypeChunk>,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        val baseSize = remember(baseChunk, baseTypeChunks) { (baseTypeChunks + baseChunk).filterNotNull().sumOf { it.originalChunkSize } }
        val newSize = remember(newChunk, newTypeChunks) { (newTypeChunks + newChunk).filterNotNull().sumOf { it.originalChunkSize } }
        val sizeDiff = newSize - baseSize
        val typeName = baseChunk?.typeName ?: newChunk?.typeName
        val titleText = remember(baseSize, newSize) {
            buildAnnotatedString {
                append("RESOURCE TYPE[${baseSize.byteSize} <=> ${newSize.byteSize}]")
                append("[")
                append(sizeDiff.byteSize.toAnnotatedDiffString())
                append("]:$typeName")
            }
        }
        Text(
            titleText,
            style = MaterialTheme.typography.h6
        )

        val baseResCount = baseChunk?.resourceCount
        val newResCount = newChunk?.resourceCount
        val resCountDiff = (newResCount ?: 0) - (baseResCount ?: 0)
        val resCountText = remember(baseResCount, newResCount) {
            buildAnnotatedString {
                append("Resource count=[${baseResCount} <=> ${newResCount}]")
                append("[")
                append(resCountDiff.toAnnotatedDiffString())
                append("]")
            }
        }
        Text(resCountText)

        var expanded by remember { mutableStateOf(false) }
        Button(onClick = { expanded = !expanded }) {
            Text(if (expanded) "Collapse" else "Expand")
        }

        if (expanded) {
            TypeSpecDiffView(
                modifier = Modifier.border(1.dp, MaterialTheme.colors.onBackground).fillMaxWidth().padding(8.dp),
                baseChunk = baseChunk,
                newChunk = newChunk
            )

            val allTypeConfigNames = remember(baseTypeChunks, newTypeChunks) { (baseTypeChunks + newTypeChunks).map { it.typeConfig }.toSet() }
            allTypeConfigNames.forEach { typeConfig ->
                val baseType = remember(baseTypeChunks) { baseTypeChunks.firstOrNull { it.typeConfig == typeConfig } }
                val newType = remember(newTypeChunks) { newTypeChunks.firstOrNull { it.typeConfig == typeConfig } }
                TypeConfigDiffView(
                    modifier = Modifier.border(1.dp, MaterialTheme.colors.onBackground).padding(8.dp),
                    baseChunk = baseType,
                    newChunk = newType
                )
            }
        }
    }
}

@Composable
internal fun TypeSpecDiffView(baseChunk: TypeSpecChunk?, newChunk: TypeSpecChunk?, modifier: Modifier = Modifier) {
    Column(modifier) {
        val sizeDiff = (newChunk?.originalChunkSize ?: 0) - (baseChunk?.originalChunkSize ?: 0)
        val typeName = baseChunk?.typeName ?: newChunk?.typeName
        val titleText = remember(baseChunk, newChunk) {
            buildAnnotatedString {
                append("TYPE SPEC[${baseChunk?.originalChunkSize?.byteSize} <=> ${newChunk?.originalChunkSize?.byteSize}]")
                append("[")
                append(sizeDiff.byteSize.toAnnotatedDiffString())
                append("]:$typeName")
            }
        }
        Text(titleText, style = MaterialTheme.typography.h6)

        val headerSizeDiff = (newChunk?.headerSize ?: 0) - (baseChunk?.headerSize ?: 0)
        val headerText = remember(baseChunk, newChunk) {
            buildAnnotatedString {
                append("Header[${baseChunk?.headerSize?.byteSize} <=> ${newChunk?.headerSize?.byteSize}]")
                append("[")
                append(headerSizeDiff.byteSize.toAnnotatedDiffString())
                append("]")
            }
        }
        Text(headerText)
    }
}

@Composable
internal fun StringPoolDiffView(baseChunk: StringPoolChunk?, newChunk: StringPoolChunk?, name: String, modifier: Modifier = Modifier) {
    Row(modifier = modifier.heightIn(max = 400.dp)) {
        Column(modifier = Modifier.weight(1f)) {
            val sizeDiff = (newChunk?.originalChunkSize ?: 0) - (baseChunk?.originalChunkSize ?: 0)
            val titleText = remember(baseChunk, newChunk) {
                buildAnnotatedString {
                    append("STRING POOL[${baseChunk?.originalChunkSize?.byteSize} <=> ${newChunk?.originalChunkSize?.byteSize}]")
                    append("[")
                    append(sizeDiff.byteSize.toAnnotatedDiffString())
                    append("]:$name")
                }
            }
            Text(
                titleText,
                style = MaterialTheme.typography.h6
            )

            val headerSizeDiff = (newChunk?.headerSize ?: 0) - (baseChunk?.headerSize ?: 0)
            val headerText = remember(baseChunk, newChunk) {
                buildAnnotatedString {
                    append("Header[${baseChunk?.headerSize?.byteSize} <=> ${newChunk?.headerSize?.byteSize}]")
                    append("[")
                    append(headerSizeDiff.byteSize.toAnnotatedDiffString())
                    append("]")
                }
            }
            Text(headerText)

            val stringCountDiff = (newChunk?.stringCount ?: 0) - (baseChunk?.stringCount ?: 0)
            val stringCountText = remember(baseChunk, newChunk) {
                buildAnnotatedString {
                    append("String count=[${baseChunk?.stringCount} <=> ${newChunk?.stringCount}]")
                    append("[")
                    append(stringCountDiff.toAnnotatedDiffString())
                    append("]")
                }
            }
            Text(stringCountText)

            val styleCountDiff = (newChunk?.styleCount ?: 0) - (baseChunk?.styleCount ?: 0)
            val styleCountText = remember(baseChunk, newChunk) {
                buildAnnotatedString {
                    append("Style count=[${baseChunk?.styleCount} <=> ${newChunk?.styleCount}]")
                    append("[")
                    append(styleCountDiff.toAnnotatedDiffString())
                    append("]")
                }
            }
            Text(styleCountText)
        }

        var diffStrings by remember { mutableStateOf(false) }
        if (diffStrings) {
            val baseStrings = (0 until (baseChunk?.stringCount ?: 0)).map { baseChunk!!.getString(it) }
            val newStrings = (0 until (newChunk?.stringCount ?: 0)).map { newChunk!!.getString(it) }
            val addedStrings = newStrings - baseStrings.toSet()
            val removedStrings = baseStrings - newStrings.toSet()

            StringListWithFilter(
                modifier = Modifier.border(1.dp, MaterialTheme.colors.onBackground).weight(1f).heightIn(max = 400.dp).padding(8.dp),
                title = "Removed",
                strings = removedStrings
            )

            StringListWithFilter(
                modifier = Modifier.border(1.dp, MaterialTheme.colors.onBackground).weight(1f).heightIn(max = 400.dp).padding(8.dp),
                title = "Added",
                strings = addedStrings
            )
        } else {
            Button(onClick = { diffStrings = true }) {
                Text("Diff strings")
            }
        }
    }
}
