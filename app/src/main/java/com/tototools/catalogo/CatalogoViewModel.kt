package com.tototools.catalogo

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tototools.catalogo.data.AppState
import com.tototools.catalogo.data.BackupImport
import com.tototools.catalogo.data.Category
import com.tototools.catalogo.data.FirebaseService
import com.tototools.catalogo.data.Pedido
import com.tototools.catalogo.data.Product
import com.tototools.catalogo.data.Store
import com.tototools.catalogo.data.StoreRepository
import com.tototools.catalogo.data.SyncResult
import com.tototools.catalogo.util.PhotoUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID

class CatalogoViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = StoreRepository(app)
    private val fb = FirebaseService()

    val state: StateFlow<AppState> = repo.stateFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AppState())

    private val _busy = MutableStateFlow<String?>(null)
    val busy = _busy.asStateFlow()

    private val _toast = MutableStateFlow<String?>(null)
    val toast = _toast.asStateFlow()

    fun clearToast() { _toast.value = null }

    private fun toast(msg: String) { _toast.value = msg }

    // —— Tiendas ——
    fun switchStore(id: String) {
        viewModelScope.launch {
            repo.update { it.copy(currentStoreId = id) }
        }
    }

    fun addStore(nombre: String) {
        viewModelScope.launch {
            val st = Store(nombre = nombre.ifBlank { "Nueva tienda" })
            repo.update { state ->
                state.copy(
                    stores = state.stores + st,
                    currentStoreId = st.id
                )
            }
            toast("Tienda creada")
        }
    }

    fun deleteCurrentStore() {
        viewModelScope.launch {
            repo.update { state ->
                if (state.stores.size <= 1) return@update state
                val id = state.currentStoreId
                val remaining = state.stores.filter { it.id != id }
                state.copy(stores = remaining, currentStoreId = remaining.first().id)
            }
            toast("Tienda eliminada")
        }
    }

    // —— Productos ——
    fun addProduct(product: Product) {
        viewModelScope.launch {
            repo.updateCurrentStore { store ->
                store.copy(prods = store.prods + product.copy(
                    id = product.id.ifBlank { UUID.randomUUID().toString() }
                ))
            }
        }
    }

    fun updateProduct(product: Product) {
        viewModelScope.launch {
            repo.updateCurrentStore { store ->
                store.copy(prods = store.prods.map { if (it.id == product.id) product else it })
            }
        }
    }

    fun deleteProduct(id: String) {
        viewModelScope.launch {
            val store = state.value.currentStore
            store?.prods?.find { it.id == id }?.fotos?.forEach { PhotoUtils.deleteIfLocal(it) }
            repo.updateCurrentStore { s -> s.copy(prods = s.prods.filter { it.id != id }) }
        }
    }

    fun toggleVisible(id: String) {
        viewModelScope.launch {
            repo.updateCurrentStore { store ->
                store.copy(prods = store.prods.map {
                    if (it.id == id) it.copy(vis = !it.vis) else it
                })
            }
        }
    }

    fun setProductPhotos(productId: String, paths: List<String>) {
        viewModelScope.launch {
            repo.updateCurrentStore { store ->
                store.copy(prods = store.prods.map {
                    if (it.id == productId) it.copy(fotos = paths) else it
                })
            }
        }
    }

    // —— Categorías ——
    fun addCategory(nombre: String) {
        viewModelScope.launch {
            repo.updateCurrentStore { store ->
                val c = Category(id = UUID.randomUUID().toString(), n = nombre.trim())
                store.copy(cats = store.cats + c)
            }
        }
    }

    fun deleteCategory(id: String) {
        viewModelScope.launch {
            repo.updateCurrentStore { store ->
                store.copy(
                    cats = store.cats.filter { it.id != id },
                    prods = store.prods.map { if (it.cat == id) it.copy(cat = "") else it }
                )
            }
        }
    }

    // —— Tienda / Firebase config ——
    fun updateStore(block: (Store) -> Store) {
        viewModelScope.launch { repo.updateCurrentStore(block) }
    }

    // —— Pedidos ——
    fun markPedidoVisto(code: String) {
        viewModelScope.launch {
            repo.updateCurrentStore { store ->
                store.copy(pedidos = store.pedidos.map {
                    if (it.c == code) it.copy(visto = true) else it
                })
            }
        }
    }

    fun deletePedido(code: String) {
        viewModelScope.launch {
            repo.updateCurrentStore { store ->
                store.copy(pedidos = store.pedidos.filter { it.c != code })
            }
        }
    }

    fun addPedidoFromText(txt: String) {
        viewModelScope.launch {
            val store = state.value.currentStore ?: return@launch
            val p = FirebaseService.parsePedidoWA(store, txt)
            repo.updateCurrentStore { s ->
                if (s.pedidos.any { it.c == p.c }) s
                else s.copy(pedidos = (listOf(p) + s.pedidos).sortedByDescending { it.t })
            }
            toast("Pedido ${p.c} añadido")
        }
    }

    // —— Firebase ——
    fun syncFirebase() {
        viewModelScope.launch {
            val store = state.value.currentStore ?: return@launch
            _busy.value = "Sincronizando pedidos…"
            try {
                val (nuevos, visitas) = fb.syncPedidos(store)
                repo.updateCurrentStore { s ->
                    val codes = s.pedidos.map { it.c }.toSet()
                    val merged = (nuevos.filter { it.c !in codes } + s.pedidos)
                        .sortedByDescending { it.t }
                    s.copy(pedidos = merged, visitas = visitas.ifEmpty { s.visitas })
                }
                _busy.value = null
                toast(
                    if (nuevos.isEmpty()) "No hay pedidos nuevos"
                    else "${nuevos.size} pedido(s) nuevo(s)"
                )
            } catch (e: Exception) {
                _busy.value = null
                toast(e.message ?: "Error al sincronizar")
            }
        }
    }

    fun publicarCatalogo() {
        viewModelScope.launch {
            val store = state.value.currentStore ?: return@launch
            _busy.value = "Publicando catálogo…"
            try {
                fb.publicarCatalogo(store)
                _busy.value = null
                toast("Catálogo publicado ✓")
            } catch (e: Exception) {
                _busy.value = null
                toast(e.message ?: "Error al publicar")
            }
        }
    }

    fun testFirebase() {
        viewModelScope.launch {
            val store = state.value.currentStore ?: return@launch
            _busy.value = "Probando conexión…"
            try {
                fb.getIdToken(store)
                _busy.value = null
                toast("Conectado a Firebase ✓")
            } catch (e: Exception) {
                _busy.value = null
                toast(e.message ?: "Error de conexión")
            }
        }
    }

    fun firebaseRules(): String {
        val email = state.value.currentStore?.fbEmail ?: "TU_CORREO"
        return fb.firebaseRulesSnippet(email)
    }

    // —— Importar backup web ——
    fun importBackupJson(raw: String) {
        viewModelScope.launch {
            try {
                val result = BackupImport.fromJsonString(raw)
                repo.update { state ->
                    // Fusionar: añadir tiendas nuevas; si mismo id, reemplazar
                    val byId = state.stores.associateBy { it.id }.toMutableMap()
                    result.stores.forEach { byId[it.id] = it }
                    val merged = byId.values.toList()
                    state.copy(
                        stores = merged,
                        currentStoreId = result.stores.firstOrNull()?.id ?: state.currentStoreId
                    )
                }
                toast(result.message)
            } catch (e: Exception) {
                toast(e.message ?: "Error al importar")
            }
        }
    }

}
