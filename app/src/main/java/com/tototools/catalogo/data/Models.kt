package com.tototools.catalogo.data

import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
data class AppState(
    val stores: List<Store> = emptyList(),
    val currentStoreId: String? = null
) {
    val currentStore: Store?
        get() = stores.find { it.id == currentStoreId } ?: stores.firstOrNull()
}

@Serializable
data class Store(
    val id: String = UUID.randomUUID().toString(),
    val nombre: String = "Mi tienda",
    val slug: String = "",
    val frase: String = "",
    val wa: String = "",
    val dir: String = "",
    val hor: String = "",
    val mp: String = "CUP",
    val tasaCUP: Double = 1.0,
    val fbUrl: String = "",
    val fbKey: String = "",
    val fbEmail: String = "",
    val fbPass: String = "",
    val cats: List<Category> = emptyList(),
    val prods: List<Product> = emptyList(),
    val pedidos: List<Pedido> = emptyList(),
    val visitas: Map<String, Int> = emptyMap()
)

@Serializable
data class Category(
    val id: String = UUID.randomUUID().toString(),
    val n: String = "",
    val icono: String = "caja"
)

@Serializable
data class Product(
    val id: String = UUID.randomUUID().toString(),
    val nombre: String = "",
    val precio: Double = 0.0,
    val ofertaPrecio: Double = 0.0,
    val ofertaFin: String = "",
    val sku: String = "",
    val cat: String = "",
    val desc: String = "",
    val unidad: String = "",
    val fotos: List<String> = emptyList(),
    val vis: Boolean = true,
    val disp: Boolean = true,
    val stock: Int? = null,
    val dest: Boolean = false,
    val moneda: String? = null
)

@Serializable
data class Pedido(
    val c: String = "",
    val t: Long = 0L,
    val txt: String = "",
    val cliente: String = "",
    val ubicacion: String = "",
    val items: List<PedidoItem> = emptyList(),
    val visto: Boolean = false,
    val moneda: String = "CUP"
)

@Serializable
data class PedidoItem(
    val id: String = "",
    val n: String = "",
    val q: Int = 1,
    val precio: Double = 0.0
)

data class SyncResult(val nuevos: Int = 0, val message: String = "")
