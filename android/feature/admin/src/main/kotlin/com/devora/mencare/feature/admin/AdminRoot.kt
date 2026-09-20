package com.devora.mencare.feature.admin

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.outlined.People
import androidx.compose.material.icons.outlined.PersonOutline
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.devora.mencare.core.designsystem.component.readableWidth
import com.devora.mencare.core.designsystem.theme.Bone
import com.devora.mencare.core.designsystem.theme.Ink
import com.devora.mencare.core.designsystem.theme.OliveWood
import com.devora.mencare.core.designsystem.theme.Stone
import com.devora.mencare.core.ui.crm.CrmDetailScreen
import com.devora.mencare.core.ui.crm.CrmListScreen
import com.devora.mencare.core.ui.notifications.NotificationsScreen
import com.devora.mencare.feature.admin.agenda.WeeklyAgendaScreen
import com.devora.mencare.feature.admin.campaign.CampaignScreen
import com.devora.mencare.feature.admin.dashboard.DashboardScreen
import com.devora.mencare.feature.admin.manage.ManageScreen
import com.devora.mencare.feature.admin.manage.OperatorEditScreen
import com.devora.mencare.feature.admin.manage.ServiceEditScreen
import com.devora.mencare.feature.admin.profile.AdminProfileScreen

private const val AGENDA = "admin/agenda"
private const val DASHBOARD = "admin/dashboard"
private const val MANAGE = "admin/manage"
private const val CLIENTS = "admin/clients"
private const val PROFILE = "admin/profile"
private const val CAMPAIGN = "admin/campaign"
private const val SERVICE_EDIT = "admin/service?serviceId={serviceId}"
private const val OPERATOR_NEW = "admin/operator/new"
private const val CLIENT_DETAIL = "admin/client/{clientId}"
private const val NOTIFICATIONS = "admin/notifications"

@Composable
fun AdminRoot(onLoggedOut: () -> Unit) {
    val navController = rememberNavController()
    val backStack by navController.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route

    val showBar = currentRoute in setOf(AGENDA, DASHBOARD, MANAGE, CLIENTS, PROFILE)

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
                    // L'agenda sta al centro: è la schermata che il titolare apre più spesso.
                    AdminBarItem(navController, currentRoute, DASHBOARD, stringResource(R.string.admin_tab_dashboard)) {
                        Icon(Icons.Outlined.Insights, contentDescription = null)
                    }
                    AdminBarItem(navController, currentRoute, CLIENTS, stringResource(R.string.admin_tab_clients)) {
                        Icon(Icons.Outlined.People, contentDescription = null)
                    }
                    AdminBarItem(navController, currentRoute, AGENDA, stringResource(R.string.admin_tab_agenda)) {
                        Icon(Icons.Outlined.CalendarMonth, contentDescription = null)
                    }
                    AdminBarItem(navController, currentRoute, MANAGE, stringResource(R.string.admin_tab_manage)) {
                        Icon(Icons.Outlined.Tune, contentDescription = null)
                    }
                    AdminBarItem(navController, currentRoute, PROFILE, stringResource(R.string.admin_tab_profile)) {
                        Icon(Icons.Outlined.PersonOutline, contentDescription = null)
                    }
                    }
                    }
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            // Si atterra sull'agenda: è la schermata che il titolare apre più spesso.
            startDestination = AGENDA,
            modifier = Modifier.padding(padding),
        ) {
            composable(AGENDA) {
                WeeklyAgendaScreen(onNotifications = { navController.navigate(NOTIFICATIONS) })
            }
            composable(NOTIFICATIONS) {
                // Stessa pagina notifiche del cliente e dell'operatore.
                NotificationsScreen(onBack = { navController.popBackStack() })
            }
            composable(DASHBOARD) {
                DashboardScreen(onSendCampaign = { navController.navigate(CAMPAIGN) })
            }
            composable(OPERATOR_NEW) {
                OperatorEditScreen(onBack = { navController.popBackStack() })
            }
            composable(MANAGE) {
                ManageScreen(
                    onEditService = { id -> navController.navigate("admin/service?serviceId=${id.orEmpty()}") },
                    onNewOperator = { navController.navigate(OPERATOR_NEW) },
                )
            }
            composable(PROFILE) {
                AdminProfileScreen(onLoggedOut = onLoggedOut)
            }
            composable(CLIENTS) {
                CrmListScreen(onClient = { id -> navController.navigate("admin/client/$id") })
            }
            composable(CAMPAIGN) {
                CampaignScreen(onBack = { navController.popBackStack() })
            }
            composable(
                SERVICE_EDIT,
                arguments = listOf(navArgument("serviceId") { defaultValue = "" }),
            ) {
                ServiceEditScreen(onBack = { navController.popBackStack() })
            }
            composable(CLIENT_DETAIL) {
                CrmDetailScreen(
                    showEconomics = true,
                    onBack = { navController.popBackStack() },
                    // Lo sheet di prenotazione vive nell'agenda: ci si porta lì.
                    onNewBooking = { navController.navigate(AGENDA) },
                )
            }
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.AdminBarItem(
    navController: NavHostController,
    currentRoute: String?,
    route: String,
    label: String,
    icon: @Composable () -> Unit,
) {
    NavigationBarItem(
        selected = currentRoute == route,
        onClick = {
            navController.navigate(route) {
                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                launchSingleTop = true
                restoreState = true
            }
        },
        icon = icon,
        label = { Text(label) },
        colors = NavigationBarItemDefaults.colors(
            selectedIconColor = OliveWood,
            selectedTextColor = OliveWood,
            unselectedIconColor = Ink,
            unselectedTextColor = Ink,
            indicatorColor = Color.Transparent,
        ),
    )
}
