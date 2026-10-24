package com.tototools.catalogo.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tototools.catalogo.CatalogoViewModel
import com.tototools.catalogo.ui.theme.BrandDark
import com.tototools.catalogo.ui.theme.Muted

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    vm: CatalogoViewModel,
    onBack: () -> Unit
) {
    val state by vm.state.collectAsState()
    val store = state.currentStore
    val context = LocalContext.current
    val backupLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    val raw = input.bufferedReader().readText()
                    vm.importBackupJson(raw)
                }
            } catch (e: Exception) {
                // toast lo emite el ViewModel; fallback
            }
        }
    }
    val busy by vm.busy.collectAsState()
    val toast by vm.toast.collectAsState()

    var nombre by remember(store?.id) { mutableStateOf(store?.nombre ?: "") }
    var slug by remember(store?.id) { mutableStateOf(store?.slug ?: "") }
    var frase by remember(store?.id) { mutableStateOf(store?.frase ?: "") }
    var wa by remember(store?.id) { mutableStateOf(store?.wa ?: "") }
    var dir by remember(store?.id) { mutableStateOf(store?.dir ?: "") }
    var hor by remember(store?.id) { mutableStateOf(store?.hor ?: "") }
    var moneda by remember(store?.id) { mutableStateOf(store?.mp ?: "CUP") }
    var tasa by remember(store?.id) { mutableStateOf(store?.tasaCUP?.toString() ?: "1") }

    var fbUrl by remember(store?.id) { mutableStateOf(store?.fbUrl ?: "") }
    var fbKey by remember(store?.id) { mutableStateOf(store?.fbKey ?: "") }
    var fbEmail by remember(store?.id) { mutableStateOf(store?.fbEmail ?: "") }
    var fbPass by remember(store?.id) { mutableStateOf(store?.fbPass ?: "") }

    var newCat by remember { mutableStateOf("") }
    var newStoreName by remember { mutableStateOf("") }
    var showNewStore by remember { mutableStateOf(false) }
    var showDeleteStore by remember { mutableStateOf(false) }

    LaunchedEffect(toast) {
        if (toast != null) {
            kotlinx.coroutines.delay(3000)
            vm.clearToast()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Ajustes") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Volver")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = BrandDark,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        },
        snackbarHost = {
            if (toast != null) {
                Snackbar(Modifier.padding(16.dp)) { Text(toast!!) }
            }
        }
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Multi-tienda
            Text("Tiendas", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            state.stores.forEach { s ->
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = s.id == state.currentStoreId,
                        onClick = { vm.switchStore(s.id) }
                    )
                    Text(s.nombre, Modifier.weight(1f))
                    if (s.slug.isNotBlank()) Text("/${s.slug}", fontSize = 12.sp, color = Muted)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { showNewStore = true }) {
                    Icon(Icons.Default.Add, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Nueva tienda")
                }
                if (state.stores.size > 1) {
                    OutlinedButton(onClick = { showDeleteStore = true }) {
                        Icon(Icons.Default.Delete, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Eliminar actual")
                    }
                }
            }

            HorizontalDivider(Modifier.padding(vertical = 4.dp))
            Text("Datos de la tienda", fontWeight = FontWeight.Bold, fontSize = 16.sp)

            OutlinedTextField(nombre, { nombre = it }, label = { Text("Nombre") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            OutlinedTextField(
                slug,
                { slug = it.lowercase().filter { ch -> ch.isLetterOrDigit() || ch == '-' } },
                label = { Text("Slug (dirección pública)") },
                supportingText = { Text("Ej: mi-tienda") },
                modifier = Modifier.fillMaxWidth(), singleLine = true
            )
            OutlinedTextField(frase, { frase = it }, label = { Text("Frase / eslogan") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            OutlinedTextField(
                wa, { wa = it.filter { ch -> ch.isDigit() || ch == '+' } },
                label = { Text("WhatsApp (código de país)") },
                supportingText = { Text("Ej: 5351234567") },
                modifier = Modifier.fillMaxWidth(), singleLine = true
            )
            OutlinedTextField(dir, { dir = it }, label = { Text("Dirección") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            OutlinedTextField(hor, { hor = it }, label = { Text("Horario") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(moneda, { moneda = it.uppercase().take(5) }, label = { Text("Moneda") }, modifier = Modifier.weight(1f), singleLine = true)
                OutlinedTextField(tasa, { tasa = it.filter { ch -> ch.isDigit() || ch == '.' } }, label = { Text("Tasa CUP") }, modifier = Modifier.weight(1f), singleLine = true)
            }

            Button(
                onClick = {
                    vm.updateStore {
                        it.copy(
                            nombre = nombre.trim(),
                            slug = slug.trim(),
                            frase = frase.trim(),
                            wa = wa.trim(),
                            dir = dir.trim(),
                            hor = hor.trim(),
                            mp = moneda.trim().ifBlank { "CUP" },
                            tasaCUP = tasa.toDoubleOrNull() ?: 1.0
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Guardar datos de la tienda") }

            HorizontalDivider(Modifier.padding(vertical = 4.dp))
            Text("Firebase", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Text(
                "Misma configuración que en la versión web (Realtime Database + Auth email/contraseña).",
                fontSize = 12.sp, color = Muted
            )
            OutlinedTextField(fbUrl, { fbUrl = it }, label = { Text("Database URL") }, supportingText = { Text("https://xxx.firebaseio.com") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            OutlinedTextField(fbKey, { fbKey = it }, label = { Text("Web API Key") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            OutlinedTextField(fbEmail, { fbEmail = it }, label = { Text("Email") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            OutlinedTextField(
                fbPass, { fbPass = it }, label = { Text("Contraseña") },
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(), singleLine = true
            )
            Button(
                onClick = {
                    vm.updateStore {
                        it.copy(fbUrl = fbUrl.trim(), fbKey = fbKey.trim(), fbEmail = fbEmail.trim(), fbPass = fbPass)
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Guardar Firebase") }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { vm.testFirebase() }, enabled = busy == null) {
                    Text("Probar conexión")
                }
                OutlinedButton(onClick = { vm.syncFirebase() }, enabled = busy == null) {
                    Text("Sync pedidos")
                }
            }
            Button(
                onClick = { vm.publicarCatalogo() },
                enabled = busy == null,
                modifier = Modifier.fillMaxWidth()
            ) { Text("Publicar catálogo") }

            OutlinedButton(
                onClick = {
                    val rules = vm.firebaseRules()
                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    cm.setPrimaryClip(ClipData.newPlainText("rules", rules))
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Copiar reglas de Firebase") }

            if (busy != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                    Text(busy!!, fontSize = 13.sp)
                }
            }

            HorizontalDivider(Modifier.padding(vertical = 4.dp))
            Text("Categorías", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            val cats = store?.cats ?: emptyList()
            if (cats.isEmpty()) Text("No hay categorías", color = Muted, fontSize = 13.sp)
            else cats.forEach { c ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(c.n, Modifier.weight(1f))
                    IconButton(onClick = { vm.deleteCategory(c.id) }) {
                        Icon(Icons.Default.Delete, "Eliminar", tint = MaterialTheme.colorScheme.error)
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(newCat, { newCat = it }, label = { Text("Nueva categoría") }, modifier = Modifier.weight(1f), singleLine = true)
                IconButton(onClick = {
                    if (newCat.isNotBlank()) { vm.addCategory(newCat); newCat = "" }
                }, enabled = newCat.isNotBlank()) {
                    Icon(Icons.Default.Add, "Añadir")
                }
            }

            HorizontalDivider(Modifier.padding(vertical = 4.dp))
            Text("Importar / exportar", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Text(
                "Puedes importar el archivo datos.json de una copia de seguridad de la versión web (sin fotos).",
                color = Muted, fontSize = 12.sp
            )
            OutlinedButton(
                onClick = { backupLauncher.launch("application/json") },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Importar datos.json (backup web)") }
            OutlinedButton(
                onClick = {
                    // También aceptar */* por si el MIME no es json
                    backupLauncher.launch("*/*")
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Importar archivo…") }

            HorizontalDivider(Modifier.padding(vertical = 4.dp))
            Text("Compartir tienda", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            if (store?.wa?.isNotBlank() == true) {
                OutlinedButton(
                    onClick = {
                        val num = store.wa.filter { it.isDigit() }
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/$num")))
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Abrir WhatsApp de la tienda") }
            }
            var showQr by remember { mutableStateOf(false) }
            val qrData = remember(store?.wa, store?.slug, store?.nombre) {
                val num = store?.wa?.filter { it.isDigit() }.orEmpty()
                when {
                    num.isNotBlank() -> "https://wa.me/$num"
                    !store?.slug.isNullOrBlank() -> store!!.slug
                    else -> store?.nombre ?: "catalogo"
                }
            }
            OutlinedButton(
                onClick = { showQr = true },
                modifier = Modifier.fillMaxWidth(),
                enabled = qrData.isNotBlank()
            ) { Text("Mostrar QR (WhatsApp / slug)") }
            if (showQr) {
                AlertDialog(
                    onDismissRequest = { showQr = false },
                    title = { Text("Código QR") },
                    text = {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            // QR generado online (sin librería extra)
                            coil.compose.AsyncImage(
                                model = "https://api.qrserver.com/v1/create-qr-code/?size=280x280&data=" +
                                    java.net.URLEncoder.encode(qrData, "UTF-8"),
                                contentDescription = "QR",
                                modifier = Modifier.size(280.dp)
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(qrData, fontSize = 12.sp, color = Muted)
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = { showQr = false }) { Text("Cerrar") }
                    }
                )
            }

            Spacer(Modifier.height(16.dp))
            Text(
                "Versión nativa Kotlin + Compose\nFotos · Pedidos · Firebase · Multi-tienda · WhatsApp",
                color = Muted, fontSize = 12.sp
            )
        }
    }

    if (showNewStore) {
        AlertDialog(
            onDismissRequest = { showNewStore = false },
            title = { Text("Nueva tienda") },
            text = {
                OutlinedTextField(newStoreName, { newStoreName = it }, label = { Text("Nombre") }, singleLine = true)
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.addStore(newStoreName)
                    newStoreName = ""
                    showNewStore = false
                }) { Text("Crear") }
            },
            dismissButton = { TextButton(onClick = { showNewStore = false }) { Text("Cancelar") } }
        )
    }
    if (showDeleteStore) {
        AlertDialog(
            onDismissRequest = { showDeleteStore = false },
            title = { Text("Eliminar tienda") },
            text = { Text("Se borrarán todos los productos y pedidos de «${store?.nombre}».") },
            confirmButton = {
                TextButton(onClick = {
                    vm.deleteCurrentStore()
                    showDeleteStore = false
                    onBack()
                }) { Text("Eliminar") }
            },
            dismissButton = { TextButton(onClick = { showDeleteStore = false }) { Text("Cancelar") } }
        )
    }
}
