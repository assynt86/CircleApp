package com.example.circleapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.circleapp.ui.theme.CircleAppTheme
import com.example.circleapp.ui.viewmodels.BugReportViewModel
import com.example.circleapp.ui.viewmodels.HomeViewModel
import com.example.circleapp.ui.viewmodels.MainViewModel
import com.example.circleapp.ui.views.BlockedUsersView
import com.example.circleapp.ui.views.BugReportView
import com.example.circleapp.ui.views.CameraView
import com.example.circleapp.ui.views.CircleView
import com.example.circleapp.ui.views.HomeView
import com.example.circleapp.ui.views.ProfileView
import com.example.circleapp.ui.views.CircleSettingsView
import com.example.circleapp.ui.views.FriendsView
import com.example.circleapp.ui.views.SettingsView
import kotlinx.coroutines.launch
import com.google.firebase.auth.FirebaseAuth
import com.example.circleapp.ui.views.AuthView
import com.example.circleapp.ui.viewmodels.AuthViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val mainViewModel: MainViewModel = viewModel()
            val useSystemTheme by mainViewModel.useSystemTheme.collectAsState(initial = true)
            val isDarkMode by mainViewModel.isDarkMode.collectAsState(initial = true)

            CircleAppTheme(
                useSystemTheme = useSystemTheme,
                isDarkMode = isDarkMode
            ) {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    AppNavigation()
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AppNavigation() {
    val navController = rememberNavController()
    val coroutineScope = rememberCoroutineScope()
    val auth = FirebaseAuth.getInstance()

    // Track authentication state reactively
    var currentUser by remember { mutableStateOf(auth.currentUser) }

    DisposableEffect(auth) {
        val listener = FirebaseAuth.AuthStateListener {
            currentUser = it.currentUser
        }
        auth.addAuthStateListener(listener)
        onDispose {
            auth.removeAuthStateListener(listener)
        }
    }

    // Determine the start destination once based on initial state.
    val startDest = if (currentUser != null) "main" else "auth"

    // Guard the app: if auth state becomes null, immediately push to auth screen
    LaunchedEffect(currentUser) {
        if (currentUser == null) {
            navController.navigate("auth") {
                popUpTo(0) { inclusive = true }
            }
        }
    }

    NavHost(navController = navController, startDestination = startDest) {

        // -------- AUTH SCREEN --------
        composable("auth") {
            val authVm: AuthViewModel = viewModel()
            AuthView(
                authViewModel = authVm,
                onAuthed = {
                    navController.navigate("main") {
                        popUpTo("auth") { inclusive = true }
                    }
                }
            )
        }

        // -------- MAIN (Pager) --------
        composable(
            route = "main?startPage={startPage}&circleId={circleId}",
            arguments = listOf(
                navArgument("startPage") {
                    type = NavType.IntType
                    defaultValue = 0 
                },
                navArgument("circleId") {
                    type = NavType.StringType
                    nullable = true
                }
            )
        ) { backStackEntry ->
            val homeViewModel: HomeViewModel = viewModel()
            val startPage = backStackEntry.arguments?.getInt("startPage") ?: 0
            val circleId = backStackEntry.arguments?.getString("circleId")
            val pagerState = rememberPagerState(initialPage = startPage) { 3 }

            HorizontalPager(state = pagerState) { page ->
                when (page) {
                    0 -> CameraView(
                        homeViewModel = homeViewModel,
                        entryPointCircleId = circleId,
                        onCancel = {
                            if (circleId != null) {
                                navController.popBackStack()
                            } else {
                                coroutineScope.launch { pagerState.animateScrollToPage(1) }
                            }
                        },
                        onUploadsComplete = {},
                        onUploadFailed = { _, _ -> }
                    )

                    1 -> HomeView(
                        homeViewModel = homeViewModel,
                        onCircleClick = { newCircleId ->
                            navController.navigate("circle/$newCircleId")
                        },
                        onJoinCircle = { newCircleId ->
                            navController.navigate("circle/$newCircleId")
                        },
                        onCreateCircle = { newCircleId ->
                            navController.navigate("circle/$newCircleId")
                        },
                        onInvitesClick = {
                            navController.navigate("friends?tab=2")
                        }
                    )

                    2 -> ProfileView(
                        onLogout = {
                            auth.signOut()
                        },
                        onFriendsClick = {
                            navController.navigate("friends")
                        },
                        onSettingsClick = {
                            navController.navigate("settings")
                        }
                    )
                }
            }
        }

        // -------- Circle Detail --------
        composable(
            route = "circle/{circleId}",
            arguments = listOf(navArgument("circleId") { type = NavType.StringType })
        ) { backStackEntry ->
            val circleId = backStackEntry.arguments?.getString("circleId")
            if (circleId != null) {
                CircleView(
                    circleId = circleId,
                    onBack = { navController.popBackStack() },
                    onCameraClick = { currentCircleId ->
                        navController.navigate("main?startPage=0&circleId=$currentCircleId")
                    },
                    onSettingsClick = { currentCircleId ->
                        navController.navigate("circle_settings/$currentCircleId")
                    }
                )
            }
        }

        // -------- Circle Settings --------
        composable(
            route = "circle_settings/{circleId}",
            arguments = listOf(navArgument("circleId") { type = NavType.StringType })
        ) { backStackEntry ->
            val circleId = backStackEntry.arguments?.getString(
                "circleId"
            )
            if (circleId != null) {
                CircleSettingsView(
                    circleId = circleId,
                    onBack = { navController.popBackStack() },
                    onDeleted = {
                        navController.navigate("main?startPage=1") {
                            popUpTo("main") { inclusive = true }
                        }
                    }
                )
            }
        }

        // -------- App Settings --------
        composable("settings") {
            SettingsView(
                onBack = { navController.popBackStack() },
                onReportBug = { navController.navigate("bug_report") },
                onBlockedAccountsClick = { navController.navigate("blocked_users") }
            )
        }

        // -------- Bug Report --------
        composable("bug_report") {
            val bugReportViewModel: BugReportViewModel = viewModel()
            BugReportView(
                onBack = { navController.popBackStack() },
                onReport = { report ->
                    bugReportViewModel.submitBugReport(report) {
                        navController.popBackStack()
                    }
                }
            )
        }

        // -------- Blocked Users --------
        composable("blocked_users") {
            BlockedUsersView(onBack = { navController.popBackStack() })
        }

        // -------- Friends --------
        composable(
            route = "friends?tab={tab}",
            arguments = listOf(
                navArgument("tab") {
                    type = NavType.IntType
                    defaultValue = 0
                }
            )
        ) { backStackEntry ->
            val initialTab = backStackEntry.arguments?.getInt("tab") ?: 0
            FriendsView(
                onBack = { navController.popBackStack() },
                initialTab = initialTab
            )
        }
    }
}
