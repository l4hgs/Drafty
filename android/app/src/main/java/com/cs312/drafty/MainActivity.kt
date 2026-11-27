package com.cs312.drafty

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.cs312.drafty.ui.DraftyViewModel
import com.cs312.drafty.ui.screens.HomeScreen
import com.cs312.drafty.ui.screens.IdeaContainerScreen
import com.cs312.drafty.ui.screens.OnboardingScreen
import com.cs312.drafty.ui.theme.DraftyTheme
import kotlinx.coroutines.flow.MutableStateFlow

object Routes {
    const val ONBOARDING = "onboarding"
    const val HOME = "home"
    const val CONTAINER_DETAIL = "ContainerDetail/{id}"
    fun containerDetail(id: String) = "ContainerDetail/$id"
}

class MainActivity : ComponentActivity() {
    private val pendingAuthUri = MutableStateFlow<Uri?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        captureAuthCallback(intent)
        enableEdgeToEdge()
        setContent {
            DraftyTheme {
                DraftyNavHost(
                    pendingAuthUri = pendingAuthUri,
                    onConsumeAuthUri = { pendingAuthUri.value = null },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        captureAuthCallback(intent)
    }

    private fun captureAuthCallback(intent: Intent?) {
        val uri = intent?.data ?: return
        if (uri.scheme == "drafty" && uri.host == "auth-callback") {
            pendingAuthUri.value = uri
        }
    }
}

@Composable
fun DraftyNavHost(
    pendingAuthUri: MutableStateFlow<Uri?>,
    onConsumeAuthUri: () -> Unit,
) {
    val navController = rememberNavController()
    val vm: DraftyViewModel = viewModel() // activity-scoped; shared by all screens
    val authCallback by pendingAuthUri.collectAsState()

    LaunchedEffect(authCallback) {
        val uri = authCallback ?: return@LaunchedEffect
        onConsumeAuthUri()
        vm.handleOAuthCallback(uri) { res ->
            if (res.isSuccess) {
                navController.navigate(Routes.HOME) {
                    popUpTo(Routes.ONBOARDING) { inclusive = true }
                    launchSingleTop = true
                }
            }
        }
    }

    val startDestination = remember {
        if (vm.hasCompletedOnboarding) Routes.HOME else Routes.ONBOARDING
    }

    NavHost(navController, startDestination = startDestination) {
        composable(Routes.ONBOARDING) {
            OnboardingScreen(
                vm = vm,
                onFinished = {
                    navController.navigate(Routes.HOME) {
                        popUpTo(Routes.ONBOARDING) { inclusive = true }
                    }
                },
            )
        }
        composable(Routes.HOME) {
            HomeScreen(vm, onOpenContainer = { navController.navigate(Routes.containerDetail(it)) })
        }
        composable(
            Routes.CONTAINER_DETAIL,
            arguments = listOf(navArgument("id") { type = NavType.StringType }),
        ) { entry ->
            IdeaContainerScreen(
                id = entry.arguments?.getString("id").orEmpty(),
                vm = vm,
                onBack = { navController.popBackStack() },
            )
        }
    }
}
