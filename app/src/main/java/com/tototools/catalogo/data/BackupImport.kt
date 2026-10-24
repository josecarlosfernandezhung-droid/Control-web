package com.tototools.catalogo.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import java.util.UUID

/**
 * Importa una copia de seguridad de la versión web (datos.json del ZIP de backup).
 * No importa fotos (están en otros ficheros del ZIP); solo texto y estructura.
 */
object BackupImport {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    data class Result(
        val stores: List<Store>,
        val message: String
    )

    fun fromJsonString(raw: String): Result {
        val root = json.parseToJsonElement(raw).jsonObject

        // Formato backup web: { stores: [...], cur: "...", cfg: {...}, ... }
        // También aceptamos un único store plano o { data: { tienda, productos, categorias } }
        val cfg = root["cfg"]?.jsonObject
        val fbUrl = cfg?.get("dbURL")?.jsonPrimitive?.contentOrNull
            ?: root["fbUrl"]?.jsonPrimitive?.contentOrNull
            ?: ""
        val fbKey = cfg?.get("apiKey")?.jsonPrimitive?.contentOrNull
            ?: root["fbKey"]?.jsonPrimitive?.contentOrNull
            ?: ""

        val storesEl = root["stores"]
        val stores = when {
            storesEl is JsonArray -> storesEl.mapNotNull { parseStore(it, fbUrl, fbKey) }
            root["prods"] != null || root["productos"] != null ->
                listOfNotNull(parseStore(root, fbUrl, fbKey))
            root["data"] is JsonObject -> {
                // formato publicado
                val data = root["data"]!!.jsonObject
                listOfNotNull(parsePublished(data, fbUrl, fbKey))
            }
            else -> emptyList()
        }

        if (stores.isEmpty()) {
            throw IllegalArgumentException(
                "No se encontraron tiendas ni productos en el archivo. " +
                    "Usa el backup de la app web (datos.json) o un export de catálogo."
            )
        }

        val nProds = stores.sumOf { it.prods.size }
        val nCats = stores.sumOf { it.cats.size }
        return Result(
            stores = stores,
            message = "Importadas ${stores.size} tienda(s), $nProds producto(s), $nCats categoría(s). " +
                "Las fotos no se importan (haz backup con fotos y se podrá ampliar más adelante)."
        )
    }

    private fun parsePublished(data: JsonObject, fbUrl: String, fbKey: String): Store? {
        val tienda = data["tienda"]?.jsonObject
        val catsObj = data["categorias"]?.jsonObject
        val prodsObj = data["productos"]?.jsonObject

        val cats = catsObj?.entries?.map { (id, el) ->
            val o = el.jsonObject
            Category(
                id = id,
                n = o["n"]?.jsonPrimitive?.contentOrNull
                    ?: o["nombre"]?.jsonPrimitive?.contentOrNull
                    ?: id,
                icono = o["icono"]?.jsonPrimitive?.contentOrNull ?: "caja"
            )
        } ?: emptyList()

        val prods = prodsObj?.entries?.mapNotNull { (id, el) ->
            parseProduct(el, id)
        } ?: emptyList()

        return Store(
            id = UUID.randomUUID().toString(),
            nombre = tienda?.get("n")?.jsonPrimitive?.contentOrNull
                ?: tienda?.get("nombre")?.jsonPrimitive?.contentOrNull
                ?: "Tienda importada",
            slug = tienda?.get("slug")?.jsonPrimitive?.contentOrNull ?: "",
            frase = tienda?.get("frase")?.jsonPrimitive?.contentOrNull ?: "",
            wa = tienda?.get("wa")?.jsonPrimitive?.contentOrNull ?: "",
            dir = tienda?.get("dir")?.jsonPrimitive?.contentOrNull ?: "",
            hor = tienda?.get("hor")?.jsonPrimitive?.contentOrNull ?: "",
            mp = tienda?.get("mp")?.jsonPrimitive?.contentOrNull ?: "CUP",
            fbUrl = fbUrl,
            fbKey = fbKey,
            cats = cats,
            prods = prods
        )
    }

    private fun parseStore(el: JsonElement, fbUrl: String, fbKey: String): Store? {
        val o = el as? JsonObject ?: return null
        val cats = (o["cats"] as? JsonArray)?.mapNotNull { parseCategory(it) }
            ?: emptyList()
        val prods = (o["prods"] as? JsonArray)?.mapNotNull { parseProduct(it) }
            ?: emptyList()
        val pedidos = (o["pedidos"] as? JsonArray)?.mapNotNull { parsePedido(it) }
            ?: emptyList()

        return Store(
            id = o["id"]?.jsonPrimitive?.contentOrNull ?: UUID.randomUUID().toString(),
            nombre = o["nombre"]?.jsonPrimitive?.contentOrNull ?: "Tienda",
            slug = o["slug"]?.jsonPrimitive?.contentOrNull ?: "",
            frase = o["frase"]?.jsonPrimitive?.contentOrNull ?: "",
            wa = o["wa"]?.jsonPrimitive?.contentOrNull ?: "",
            dir = o["dir"]?.jsonPrimitive?.contentOrNull ?: "",
            hor = when (val h = o["hor"]) {
                is JsonPrimitive -> h.contentOrNull ?: ""
                is JsonObject -> h.toString()
                else -> ""
            },
            mp = o["mp"]?.jsonPrimitive?.contentOrNull ?: "CUP",
            tasaCUP = o["tasas"]?.jsonObject?.get("CUP")?.jsonPrimitive?.doubleOrNull
                ?: o["tasaCUP"]?.jsonPrimitive?.doubleOrNull
                ?: 1.0,
            fbUrl = fbUrl.ifBlank {
                o["fbUrl"]?.jsonPrimitive?.contentOrNull ?: ""
            },
            fbKey = fbKey.ifBlank {
                o["fbKey"]?.jsonPrimitive?.contentOrNull ?: ""
            },
            fbEmail = o["fbEmail"]?.jsonPrimitive?.contentOrNull ?: "",
            fbPass = o["fbPass"]?.jsonPrimitive?.contentOrNull ?: "",
            cats = cats,
            prods = prods,
            pedidos = pedidos
        )
    }

    private fun parseCategory(el: JsonElement): Category? {
        val o = el as? JsonObject ?: return null
        return Category(
            id = o["id"]?.jsonPrimitive?.contentOrNull ?: UUID.randomUUID().toString(),
            n = o["n"]?.jsonPrimitive?.contentOrNull
                ?: o["nombre"]?.jsonPrimitive?.contentOrNull
                ?: "",
            icono = o["icono"]?.jsonPrimitive?.contentOrNull ?: "caja"
        )
    }

    private fun parseProduct(el: JsonElement, forcedId: String? = null): Product? {
        val o = el as? JsonObject ?: return null
        val nombre = o["nombre"]?.jsonPrimitive?.contentOrNull ?: return null
        if (nombre.isBlank()) return null

        fun num(key: String): Double {
            val p = o[key]?.jsonPrimitive ?: return 0.0
            return p.doubleOrNull
                ?: p.contentOrNull?.replace(',', '.')?.toDoubleOrNull()
                ?: 0.0
        }

        return Product(
            id = forcedId ?: o["id"]?.jsonPrimitive?.contentOrNull ?: UUID.randomUUID().toString(),
            nombre = nombre,
            precio = num("precio"),
            ofertaPrecio = num("ofertaPrecio"),
            ofertaFin = o["ofertaFin"]?.jsonPrimitive?.contentOrNull ?: "",
            sku = o["sku"]?.jsonPrimitive?.contentOrNull ?: "",
            cat = o["cat"]?.jsonPrimitive?.contentOrNull ?: "",
            desc = o["desc"]?.jsonPrimitive?.contentOrNull ?: "",
            unidad = o["unidad"]?.jsonPrimitive?.contentOrNull ?: "",
            fotos = emptyList(), // fotos del backup web son IDs de blob, no rutas
            vis = o["vis"]?.jsonPrimitive?.booleanOrNull ?: true,
            disp = o["disp"]?.jsonPrimitive?.booleanOrNull ?: true,
            stock = o["st"]?.jsonPrimitive?.intOrNull
                ?: o["stock"]?.jsonPrimitive?.intOrNull,
            dest = o["dest"]?.jsonPrimitive?.booleanOrNull ?: false,
            moneda = o["moneda"]?.jsonPrimitive?.contentOrNull?.ifBlank { null }
        )
    }

    private fun parsePedido(el: JsonElement): Pedido? {
        val o = el as? JsonObject ?: return null
        val c = o["c"]?.jsonPrimitive?.contentOrNull ?: return null
        return Pedido(
            c = c,
            t = o["t"]?.jsonPrimitive?.longOrNull ?: 0L,
            txt = o["txt"]?.jsonPrimitive?.contentOrNull ?: "",
            cliente = o["cliente"]?.jsonPrimitive?.contentOrNull ?: "",
            ubicacion = o["ubicacion"]?.jsonPrimitive?.contentOrNull ?: "",
            visto = o["visto"]?.jsonPrimitive?.booleanOrNull ?: false,
            moneda = o["moneda"]?.jsonPrimitive?.contentOrNull ?: "CUP"
        )
    }
}
