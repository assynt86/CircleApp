package com.crcleapp.crcle

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.crcleapp.crcle.ui.theme.CircleAppTheme
import com.crcleapp.crcle.ui.viewmodels.BugReportViewModel
import com.crcleapp.crcle.ui.viewmodels.HomeViewModel
import com.crcleapp.crcle.ui.viewmodels.MainViewModel
import com.crcleapp.crcle.ui.views.BlockedUsersView
import com.crcleapp.crcle.ui.views.BugReportView
import com.crcleapp.crcle.ui.views.CameraView
import com.crcleapp.crcle.ui.views.CircleView
import com.crcleapp.crcle.ui.views.HomeView
import com.crcleapp.crcle.ui.views.CircleSettingsView
import com.crcleapp.crcle.ui.views.FriendsView
import com.crcleapp.crcle.ui.views.ProfileView
import com.crcleapp.crcle.ui.views.SettingsView
import com.crcleapp.crcle.ui.views.NotificationsView
import com.crcleapp.crcle.ui.views.NotificationSettingsView
import kotlinx.coroutines.launch
import com.google.firebase.auth.FirebaseAuth
import com.crcleapp.crcle.ui.views.AuthView
import com.crcleapp.crcle.ui.viewmodels.AuthViewModel
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.messaging.FirebaseMessaging

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // --- TEST NOTIFICATIONS SETUP ---
        FirebaseMessaging.getInstance().subscribeToTopic("test_notifications")
            .addOnCompleteListener { task ->
                if (task.isSuccessful) Log.d("FCM", "Subscribed to test_notifications")
            }

        // Save token to user doc whenever it changes or on app start
        FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
            if (task.isSuccessful) {
                val token = task.result
                val uid = FirebaseAuth.getInstance().currentUser?.uid
                if (uid != null && token != null) {
                    FirebaseFirestore.getInstance().collection("users").document(uid)
                        .update("fcmToken", token)
                }
            }
        }

        setContent {
            val mainViewModel: MainViewModel = viewModel()
            val useSystemTheme by mainViewModel.useSystemTheme.collectAsState(initial = true)
            val isDarkMode by mainViewModel.isDarkMode.collectAsState(initial = true)

            CircleAppTheme(
                useSystemTheme = useSystemTheme,
                isDarkMode = isDarkMode
            ) {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    AppNavigation(mainViewModel)
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AppNavigation(mainViewModel: MainViewModel) {
    val navController = rememberNavController()
    val coroutineScope = rememberCoroutineScope()
    val auth = FirebaseAuth.getInstance()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    
    // Use null as initial state to detect when preference is actually loaded from DataStore
    val launchOnCameraPref by mainViewModel.launchOnCamera.collectAsState(initial = null)

    // Track authentication state reactively and wait for the Firestore user profile
    // before allowing the app to enter the authenticated navigation flow.
    var currentUser by remember { mutableStateOf(auth.currentUser) }
    var hasUserProfile by remember { mutableStateOf<Boolean?>(null) }
    var userProfileListener by remember { mutableStateOf<ListenerRegistration?>(null) }
    var tokenSyncedUid by remember { mutableStateOf<String?>(null) }

    DisposableEffect(auth) {
        val listener = FirebaseAuth.AuthStateListener { firebaseAuth ->
            val user = firebaseAuth.currentUser
            currentUser = user

            if (user == null) {
                tokenSyncedUid = null
            }

            userProfileListener?.remove()
            userProfileListener = null

            if (user == null) {
                hasUserProfile = false
            } else {
                hasUserProfile = null
                userProfileListener = FirebaseFirestore.getInstance().collection("users").document(user.uid)
                    .addSnapshotListener { snapshot, error ->
                        if (auth.currentUser?.uid != user.uid) return@addSnapshotListener

                        if (error != null) {
                            Log.w("AppNavigation", "Failed to observe user profile", error)
                            return@addSnapshotListener
                        }

                        val profileExists = snapshot?.exists() == true
                        hasUserProfile = profileExists

                        if (profileExists && tokenSyncedUid != user.uid) {
                            tokenSyncedUid = user.uid
                            FirebaseMessaging.getInstance().token.addOnSuccessListener { token ->
                                FirebaseFirestore.getInstance().collection("users").document(user.uid)
                                    .update("fcmToken", token)
                            }
                        }
                    }
            }
        }
        auth.addAuthStateListener(listener)
        onDispose {
            auth.removeAuthStateListener(listener)
            userProfileListener?.remove()
        }
    }

    // Determine the start destination based on a fully ready session.
    val startDest = if (currentUser != null && hasUserProfile == true) "main" else "auth"

    // On cold launch with a remembered auth session, wait for the profile check so we
    // do not accidentally boot the app into the wrong destination.
    if (currentRoute == null && currentUser != null && hasUserProfile == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    // Keep the user on auth until the Firestore profile exists, and return there on sign-out.
    LaunchedEffect(currentUser, hasUserProfile, currentRoute) {
        if (currentRoute == null) return@LaunchedEffect

        when {
            (currentUser == null || hasUserProfile != true) && currentRoute != "auth" -> {
                navController.navigate("auth") {
                    popUpTo(0) { inclusive = true }
                }
            }
            currentUser != null && hasUserProfile == true && currentRoute == "auth" -> {
                navController.navigate("main") {
                    popUpTo("auth") { inclusive = true }
                }
            }
        }
    }

    NavHost(navController = navController, startDestination = startDest) {

        // -------- AUTH SCREEN --------
        composable("auth") {
            val authVm: AuthViewModel = viewModel()
            AuthView(
                authViewModel = authVm,
                onAuthed = {}
            )
        }

        // -------- MAIN (Pager) --------
        composable(
            route = "main?startPage={startPage}&circleId={circleId}",
            arguments = listOf(
                navArgument("startPage") {
                    type = NavType.IntType
                    defaultValue = -1 
                },
                navArgument("circleId") {
                    type = NavType.StringType
                    nullable = true
                }
            )
                ) { backStackEntry ->
            if (currentUser == null || hasUserProfile != true) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                return@composable
            }

            val homeViewModel: HomeViewModel = viewModel()
            val argStartPage = backStackEntry.arguments?.getInt("startPage") ?: -1
            
            // Wait until preference is loaded before rendering the pager
            if (launchOnCameraPref == null) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                // Default to Home (1) unless preference is set to launch on Camera (0)
                val initialPage = if (argStartPage == -1) {
                    if (launchOnCameraPref == true) 0 else 1
                } else {
                    argStartPage
                }
                
                val circleId = backStackEntry.arguments?.getString("circleId")
                val pagerState = rememberPagerState(initialPage = initialPage) { 3 }

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
                                navController.navigate("notifications")
                            },
                            onCameraClick = {
                                coroutineScope.launch { pagerState.animateScrollToPage(0) }
                            },
                            onProfileClick = {
                                coroutineScope.launch { pagerState.animateScrollToPage(2) }
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
                onBlockedAccountsClick = { navController.navigate("blocked_users") },
                onNotificationSettingsClick = { navController.navigate("notification_settings") },
                onAccountDeleted = {
                    navController.navigate("auth") {
                        popUpTo(0) { inclusive = true }
                    }
                }
            )
        }
        
        // -------- Notification Settings --------
        composable("notification_settings") {
            NotificationSettingsView(
                onBack = { navController.popBackStack() }
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

        // -------- Notifications --------
        composable("notifications") {
            NotificationsView(
                onBack = { navController.popBackStack() },
                onNavigateToFriends = { tab ->
                    navController.navigate("friends?tab=$tab")
                },
                onNavigateToCircle = { circleId ->
                    navController.navigate("circle/$circleId")
                }
            )
        }
    }
}



