package com.tototools.catalogo.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Cliente REST mínimo para Firebase Auth + Realtime Database
 * (sin SDK, igual que la web con fetch).
 */
class FirebaseService {

    private val json = Json { ignoreUnknownKeys = true }

    private var cachedToken: String? = null
    private var tokenExp: Long = 0
    private var tokenKey: String = ""

    fun isConfigured(store: Store): Boolean =
        store.fbUrl.isNotBlank() && store.fbKey.isNotBlank() &&
            store.fbEmail.isNotBlank() && store.fbPass.isNotBlank()

    suspend fun getIdToken(store: Store): String = withContext(Dispatchers.IO) {
        val key = store.fbKey + store.fbEmail + store.fbPass
        if (cachedToken != null && tokenKey == key && System.currentTimeMillis() < tokenExp - 60_000) {
            return@withContext cachedToken!!
        }
        val url = URL(
            "https://identitytoolkit.googleapis.com/v1/accounts:signInWithPassword?key=" +
                URLEncoder.encode(store.fbKey, "UTF-8")
        )
        val body = buildJsonObject {
            put("email", store.fbEmail)
            put("password", store.fbPass)
            put("returnSecureToken", true)
        }.toString()
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            setRequestProperty("Content-Type", "application/json")
            doOutput = true
            connectTimeout = 15000
            readTimeout = 15000
        }
        conn.outputStream.use { it.write(body.toByteArray()) }
        val code = conn.responseCode
        val resp = (if (code in 200..299) conn.inputStream else conn.errorStream)
            .bufferedReader().readText()
        val obj = json.parseToJsonElement(resp).jsonObject
        if (code !in 200..299) {
            val msg = obj["error"]?.jsonObject?.get("message")?.jsonPrimitive?.contentOrNull ?: "Error $code"
            throw Exception(
                when {
                    msg.contains("INVALID") || msg.contains("EMAIL_NOT_FOUND") || msg.contains("PASSWORD") ->
                        "Correo o contraseña de Firebase incorrectos."
                    msg.contains("OPERATION_NOT_ALLOWED") ->
                        "En Firebase activa «Correo y contraseña» en Authentication."
                    else -> "Error de Firebase: $msg"
                }
            )
        }
        val token = obj["idToken"]?.jsonPrimitive?.content
            ?: throw Exception("Respuesta de Firebase sin token")
        val expiresIn = obj["expiresIn"]?.jsonPrimitive?.content?.toLongOrNull() ?: 3600L
        cachedToken = token
        tokenExp = System.currentTimeMillis() + expiresIn * 1000
        tokenKey = key
        token
    }

    suspend fun dbGet(store: Store, path: String): JsonElement? = withContext(Dispatchers.IO) {
        val token = getIdToken(store)
        val base = store.fbUrl.trimEnd('/')
        val url = URL("$base/$path.json?auth=${URLEncoder.encode(token, "UTF-8")}")
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 20000
            readTimeout = 25000
        }
        val code = conn.responseCode
        val resp = (if (code in 200..299) conn.inputStream else conn.errorStream)
            .bufferedReader().readText()
        when (code) {
            401, 403 -> throw Exception("Sin permiso: revisa las reglas de Firebase y publícalas.")
            !in 200..299 -> throw Exception("Error de la base de datos ($code).")
        }
        if (resp == "null" || resp.isBlank()) null
        else json.parseToJsonElement(resp)
    }

    suspend fun dbPut(store: Store, path: String, body: String): Unit = withContext(Dispatchers.IO) {
        val token = getIdToken(store)
        val base = store.fbUrl.trimEnd('/')
        val url = URL("$base/$path.json?auth=${URLEncoder.encode(token, "UTF-8")}")
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "PUT"
            setRequestProperty("Content-Type", "application/json")
            doOutput = true
            connectTimeout = 20000
            readTimeout = 25000
        }
        conn.outputStream.use { it.write(body.toByteArray()) }
        val code = conn.responseCode
        if (code == 401 || code == 403) throw Exception("Sin permiso al escribir en Firebase.")
        if (code !in 200..299) throw Exception("Error al escribir ($code).")
    }

    suspend fun dbPatch(store: Store, path: String, body: String): Unit = withContext(Dispatchers.IO) {
        val token = getIdToken(store)
        val base = store.fbUrl.trimEnd('/')
        val url = URL("$base/$path.json?auth=${URLEncoder.encode(token, "UTF-8")}")
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "PATCH"
            setRequestProperty("Content-Type", "application/json")
            doOutput = true
            connectTimeout = 20000
            readTimeout = 25000
        }
        conn.outputStream.use { it.write(body.toByteArray()) }
        val code = conn.responseCode
        if (code == 401 || code == 403) throw Exception("Sin permiso al escribir en Firebase.")
        if (code !in 200..299) throw Exception("Error al escribir ($code).")
    }

    /**
     * Sincroniza pedidos y visitas desde Firebase (igual que la web).
     */
    suspend fun syncPedidos(store: Store): Pair<List<Pedido>, Map<String, Int>> {
        if (!isConfigured(store)) throw Exception("Configura Firebase en Ajustes primero.")
        if (store.slug.isBlank()) throw Exception("Tu tienda aún no tiene dirección (slug).")

        val pedRaw = dbGet(store, "pedidos/${store.slug}")
        val visRaw = dbGet(store, "visitas/${store.slug}")

        val nuevos = mutableListOf<Pedido>()
        val existingCodes = store.pedidos.map { it.c }.toSet()

        if (pedRaw is JsonObject) {
            for ((_, v) in pedRaw) {
                val obj = v.jsonObject
                val txt = obj["txt"]?.jsonPrimitive?.contentOrNull ?: continue
                val t = obj["t"]?.jsonPrimitive?.longOrNull ?: 0L
                val parsed = parsePedidoWA(store, txt, t)
                if (parsed.c.isNotBlank() && parsed.c !in existingCodes) {
                    nuevos += parsed
                }
            }
        }

        val visitas = mutableMapOf<String, Int>()
        if (visRaw is JsonObject) {
            for ((k, v) in visRaw) {
                visitas[k] = v.jsonPrimitive.intOrNull ?: 0
            }
        }

        return nuevos to visitas
    }

    /**
     * Publica catálogo básico en Firebase bajo el slug.
     * (Versión simplificada: tienda + categorías + productos visibles)
     */
    suspend fun publicarCatalogo(store: Store) {
        if (!isConfigured(store)) throw Exception("Configura Firebase en Ajustes primero.")
        if (store.slug.isBlank()) throw Exception("Define un slug en Ajustes antes de publicar.")

        val cats = buildJsonObject {
            store.cats.forEachIndexed { i, c ->
                put(c.id, buildJsonObject {
                    put("n", c.n)
                    put("o", i)
                })
            }
        }
        val prods = buildJsonObject {
            store.prods.filter { it.vis && it.nombre.isNotBlank() }.forEachIndexed { i, p ->
                put(p.id, buildJsonObject {
                    put("n", p.nombre)
                    if (p.desc.isNotBlank()) put("d", p.desc)
                    put("p", p.precio)
                    if (p.ofertaPrecio > 0) {
                        put("of", buildJsonObject {
                            put("p", p.ofertaPrecio)
                            if (p.ofertaFin.isNotBlank()) put("h", p.ofertaFin)
                        })
                    }
                    if (p.cat.isNotBlank()) put("c", p.cat)
                    if (p.sku.isNotBlank()) put("sku", p.sku)
                    if (!p.disp || (p.stock != null && p.stock <= 0)) put("ag", true)
                    if (p.dest) put("de", true)
                    put("o", i)
                    // fotos: solo URIs http(s) públicas; las locales se omiten
                    val remoteFotos = p.fotos.filter { it.startsWith("http") }
                    if (remoteFotos.isNotEmpty()) {
                        put("f", kotlinx.serialization.json.JsonArray(
                            remoteFotos.map { JsonPrimitive(it) }
                        ))
                    }
                })
            }
        }
        val tienda = buildJsonObject {
            put("n", store.nombre)
            if (store.frase.isNotBlank()) put("f", store.frase)
            if (store.wa.isNotBlank()) put("wa", store.wa)
            if (store.dir.isNotBlank()) put("dir", store.dir)
            if (store.hor.isNotBlank()) put("hor", store.hor)
            put("mp", store.mp)
        }

        val body = buildJsonObject {
            put("tienda", tienda)
            put("categorias", cats)
            put("productos", prods)
            put("v", System.currentTimeMillis())
        }.toString()

        dbPatch(store, "catalogo/${store.slug}", body)
    }

    fun firebaseRulesSnippet(email: String): String {
        val em = email.ifBlank { "TU_CORREO" }.replace("'", "")
        val ok = "auth != null && auth.token.email === '$em'"
        return """
{
  "rules": {
    ".read": false,
    ".write": false,
    "visitas": {
      "${'$'}slug": {
        ".read": "$ok",
        "${'$'}dia": {
          ".write": true,
          ".validate": "newData.isNumber() && newData.val() === (data.exists() ? data.val() : 0) + 1"
        }
      }
    },
    "pedidos": {
      "${'$'}slug": {
        ".read": "$ok",
        "${'$'}cod": {
          ".write": "!data.exists() && newData.exists()",
          ".validate": "newData.hasChildren(['t','txt']) && newData.child('t').isNumber() && newData.child('txt').isString() && newData.child('txt').val().length < 6000"
        }
      }
    },
    "catalogo": {
      "${'$'}slug": {
        ".read": true,
        ".write": "$ok"
      }
    }
  }
}
""".trimIndent()
    }

    companion object {
        /**
         * Parsea el texto de un pedido (formato WhatsApp / ref de la web).
         */
        fun parsePedidoWA(store: Store, txt: String, t: Long = System.currentTimeMillis()): Pedido {
            val refRe = Regex("""\[ref:([0-9A-Za-z]{8,12})\|([^|\]]*)\|([A-Za-z]{3})(?:\|([A-Za-z]))?]""")
            val m = refRe.find(txt)
            val code = m?.groupValues?.getOrNull(1)?.uppercase() ?: ""
            val moneda = m?.groupValues?.getOrNull(3) ?: store.mp

            val nombreRe = Regex("""mi nombre es \*([^*\n]+)\*""", RegexOption.IGNORE_CASE)
            val ubicRe = Regex("""Ubicaci[oó]n[^:\n]*:\*?\s*([^\n]+)""", RegexOption.IGNORE_CASE)
            val cliente = nombreRe.find(txt)?.groupValues?.getOrNull(1)?.trim() ?: ""
            val ubicacion = ubicRe.find(txt)?.groupValues?.getOrNull(1)?.trim() ?: ""

            val items = mutableListOf<PedidoItem>()
            // Formato ref: id:qty,id:qty
            val pairs = m?.groupValues?.getOrNull(2)?.split(",") ?: emptyList()
            for (par in pairs) {
                val k = par.lastIndexOf(':')
                if (k < 1) continue
                val id = try {
                    java.net.URLDecoder.decode(par.substring(0, k), "UTF-8")
                } catch (_: Exception) {
                    par.substring(0, k)
                }
                val q = par.substring(k + 1).toIntOrNull()?.coerceAtLeast(1) ?: 1
                val p = store.prods.find { it.id == id }
                items += PedidoItem(
                    id = id,
                    n = p?.nombre ?: id,
                    q = q,
                    precio = p?.let { if (it.ofertaPrecio > 0) it.ofertaPrecio else it.precio } ?: 0.0
                )
            }
            // Fallback: líneas tipo • *Nombre* x2
            if (items.isEmpty()) {
                val lineRe = Regex("""^[•\-]\s*\*(.+?)\*\s*x(\d+)""", setOf(RegexOption.MULTILINE, RegexOption.IGNORE_CASE))
                for (lm in lineRe.findAll(txt)) {
                    items += PedidoItem(n = lm.groupValues[1], q = lm.groupValues[2].toIntOrNull() ?: 1)
                }
            }

            return Pedido(
                c = code.ifBlank { "P${System.currentTimeMillis() % 100000}" },
                t = if (t > 0) t else System.currentTimeMillis(),
                txt = txt,
                cliente = cliente,
                ubicacion = ubicacion,
                items = items,
                moneda = moneda
            )
        }
    }
}
