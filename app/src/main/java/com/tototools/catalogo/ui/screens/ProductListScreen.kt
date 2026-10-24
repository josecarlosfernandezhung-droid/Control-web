package com.tototools.catalogo.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.tototools.catalogo.CatalogoViewModel
import com.tototools.catalogo.data.Product
import com.tototools.catalogo.ui.theme.Brand
import com.tototools.catalogo.ui.theme.BrandDark
import com.tototools.catalogo.ui.theme.Muted
import java.io.File
import androidx.compose.ui.platform.LocalContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProductListScreen(
    vm: CatalogoViewModel,
    onProductClick: (Product) -> Unit,
    onAddClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onPedidosClick: () -> Unit
) {
    val state by vm.state.collectAsState()
    val store = state.currentStore
    val allProducts = store?.prods ?: emptyList()
    val cats = store?.cats?.associateBy { it.id } ?: emptyMap()
    val catList = store?.cats ?: emptyList()
    var catFilter by remember { mutableStateOf<String?>(null) } // null = todas
    var query by remember { mutableStateOf("") }
    val products = allProducts.filter { p ->
        (catFilter == null || p.cat == catFilter) &&
        (query.isBlank() || p.nombre.contains(query, true) || p.sku.contains(query, true))
    }
    val pedidosNuevos = store?.pedidos?.count { !it.visto } ?: 0

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            store?.nombre ?: "Catálogo",
                            fontWeight = FontWeight.Bold,
                            fontSize = 19.sp
                        )
                        if (!store?.slug.isNullOrBlank()) {
                            Text(
                                "/${store?.slug}",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f)
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = BrandDark,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    actionIconContentColor = MaterialTheme.colorScheme.onPrimary
                ),
                actions = {
                    BadgedBox(badge = {
                        if (pedidosNuevos > 0) {
                            Badge { Text("$pedidosNuevos") }
                        }
                    }) {
                        IconButton(onClick = onPedidosClick) {
                            Icon(Icons.Default.ReceiptLong, contentDescription = "Pedidos")
                        }
                    }
                    IconButton(onClick = onSettingsClick) {
                        Icon(Icons.Default.Settings, contentDescription = "Ajustes")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onAddClick,
                containerColor = Brand,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ) {
                Icon(Icons.Default.Add, contentDescription = "Añadir producto")
            }
        }
    ) { padding ->
        Column(Modifier.padding(padding)) {
        // Búsqueda
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = { Text("Buscar producto…") },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 8.dp),
            singleLine = true,
            leadingIcon = { Icon(Icons.Default.Search, null) }
        )
        // Filtro por categoría
        if (catList.isNotEmpty()) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 14.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = catFilter == null,
                    onClick = { catFilter = null },
                    label = { Text("Todas") }
                )
                catList.forEach { c ->
                    FilterChip(
                        selected = catFilter == c.id,
                        onClick = { catFilter = if (catFilter == c.id) null else c.id },
                        label = { Text(c.n) }
                    )
                }
            }
        }
        if (products.isEmpty()) {
            Box(
                Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Inventory2, null, Modifier.size(64.dp), tint = Muted)
                    Spacer(Modifier.height(12.dp))
                    Text("Sin productos todavía", color = Muted)
                    Text("Pulsa + para añadir el primero", color = Muted, fontSize = 13.sp)
                }
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(
                    start = 14.dp, end = 14.dp,
                    top = 8.dp,
                    bottom = 80.dp
                ),
                modifier = Modifier.weight(1f)
            ) {
                items(products, key = { it.id }) { p ->
                    ProductRow(
                        product = p,
                        categoryName = cats[p.cat]?.n,
                        moneda = store?.mp ?: "CUP",
                        onClick = { onProductClick(p) },
                        onToggleVis = { vm.toggleVisible(p.id) }
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
                }
            }
        }
        } // Column
    }
}

@Composable
private fun ProductRow(
    product: Product,
    categoryName: String?,
    moneda: String,
    onClick: () -> Unit,
    onToggleVis: () -> Unit
) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(50.dp).clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            val foto = product.fotos.firstOrNull()
            if (foto != null) {
                val model = when {
                    foto.startsWith("file:") -> File(android.net.Uri.parse(foto).path ?: foto)
                    foto.startsWith("/") -> File(foto)
                    else -> foto
                }
                AsyncImage(
                    model = model,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Text(
                    product.nombre.take(2).uppercase().ifBlank { "?" },
                    fontWeight = FontWeight.Bold,
                    color = Brand
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    product.nombre.ifBlank { "(sin nombre)" },
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (product.dest) {
                    Icon(Icons.Default.Star, null, Modifier.size(14.dp).padding(start = 4.dp), tint = Brand)
                }
            }
            val sub = buildString {
                if (!categoryName.isNullOrBlank()) append(categoryName)
                if (product.sku.isNotBlank()) {
                    if (isNotEmpty()) append(" · ")
                    append(product.sku)
                }
                if (product.stock != null) {
                    if (isNotEmpty()) append(" · ")
                    append("stock ${product.stock}")
                }
            }
            if (sub.isNotEmpty()) {
                Text(sub, fontSize = 13.sp, color = Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            val ofertaActiva = product.ofertaPrecio > 0 && (
                product.ofertaFin.isBlank() || product.ofertaFin >= java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
                    .format(java.util.Date())
            )
            if (ofertaActiva) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        formatPrice(product.ofertaPrecio, moneda),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = Brand
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        formatPrice(product.precio, moneda),
                        fontSize = 12.sp,
                        color = Muted,
                        style = androidx.compose.ui.text.TextStyle(
                            textDecoration = androidx.compose.ui.text.style.TextDecoration.LineThrough
                        )
                    )
                    if (product.ofertaFin.isNotBlank()) {
                        Spacer(Modifier.width(6.dp))
                        Text("hasta ${product.ofertaFin}", fontSize = 11.sp, color = Muted)
                    }
                }
            } else {
                Text(
                    formatPrice(product.precio, moneda) +
                        (if (product.unidad.isNotBlank()) " / ${product.unidad}" else ""),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = Brand
                )
            }
            if (!product.disp) {
                Text("Agotado", fontSize = 12.sp, color = MaterialTheme.colorScheme.error)
            } else if (product.stock != null) {
                Text("Stock: ${product.stock}", fontSize = 12.sp, color = Muted)
            }
        }
        IconButton(onClick = onToggleVis) {
            Icon(
                if (product.vis) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                if (product.vis) "Visible" else "Oculto",
                tint = if (product.vis) Brand else Muted
            )
        }
    }
}

fun formatPrice(value: Double, moneda: String): String {
    val formatted = if (value == value.toLong().toDouble()) value.toLong().toString()
    else "%.2f".format(value)
    return "$formatted $moneda"
}
