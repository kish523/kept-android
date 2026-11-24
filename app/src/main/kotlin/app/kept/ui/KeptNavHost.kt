package app.kept.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import app.kept.AppContainer
import app.kept.ui.home.HomeScreen
import app.kept.ui.home.HomeViewModel
import app.kept.ui.review.ReviewScreen
import app.kept.ui.review.ReviewViewModel
import kotlinx.coroutines.flow.MutableStateFlow

private const val SAVED_MESSAGE = "saved_message"

@Composable
fun KeptNavHost(container: AppContainer, sharedReceiptId: MutableStateFlow<String?>) {
    val nav = rememberNavController()

    // A file shared into Kept goes straight to its review card.
    val shared by sharedReceiptId.collectAsStateWithLifecycle()
    LaunchedEffect(shared) {
        shared?.let {
            nav.navigate("review/$it")
            sharedReceiptId.value = null
        }
    }

    NavHost(navController = nav, startDestination = "home") {
        composable("home") { entry ->
            val message by entry.savedStateHandle.getStateFlow<String?>(SAVED_MESSAGE, null)
                .collectAsStateWithLifecycle()
            HomeScreen(
                viewModel = viewModel(factory = HomeViewModel.factory(container)),
                onOpenReceipt = { id -> nav.navigate("review/$id") },
                savedMessage = message,
                onMessageShown = { entry.savedStateHandle[SAVED_MESSAGE] = null },
            )
        }
        composable(
            route = "review/{receiptId}",
            arguments = listOf(navArgument("receiptId") { type = NavType.StringType }),
        ) { entry ->
            val receiptId = requireNotNull(entry.arguments?.getString("receiptId"))
            ReviewScreen(
                viewModel = viewModel(key = receiptId, factory = ReviewViewModel.factory(container, receiptId)),
                onSaved = { message ->
                    nav.previousBackStackEntry?.savedStateHandle?.set(SAVED_MESSAGE, message)
                    nav.popBackStack()
                },
                onBack = { nav.popBackStack() },
            )
        }
    }
}

fun Context.findActivity(): Activity? {
    var c: Context? = this
    while (c is ContextWrapper) {
        if (c is Activity) return c
        c = c.baseContext
    }
    return null
}
