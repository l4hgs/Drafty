package com.cs312.drafty

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
    val authSession by vm.authSession.collectAsState()

    LaunchedEffect(authCallback) {
        val uri = authCallback ?: return@LaunchedEffect
        onConsumeAuthUri()
        vm.handleOAuthCallback(uri) { res ->
            val currentRoute = navController.currentBackStackEntry?.destination?.route
            // If currently on Onboarding, let OnboardingScreen smoothly advance to Page 5 (Voice Setup)
            // instead of abruptly hard-cutting to Home.
            if (res.isSuccess && currentRoute != Routes.ONBOARDING) {
                navController.navigate(Routes.HOME) {
                    popUpTo(Routes.ONBOARDING) { inclusive = true }
                    launchSingleTop = true
                }
            }
        }
    }

    // Redirect back to Onboarding immediately if the user signs out
    LaunchedEffect(authSession) {
        val currentRoute = navController.currentBackStackEntry?.destination?.route
        if (authSession == null && currentRoute != null && currentRoute != Routes.ONBOARDING) {
            navController.navigate(Routes.ONBOARDING) {
                popUpTo(0) { inclusive = true }
                launchSingleTop = true
            }
        }
    }

    val startDestination = remember {
        if (vm.hasCompletedOnboarding) Routes.HOME else Routes.ONBOARDING
    }

    NavHost(
        navController = navController,
        startDestination = startDestination,
        enterTransition = { fadeIn(animationSpec = tween(280)) },
        exitTransition = { fadeOut(animationSpec = tween(220)) },
        popEnterTransition = { fadeIn(animationSpec = tween(260)) },
        popExitTransition = { fadeOut(animationSpec = tween(200)) },
    ) {
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
