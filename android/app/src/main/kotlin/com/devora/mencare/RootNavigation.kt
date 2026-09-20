package com.devora.mencare

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.devora.mencare.core.data.repository.AuthRepository
import com.devora.mencare.core.model.UserRole
import com.devora.mencare.feature.admin.AdminRoot
import com.devora.mencare.feature.auth.AUTH_GRAPH_ROUTE
import com.devora.mencare.feature.auth.authGraph
import com.devora.mencare.feature.client.ClientRoot
import com.devora.mencare.feature.staff.StaffRoot
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

private const val CLIENT_ROUTE = "area/client"
private const val STAFF_ROUTE = "area/staff"
private const val ADMIN_ROUTE = "area/admin"

@HiltViewModel
class SessionViewModel @Inject constructor(
    authRepository: AuthRepository,
) : ViewModel() {
    val role = authRepository.currentUser
        .map { it?.role }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)
}

/**
 * Role routing happens here: each role gets its own graph and a client
 * session can never reach staff/admin destinations — those graphs are only
 * navigated to for the authenticated role, and every logout pops back to auth.
 */
@Composable
fun RootNavigation(sessionViewModel: SessionViewModel = hiltViewModel()) {
    val navController = rememberNavController()
    val role by sessionViewModel.role.collectAsStateWithLifecycle()

    fun routeFor(userRole: UserRole) = when (userRole) {
        UserRole.CLIENT -> CLIENT_ROUTE
        UserRole.STAFF -> STAFF_ROUTE
        UserRole.OWNER -> ADMIN_ROUTE
    }

    fun backToAuth() {
        navController.navigate(AUTH_GRAPH_ROUTE) {
            popUpTo(0) { inclusive = true }
        }
    }

    // A logout clears the session before any screen callback can run: the role
    // guards below stop rendering the area on the same frame, so the screens'
    // own onLoggedOut effects never fire. React to the session itself instead.
    LaunchedEffect(role) {
        val areaRoutes = setOf(CLIENT_ROUTE, STAFF_ROUTE, ADMIN_ROUTE)
        val current = navController.currentDestination?.route
        val known = role
        when {
            known == null && current in areaRoutes -> backToAuth()
            // Sessione ripresa all'avvio: i token erano ancora validi e il
            // profilo è arrivato da solo, quindi si entra direttamente
            // nell'area del ruolo invece di chiedere di nuovo la password.
            known != null && current !in areaRoutes ->
                navController.navigate(routeFor(known)) { popUpTo(0) { inclusive = true } }
        }
    }

    NavHost(navController = navController, startDestination = AUTH_GRAPH_ROUTE) {
        authGraph(
            navController = navController,
            onAuthenticated = { authenticatedRole ->
                navController.navigate(routeFor(authenticatedRole)) {
                    popUpTo(0) { inclusive = true }
                }
            },
        )
        composable(CLIENT_ROUTE) {
            if (role == UserRole.CLIENT) ClientRoot(onLoggedOut = ::backToAuth)
        }
        composable(STAFF_ROUTE) {
            if (role == UserRole.STAFF) StaffRoot(onLoggedOut = ::backToAuth)
        }
        composable(ADMIN_ROUTE) {
            if (role == UserRole.OWNER) AdminRoot(onLoggedOut = ::backToAuth)
        }
    }
}
