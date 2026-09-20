package com.devora.mencare.feature.client

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddCircleOutline
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.devora.mencare.core.designsystem.component.BrandBottomBar
import com.devora.mencare.core.designsystem.component.BrandBottomBarItem
import com.devora.mencare.core.designsystem.theme.Bone
import com.devora.mencare.core.ui.notifications.NotificationsScreen
import com.devora.mencare.feature.client.appointments.AppointmentsScreen
import com.devora.mencare.feature.client.booking.BookingViewModel
import com.devora.mencare.feature.client.booking.BookingWizardScreen
import com.devora.mencare.feature.client.home.ClientHomeScreen
import com.devora.mencare.feature.client.home.QuickSlot
import com.devora.mencare.feature.client.profile.ProfileScreen

private const val HOME = "client/home"
private const val BOOKING =
    "client/booking?rebookId={rebookId}&editId={editId}&date={date}&time={time}&operatorId={operatorId}&services={services}"
private const val APPOINTMENTS = "client/appointments"
private const val PROFILE = "client/profile"
private const val NOTIFICATIONS = "client/notifications"

private fun bookingRoute(rebookId: String? = null) =
    "client/booking?rebookId=${rebookId.orEmpty()}"

/** Modifica di un appuntamento futuro: il wizard riparte dall'operatore, già compilato. */
private fun editRoute(appointmentId: String) = "client/booking?editId=$appointmentId"

/** Slot scelto dalla home: il wizard si apre già su quel giorno e quell'ora. */
private fun quickSlotRoute(slot: QuickSlot) = buildString {
    append("client/booking?rebookId=")
    append("&date=").append(slot.date)
    // L'ora viaggia in secondi: niente due punti dentro la route.
    append("&time=").append(slot.time.toSecondOfDay())
    append("&operatorId=").append(slot.operatorId.orEmpty())
    append("&services=").append(slot.serviceIds.joinToString(","))
}

/** Client area: bottom navigation + inner nav graph. */
@Composable
fun ClientRoot(onLoggedOut: () -> Unit) {
    val navController = rememberNavController()
    val backStack by navController.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route

    // Il wizard di prenotazione ha la sua barra azioni in fondo: la tab bar
    // ruberebbe quello spazio, come mostra il mockup.
    val showBar = currentRoute != NOTIFICATIONS && currentRoute != BOOKING

    Scaffold(
        containerColor = Bone,
        // Every client screen paints its own dark band under the status bar via
        // DarkHeader, so the scaffold must not add that inset a second time.
        contentWindowInsets = WindowInsets(0),
        bottomBar = {
            if (showBar) {
                BrandBottomBar {
                    BarItem(navController, currentRoute, HOME, Icons.Outlined.Home, stringResource(R.string.client_tab_home))
                    BarItem(navController, currentRoute, BOOKING, Icons.Outlined.AddCircleOutline, stringResource(R.string.client_tab_book))
                    BarItem(navController, currentRoute, APPOINTMENTS, Icons.Outlined.CalendarMonth, stringResource(R.string.client_tab_appointments))
                    BarItem(navController, currentRoute, PROFILE, Icons.Outlined.Person, stringResource(R.string.client_tab_profile))
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = HOME,
            modifier = Modifier.padding(padding),
        ) {
            composable(HOME) {
                ClientHomeScreen(
                    // Sempre "fresh": il wizard riparte azzerato, mai dal punto lasciato.
                    onBook = { navController.navigateFresh(bookingRoute()) },
                    onRebook = { id -> navController.navigateFresh(bookingRoute(id)) },
                    onQuickSlot = { slot -> navController.navigateFresh(quickSlotRoute(slot)) },
                    onHistory = { navController.navigateTab(APPOINTMENTS) },
                    onNotifications = { navController.navigate(NOTIFICATIONS) },
                )
            }
            composable(
                BOOKING,
                arguments = listOf(
                    navArgument("rebookId") { defaultValue = "" },
                    navArgument("editId") { defaultValue = "" },
                    navArgument("date") { defaultValue = "" },
                    navArgument("time") { defaultValue = "" },
                    navArgument("operatorId") { defaultValue = "" },
                    navArgument("services") { defaultValue = "" },
                ),
            ) {
                val viewModel: BookingViewModel = hiltViewModel()
                BookingWizardScreen(
                    // Annulla porta ai propri appuntamenti, non alla home.
                    onCancel = { navController.navigateTab(APPOINTMENTS) },
                    onConfirmed = { navController.navigateTab(HOME) },
                    viewModel = viewModel,
                )
            }
            composable(APPOINTMENTS) {
                AppointmentsScreen(
                    onBook = { navController.navigateFresh(bookingRoute()) },
                    onRebook = { id -> navController.navigateFresh(bookingRoute(id)) },
                    onEdit = { id -> navController.navigateFresh(editRoute(id)) },
                )
            }
            composable(PROFILE) {
                ProfileScreen(onLoggedOut = onLoggedOut)
            }
            composable(NOTIFICATIONS) {
                NotificationsScreen(onBack = { navController.popBackStack() })
            }
        }
    }
}

private fun NavHostController.navigateTab(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

/**
 * Come [navigateTab] ma senza ripristinare lo stato salvato: quando la home
 * passa un appuntamento da riprenotare o uno slot, devono vincere gli argomenti
 * nuovi, non il wizard lasciato a metà la volta scorsa.
 */
private fun NavHostController.navigateFresh(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.BarItem(
    navController: NavHostController,
    currentRoute: String?,
    route: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
) {
    BrandBottomBarItem(
        selected = currentRoute == route,
        // La tab Prenota apre sempre un wizard nuovo (come su iOS).
        onClick = {
            if (route == BOOKING) navController.navigateFresh(bookingRoute()) else navController.navigateTab(route)
        },
        icon = icon,
        label = label,
    )
}
