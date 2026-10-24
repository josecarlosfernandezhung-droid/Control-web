package com.tototools.catalogo.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import com.tototools.catalogo.CatalogoViewModel
import com.tototools.catalogo.data.Product
import com.tototools.catalogo.ui.theme.Brand
import com.tototools.catalogo.ui.theme.BrandDark
import com.tototools.catalogo.ui.theme.Muted
import com.tototools.catalogo.util.PhotoUtils
import java.io.File
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProductEditScreen(
    vm: CatalogoViewModel,
    productId: String?,
    onBack: () -> Unit,
    onShare: (Product) -> Unit = {}
) {
    val context = LocalContext.current
    val state by vm.state.collectAsState()
    val store = state.currentStore
    val existing = store?.prods?.find { it.id == productId }

    var nombre by remember { mutableStateOf(existing?.nombre ?: "") }
    var precio by remember { mutableStateOf(existing?.precio?.let { if (it == 0.0) "" else it.toString() } ?: "") }
    var sku by remember { mutableStateOf(existing?.sku ?: "") }
    var desc by remember { mutableStateOf(existing?.desc ?: "") }
    var catId by remember { mutableStateOf(existing?.cat ?: "") }
    var visible by remember { mutableStateOf(existing?.vis ?: true) }
    var dest by remember { mutableStateOf(existing?.dest ?: false) }
    var disp by remember { mutableStateOf(existing?.disp ?: true) }
    var unidad by remember { mutableStateOf(existing?.unidad ?: "") }
    var stock by remember { mutableStateOf(existing?.stock?.toString() ?: "") }
    var ofertaPrecio by remember {
        mutableStateOf(existing?.ofertaPrecio?.let { if (it == 0.0) "" else it.toString() } ?: "")
    }
    var ofertaFin by remember { mutableStateOf(existing?.ofertaFin ?: "") }
    var fotos by remember { mutableStateOf(existing?.fotos ?: emptyList()) }

    var showDelete by remember { mutableStateOf(false) }
    var showPhotoSource by remember { mutableStateOf(false) }
    var cameraFile by remember { mutableStateOf<File?>(null) }

    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null && fotos.size < 5) {
            PhotoUtils.saveCompressed(context, uri)?.let { path ->
                fotos = fotos + path
            }
        }
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        if (success) {
            cameraFile?.let { file ->
                val uri = Uri.fromFile(file)
                if (fotos.size < 5) {
                    PhotoUtils.saveCompressed(context, uri)?.let { path ->
                        fotos = fotos + path
                    }
                }
                file.delete() // temp
            }
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { }

    fun ensurePermissions(forCamera: Boolean, then: () -> Unit) {
        val needed = mutableListOf<String>()
        if (forCamera && ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA)
            != PackageManager.PERMISSION_GRANTED
        ) needed += Manifest.permission.CAMERA
        if (Build.VERSION.SDK_INT >= 33) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_IMAGES)
                != PackageManager.PERMISSION_GRANTED
            ) needed += Manifest.permission.READ_MEDIA_IMAGES
        } else if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE)
            != PackageManager.PERMISSION_GRANTED
        ) {
            needed += Manifest.permission.READ_EXTERNAL_STORAGE
        }
        if (needed.isEmpty()) then() else permissionLauncher.launch(needed.toTypedArray())
    }

    fun openCamera() {
        ensurePermissions(forCamera = true) {
            val tmp = PhotoUtils.createTempCameraFile(context)
            cameraFile = tmp
            cameraLauncher.launch(PhotoUtils.uriForFile(context, tmp))
        }
    }

    fun openGallery() {
        ensurePermissions(forCamera = false) {
            galleryLauncher.launch("image/*")
        }
    }

    fun buildProduct(): Product {
        val price = precio.replace(',', '.').toDoubleOrNull() ?: 0.0
        val oferta = ofertaPrecio.replace(',', '.').toDoubleOrNull() ?: 0.0
        return Product(
            id = productId ?: UUID.randomUUID().toString(),
            nombre = nombre.trim(),
            precio = price,
            ofertaPrecio = oferta,
            ofertaFin = ofertaFin.trim(),
            sku = sku.trim(),
            desc = desc.trim(),
            unidad = unidad.trim(),
            stock = stock.toIntOrNull(),
            cat = catId,
            vis = visible,
            disp = disp,
            dest = dest,
            fotos = fotos
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (productId == null) "Nuevo producto" else "Editar producto") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = BrandDark,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary,
                    actionIconContentColor = MaterialTheme.colorScheme.onPrimary
                ),
                actions = {
                    if (productId != null) {
                        IconButton(onClick = { onShare(buildProduct()) }) {
                            Icon(Icons.Default.Share, contentDescription = "Compartir")
                        }
                        IconButton(onClick = { showDelete = true }) {
                            Icon(Icons.Default.Delete, contentDescription = "Eliminar")
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text("Fotos (máx. 5)", style = MaterialTheme.typography.labelLarge, color = Muted)

            Row(
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                fotos.forEachIndexed { index, path ->
                    Box(
                        Modifier
                            .size(96.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        AsyncImage(
                            model = File(path),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                        IconButton(
                            onClick = {
                                PhotoUtils.deleteIfLocal(path)
                                fotos = fotos.filterIndexed { i, _ -> i != index }
                            },
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .size(28.dp)
                                .background(MaterialTheme.colorScheme.error, CircleShape)
                        ) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "Quitar",
                                tint = MaterialTheme.colorScheme.onError,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        if (index == 0) {
                            Text(
                                "Principal",
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .background(Brand.copy(alpha = 0.85f))
                                    .padding(horizontal = 6.dp, vertical = 2.dp),
                                color = MaterialTheme.colorScheme.onPrimary,
                                fontSize = 10.sp
                            )
                        }
                    }
                }

                if (fotos.size < 5) {
                    Box(
                        Modifier
                            .size(96.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .border(2.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(14.dp))
                            .clickable { showPhotoSource = true },
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.AddAPhoto, null, tint = Brand)
                            Text("Añadir", fontSize = 12.sp, color = Muted)
                        }
                    }
                }
            }

            OutlinedTextField(
                value = nombre,
                onValueChange = { nombre = it },
                label = { Text("Nombre") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            OutlinedTextField(
                value = precio,
                onValueChange = { precio = it.filter { ch -> ch.isDigit() || ch == '.' || ch == ',' } },
                label = { Text("Precio (${store?.mp ?: "CUP"})") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = ofertaPrecio,
                    onValueChange = { ofertaPrecio = it.filter { ch -> ch.isDigit() || ch == '.' || ch == ',' } },
                    label = { Text("Precio oferta") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                )
                OutlinedTextField(
                    value = ofertaFin,
                    onValueChange = { ofertaFin = it },
                    label = { Text("Oferta hasta (AAAA-MM-DD)") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    placeholder = { Text("2026-12-31") }
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = unidad,
                    onValueChange = { unidad = it },
                    label = { Text("Unidad (kg, ud…)") },
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )
                OutlinedTextField(
                    value = stock,
                    onValueChange = { stock = it.filter { ch -> ch.isDigit() } },
                    label = { Text("Stock (opcional)") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
            }
            OutlinedTextField(
                value = sku,
                onValueChange = { sku = it },
                label = { Text("SKU / código (opcional)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            OutlinedTextField(
                value = desc,
                onValueChange = { desc = it },
                label = { Text("Descripción (opcional)") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3
            )

            val cats = store?.cats ?: emptyList()
            if (cats.isNotEmpty()) {
                var expanded by remember { mutableStateOf(false) }
                ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
                    OutlinedTextField(
                        value = cats.find { it.id == catId }?.n ?: "Sin categoría",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Categoría") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                        modifier = Modifier.menuAnchor().fillMaxWidth()
                    )
                    ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        DropdownMenuItem(
                            text = { Text("Sin categoría") },
                            onClick = { catId = ""; expanded = false }
                        )
                        cats.forEach { c ->
                            DropdownMenuItem(
                                text = { Text(c.n) },
                                onClick = { catId = c.id; expanded = false }
                            )
                        }
                    }
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = visible, onCheckedChange = { visible = it })
                Text("Visible en el catálogo")
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = dest, onCheckedChange = { dest = it })
                Text("Destacado")
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = disp, onCheckedChange = { disp = it })
                Text("Disponible / en stock")
            }

            Spacer(Modifier.height(8.dp))

            Button(
                onClick = {
                    val product = buildProduct()
                    if (productId == null) vm.addProduct(product) else vm.updateProduct(product)
                    onBack()
                },
                modifier = Modifier.fillMaxWidth().height(50.dp),
                enabled = nombre.isNotBlank()
            ) {
                Text(if (productId == null) "Crear producto" else "Guardar cambios")
            }
        }
    }

    if (showPhotoSource) {
        AlertDialog(
            onDismissRequest = { showPhotoSource = false },
            title = { Text("Añadir foto") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(
                        onClick = { showPhotoSource = false; openCamera() },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.PhotoCamera, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Cámara")
                    }
                    TextButton(
                        onClick = { showPhotoSource = false; openGallery() },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.PhotoLibrary, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Galería")
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showPhotoSource = false }) { Text("Cancelar") }
            }
        )
    }

    if (showDelete && productId != null) {
        AlertDialog(
            onDismissRequest = { showDelete = false },
            title = { Text("Eliminar producto") },
            text = { Text("¿Seguro que quieres borrar «${existing?.nombre}»?") },
            confirmButton = {
                TextButton(onClick = {
                    PhotoUtils.deleteAll(existing?.fotos ?: emptyList())
                    vm.deleteProduct(productId)
                    showDelete = false
                    onBack()
                }) { Text("Eliminar") }
            },
            dismissButton = {
                TextButton(onClick = { showDelete = false }) { Text("Cancelar") }
            }
        )
    }
}
