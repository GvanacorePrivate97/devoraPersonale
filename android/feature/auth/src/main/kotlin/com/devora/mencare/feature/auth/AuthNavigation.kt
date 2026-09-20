package com.devora.mencare.feature.auth

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import androidx.navigation.navigation
import com.devora.mencare.core.model.UserRole

const val AUTH_GRAPH_ROUTE = "auth"
private const val SPLASH_ROUTE = "auth/splash"
private const val LOGIN_ROUTE = "auth/login"
private const val REGISTER_ROUTE = "auth/register"
private const val RECOVER_ROUTE = "auth/recover"

fun NavGraphBuilder.authGraph(
    navController: NavHostController,
    onAuthenticated: (UserRole) -> Unit,
) {
    navigation(startDestination = SPLASH_ROUTE, route = AUTH_GRAPH_ROUTE) {
        composable(SPLASH_ROUTE) {
            SplashScreen(
                onFinished = {
                    navController.navigate(LOGIN_ROUTE) {
                        popUpTo(SPLASH_ROUTE) { inclusive = true }
                    }
                },
            )
        }
        composable(LOGIN_ROUTE) {
            LoginScreen(
                onLoggedIn = onAuthenticated,
                onRegister = { navController.navigate(REGISTER_ROUTE) },
                onForgotPassword = { navController.navigate(RECOVER_ROUTE) },
            )
        }
        composable(REGISTER_ROUTE) {
            RegisterScreen(
                onRegistered = onAuthenticated,
                onBack = { navController.popBackStack() },
            )
        }
        composable(RECOVER_ROUTE) {
            RecoverScreen(onBack = { navController.popBackStack() })
        }
    }
}
