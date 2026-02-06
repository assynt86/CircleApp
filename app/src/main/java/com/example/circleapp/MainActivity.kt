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
import kotlinx.coroutines.launch

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
    val homeViewModel: HomeViewModel = viewModel()
    val coroutineScope = rememberCoroutineScope()

    NavHost(navController = navController, startDestination = "main") {
        composable(
            route = "main?startPage={startPage}&circleId={circleId}",
            arguments = listOf(
                navArgument("startPage") {
                    type = NavType.IntType
                    defaultValue = 1
                },
                navArgument("circleId") {
                    type = NavType.StringType
                    nullable = true
                }
            )
        ) { backStackEntry ->
            val startPage = backStackEntry.arguments?.getInt("startPage") ?: 1
            val circleId = backStackEntry.arguments?.getString("circleId")

            val pagerState = rememberPagerState(initialPage = startPage) { 2 }

            LaunchedEffect(startPage) {
                if (pagerState.currentPage != startPage) {
                    pagerState.animateScrollToPage(startPage)
                }
            }

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
                        onUploadFailed = { _, _ ->
                            if (circleId != null) {
                                navController.popBackStack()
                            } else {
                                coroutineScope.launch { pagerState.animateScrollToPage(1) }
                            }
                        }
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
                }
            }
        }
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
                    }
                )
            }
        }
    }
}
