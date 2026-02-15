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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.circleapp.ui.theme.CircleAppTheme
import com.example.circleapp.ui.viewmodels.HomeViewModel
import com.example.circleapp.ui.views.CameraView
import com.example.circleapp.ui.views.CircleView
import com.example.circleapp.ui.views.HomeView
import com.example.circleapp.ui.views.ProfileView
import kotlinx.coroutines.launch
import com.google.firebase.auth.FirebaseAuth
import com.example.circleapp.ui.views.AuthView
import com.example.circleapp.ui.viewmodels.AuthViewModel



class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            CircleAppTheme {
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

    // Gate the app: if not signed in, start at auth screen
    val isSignedIn = FirebaseAuth.getInstance().currentUser != null
    val startDest = if (isSignedIn) "main" else "auth"

    NavHost(navController = navController, startDestination = startDest) {

        // -------- AUTH SCREEN --------
        composable("auth") {
            val authVm: AuthViewModel = viewModel()
            AuthView(
                authViewModel = authVm,
                onAuthed = {
                    // After login/signup, go to main and remove auth from back stack
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
                    defaultValue = 0 // Startup on CameraView (page 0)
                },
                navArgument("circleId") {
                    type = NavType.StringType
                    nullable = true
                }
            )
        ) { backStackEntry ->

            val homeViewModel: HomeViewModel = viewModel()

            // startPage is used for the initial page when the entry is first created.
            // rememberPagerState uses rememberSaveable, so it will survive back navigation
            // to this same backstack entry without being reset by the initialPage value.
            val startPage = backStackEntry.arguments?.getInt("startPage") ?: 0
            val circleId = backStackEntry.arguments?.getString("circleId")

            val pagerState = rememberPagerState(initialPage = startPage) { 3 }

            // Removed the LaunchedEffect that was forcing the pager to startPage.
            // This allows the pager to restore its previous page (e.g., HomeView)
            // when the user navigates back from a CircleView.

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
                        }
                    )

                    2 -> ProfileView(
                        onLogout = {
                            FirebaseAuth.getInstance().signOut()
                            navController.navigate("auth") {
                                popUpTo("main") { inclusive = true }
                            }
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
                        // Navigate to a new main entry starting on the Camera page
                        navController.navigate("main?startPage=0&circleId=$currentCircleId")
                    }
                )
            }
        }
    }
}
