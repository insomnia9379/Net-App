package com.netapp.marketplacescanner.ui

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.netapp.marketplacescanner.ui.screens.EditSearchScreen
import com.netapp.marketplacescanner.ui.screens.LoginScreen
import com.netapp.marketplacescanner.ui.screens.ResultsScreen
import com.netapp.marketplacescanner.ui.screens.SavedScreen
import com.netapp.marketplacescanner.ui.screens.ScanScreen
import com.netapp.marketplacescanner.ui.screens.SearchListScreen
import com.netapp.marketplacescanner.ui.screens.SettingsScreen

object Routes {
    const val LIST = "list"
    const val EDIT = "edit"
    const val RESULTS = "results"
    const val SCAN = "scan"
    const val SAVED = "saved"
    const val LOGIN = "login"
    const val SETTINGS = "settings"
}

@Composable
fun AppRoot(vm: ScannerViewModel = viewModel()) {
    val nav = rememberNavController()

    NavHost(navController = nav, startDestination = Routes.LIST) {
        composable(Routes.LIST) {
            SearchListScreen(
                vm = vm,
                onAdd = { nav.navigate("${Routes.EDIT}/-1") },
                onOpen = { id -> nav.navigate("${Routes.RESULTS}/$id") },
                onLogin = { nav.navigate(Routes.LOGIN) },
                onSettings = { nav.navigate(Routes.SETTINGS) },
                onSaved = { nav.navigate(Routes.SAVED) },
            )
        }
        composable("${Routes.EDIT}/{id}") { entry ->
            val id = entry.arguments?.getString("id")?.toLongOrNull() ?: -1L
            EditSearchScreen(
                vm = vm,
                searchId = id,
                onDone = { nav.popBackStack() },
            )
        }
        composable("${Routes.RESULTS}/{id}") { entry ->
            val id = entry.arguments?.getString("id")?.toLongOrNull() ?: return@composable
            ResultsScreen(
                vm = vm,
                searchId = id,
                onBack = { nav.popBackStack() },
                onEdit = { nav.navigate("${Routes.EDIT}/$id") },
                onScan = { nav.navigate("${Routes.SCAN}/$id") },
            )
        }
        composable("${Routes.SCAN}/{id}") { entry ->
            val id = entry.arguments?.getString("id")?.toLongOrNull() ?: return@composable
            ScanScreen(
                vm = vm,
                searchId = id,
                onBack = { nav.popBackStack() },
                onViewResults = { nav.popBackStack() },
            )
        }
        composable(Routes.SAVED) {
            SavedScreen(vm = vm, onBack = { nav.popBackStack() })
        }
        composable(Routes.LOGIN) {
            LoginScreen(
                vm = vm,
                onDone = { nav.popBackStack() },
            )
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(
                vm = vm,
                onBack = { nav.popBackStack() },
                onLogin = { nav.navigate(Routes.LOGIN) },
            )
        }
    }
}
