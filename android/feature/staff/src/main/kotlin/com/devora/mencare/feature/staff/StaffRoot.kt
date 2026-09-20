package com.devora.mencare.feature.staff

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.People
import androidx.compose.material.icons.outlined.PersonOutline
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.devora.mencare.core.designsystem.component.brandNavBarItemColors
import com.devora.mencare.core.designsystem.component.readableWidth
import com.devora.mencare.core.designsystem.theme.Bone
import com.devora.mencare.core.designsystem.theme.Stone
import com.devora.mencare.core.ui.crm.CrmDetailScreen
import com.devora.mencare.core.ui.crm.CrmListScreen
import com.devora.mencare.core.ui.notifications.NotificationsScreen

private const val AGENDA = "staff/agenda"
private const val CLIENTS = "staff/clients"
private const val PROFILE = "staff/profile"
private const val DETAIL = "staff/appointment/{appointmentId}"
private const val CLIENT_DETAIL = "staff/client/{clientId}"
private const val NOTIFICATIONS = "staff/notifications"

@Composable
fun StaffRoot(onLoggedOut: () -> Unit) {
    val navController = rememberNavController()
    val backStack by navController.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route

    val showBar = currentRoute in setOf(AGENDA, CLIENTS, PROFILE)

    Scaffold(
        containerColor = Bone,
        // Ogni schermata dipinge la sua banda scura sotto la status bar e la sua
        // barra azioni sopra quella di sistema: lo scaffold non deve aggiungere
        // quegli inset una seconda volta.
        contentWindowInsets = WindowInsets(0),
        bottomBar = {
            if (showBar) {
                Column {
                    HorizontalDivider(color = Stone)
                    // Sui tablet le voci restano raccolte al centro, non sparse sui bordi.
                    NavigationBar(
                        modifier = Modifier.readableWidth(),
                        containerColor = Bone,
                        tonalElevation = 0.dp,
                    ) {
                        StaffBarItem(navController, currentRoute, AGENDA, stringResource(R.string.staff_tab_agenda)) {
                            Icon(Icons.Outlined.CalendarMonth, contentDescription = null)
                        }
                        StaffBarItem(navController, currentRoute, CLIENTS, stringResource(R.string.staff_tab_clients)) {
                            Icon(Icons.Outlined.People, contentDescription = null)
                        }
                        StaffBarItem(navController, currentRoute, PROFILE, stringResource(R.string.staff_tab_profile)) {
                            Icon(Icons.Outlined.PersonOutline, contentDescription = null)
                        }
                    }
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = AGENDA,
            modifier = Modifier.padding(padding),
        ) {
            composable(AGENDA) {
                AgendaScreen(
                    onAppointment = { id -> navController.navigate("staff/appointment/$id") },
                    onProfile = { navController.navigateTab(PROFILE) },
                    onNotifications = { navController.navigate(NOTIFICATIONS) },
                )
            }
            composable(CLIENTS) {
                // Stessa lista del titolare.
                CrmListScreen(onClient = { id -> navController.navigate("staff/client/$id") })
            }
            composable(PROFILE) {
                StaffProfileScreen(onLoggedOut = onLoggedOut)
            }
            composable(NOTIFICATIONS) {
                // Stessa pagina notifiche del cliente e del titolare.
                NotificationsScreen(onBack = { navController.popBackStack() })
            }
            composable(DETAIL) {
                AppointmentDetailScreen(onBack = { navController.popBackStack() })
            }
            composable(CLIENT_DETAIL) {
                // Stessa scheda del titolare.
                CrmDetailScreen(
                    showEconomics = false,
                    onBack = { navController.popBackStack() },
                    // La nuova prenotazione si apre dall'agenda: ci si porta lì.
                    onNewBooking = { navController.navigateTab(AGENDA) },
                )
            }
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.StaffBarItem(
    navController: NavHostController,
    currentRoute: String?,
    route: String,
    label: String,
    icon: @Composable () -> Unit,
) {
    NavigationBarItem(
        selected = currentRoute == route,
        onClick = { navController.navigateTab(route) },
        icon = icon,
        label = { Text(label) },
        colors = brandNavBarItemColors(),
    )
}

private fun NavHostController.navigateTab(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
