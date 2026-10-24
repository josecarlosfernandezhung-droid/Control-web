package com.tototools.catalogo.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tototools.catalogo.CatalogoViewModel
import com.tototools.catalogo.data.Pedido
import com.tototools.catalogo.ui.theme.Brand
import com.tototools.catalogo.ui.theme.BrandDark
import com.tototools.catalogo.ui.theme.Muted
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PedidosScreen(
    vm: CatalogoViewModel,
    onBack: () -> Unit
) {
    val state by vm.state.collectAsState()
    val store = state.currentStore
    val pedidos = store?.pedidos ?: emptyList()
    val context = LocalContext.current
    var showPaste by remember { mutableStateOf(false) }
    var pasteText by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf<Pedido?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Pedidos (${pedidos.size})") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Volver")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = BrandDark,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary,
                    actionIconContentColor = MaterialTheme.colorScheme.onPrimary
                ),
                actions = {
                    IconButton(onClick = { vm.syncFirebase() }) {
                        Icon(Icons.Default.CloudSync, "Sincronizar Firebase")
                    }
                    IconButton(onClick = { showPaste = true }) {
                        Icon(Icons.Default.ContentPaste, "Pegar pedido")
                    }
                }
            )
        }
    ) { padding ->
        if (pedidos.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.ReceiptLong, null, Modifier.size(64.dp), tint = Muted)
                    Spacer(Modifier.height(12.dp))
                    Text("Sin pedidos", color = Muted)
                    Text("Sincroniza con Firebase o pega un texto", color = Muted, fontSize = 13.sp)
                }
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(
                    start = 14.dp, end = 14.dp,
                    top = padding.calculateTopPadding() + 8.dp,
                    bottom = 24.dp
                ),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(pedidos, key = { it.c }) { p ->
                    Card(
                        onClick = {
                            selected = p
                            if (!p.visto) vm.markPedidoVisto(p.c)
                        },
                        colors = CardDefaults.cardColors(
                            containerColor = if (p.visto) MaterialTheme.colorScheme.surface
                            else MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Column(Modifier.padding(14.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(p.c, fontWeight = FontWeight.Bold, color = Brand)
                                if (!p.visto) {
                                    Spacer(Modifier.width(8.dp))
                                    Badge { Text("nuevo") }
                                }
                                Spacer(Modifier.weight(1f))
                                Text(formatFecha(p.t), fontSize = 12.sp, color = Muted)
                            }
                            if (p.cliente.isNotBlank()) Text(p.cliente, fontWeight = FontWeight.Medium)
                            if (p.ubicacion.isNotBlank()) Text(p.ubicacion, fontSize = 13.sp, color = Muted)
                            if (p.items.isNotEmpty()) {
                                Spacer(Modifier.height(4.dp))
                                p.items.take(4).forEach { it ->
                                    Text("• ${it.n} × ${it.q}", fontSize = 13.sp)
                                }
                                if (p.items.size > 4) Text("… +${p.items.size - 4} más", fontSize = 12.sp, color = Muted)
                            }
                        }
                    }
                }
            }
        }
    }

    if (showPaste) {
        AlertDialog(
            onDismissRequest = { showPaste = false },
            title = { Text("Pegar texto de pedido") },
            text = {
                OutlinedTextField(
                    value = pasteText,
                    onValueChange = { pasteText = it },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp),
                    placeholder = { Text("Pega aquí el mensaje de WhatsApp…") }
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (pasteText.isNotBlank()) {
                        vm.addPedidoFromText(pasteText)
                        pasteText = ""
                        showPaste = false
                    }
                }) { Text("Añadir") }
            },
            dismissButton = { TextButton(onClick = { showPaste = false }) { Text("Cancelar") } }
        )
    }

    selected?.let { p ->
        AlertDialog(
            onDismissRequest = { selected = null },
            title = { Text("Pedido ${p.c}") },
            text = {
                Column {
                    if (p.cliente.isNotBlank()) Text("Cliente: ${p.cliente}")
                    if (p.ubicacion.isNotBlank()) Text("Ubicación: ${p.ubicacion}")
                    Spacer(Modifier.height(8.dp))
                    p.items.forEach { Text("• ${it.n} × ${it.q}  ${formatPrice(it.precio, p.moneda)}") }
                    if (p.txt.isNotBlank()) {
                        Spacer(Modifier.height(8.dp))
                        Text(p.txt, fontSize = 12.sp, color = Muted)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val wa = store?.wa?.filter { it.isDigit() } ?: ""
                    if (wa.isNotBlank()) {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/$wa"))
                        context.startActivity(intent)
                    }
                    selected = null
                }) { Text("WhatsApp") }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = {
                        vm.deletePedido(p.c)
                        selected = null
                    }) { Text("Borrar") }
                    TextButton(onClick = { selected = null }) { Text("Cerrar") }
                }
            }
        )
    }
}

private fun formatFecha(t: Long): String {
    if (t <= 0) return ""
    return SimpleDateFormat("dd/MM HH:mm", Locale.getDefault()).format(Date(t))
}
