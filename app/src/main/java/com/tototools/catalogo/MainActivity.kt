package com.tototools.catalogo

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.tototools.catalogo.data.Product
import com.tototools.catalogo.ui.screens.PedidosScreen
import com.tototools.catalogo.ui.screens.ProductEditScreen
import com.tototools.catalogo.ui.screens.ProductListScreen
import com.tototools.catalogo.ui.screens.SettingsScreen
import com.tototools.catalogo.ui.screens.formatPrice
import com.tototools.catalogo.ui.theme.CatalogoTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            CatalogoTheme {
                Surface(Modifier.fillMaxSize()) {
                    CatalogoApp()
                }
            }
        }
    }
}

@Composable
fun CatalogoApp(vm: CatalogoViewModel = viewModel()) {
    val nav = rememberNavController()
    val context = LocalContext.current
    val state by vm.state.collectAsState()
    val store = state.currentStore

    fun shareProduct(p: Product) {
        val moneda = store?.mp ?: "CUP"
        val precio = if (p.ofertaPrecio > 0) formatPrice(p.ofertaPrecio, moneda)
        else formatPrice(p.precio, moneda)
        val text = buildString {
            appendLine("*${p.nombre}*")
            appendLine(precio)
            if (p.desc.isNotBlank()) appendLine(p.desc)
            val wa = store?.wa?.filter { it.isDigit() }
            if (!wa.isNullOrBlank()) appendLine("\nWhatsApp: https://wa.me/$wa")
        }
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        context.startActivity(Intent.createChooser(intent, "Compartir producto"))
    }

    NavHost(navController = nav, startDestination = "products") {
        composable("products") {
            ProductListScreen(
                vm = vm,
                onProductClick = { p -> nav.navigate("edit/${p.id}") },
                onAddClick = { nav.navigate("edit/new") },
                onSettingsClick = { nav.navigate("settings") },
                onPedidosClick = { nav.navigate("pedidos") }
            )
        }
        composable(
            route = "edit/{id}",
            arguments = listOf(navArgument("id") { type = NavType.StringType })
        ) { entry ->
            val id = entry.arguments?.getString("id")
            ProductEditScreen(
                vm = vm,
                productId = if (id == "new") null else id,
                onBack = { nav.popBackStack() },
                onShare = { shareProduct(it) }
            )
        }
        composable("settings") {
            SettingsScreen(vm = vm, onBack = { nav.popBackStack() })
        }
        composable("pedidos") {
            PedidosScreen(vm = vm, onBack = { nav.popBackStack() })
        }
    }
}
