package com.example.circleapp

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.circleapp.ui.theme.CircleAppTheme
import com.example.circleapp.ui.viewmodels.HomeViewModel
import com.example.circleapp.ui.views.CircleView
import com.example.circleapp.ui.views.HomeView
import com.google.firebase.auth.FirebaseAuth

class MainActivity : ComponentActivity() {

    private val homeViewModel by viewModels<HomeViewModel>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val auth = FirebaseAuth.getInstance()
        if (auth.currentUser == null) {
            auth.signInAnonymously()
        }

        setContent {
            CircleAppTheme {
                val navController = rememberNavController()
                val context = LocalContext.current

                NavHost(navController = navController, startDestination = "home") {

                    composable("home") {
                        HomeView(
                            homeViewModel = homeViewModel,
                            onCircleClick = { circleId ->
                                navController.navigate("circle/$circleId")
                            },
                            onPhotoSaved = { circleId ->
                                Toast.makeText(context, "Photo uploaded to $circleId", Toast.LENGTH_SHORT).show()
                            },
                            onUploadFailed = { circleId, error ->
                                Toast.makeText(context, "Upload failed for $circleId: $error", Toast.LENGTH_LONG).show()
                            },
                            onJoinCircle = { inviteCode, onSuccess, onNotFound, onError ->
                                homeViewModel.joinCircle(onSuccess, onNotFound, onError)
                            },
                            onCreateCircle = { circleName, durationDays, onSuccess, onError ->
                                homeViewModel.createCircle(onSuccess, onError)
                            }
                        )
                    }

                    composable("circle/{circleId}") { backStackEntry ->
                        val circleId = backStackEntry.arguments?.getString("circleId") ?: ""
                        CircleView(
                            circleId = circleId,
                            onBack = { navController.popBackStack() },
                            onPhotoSaved = {
                                Toast.makeText(context, "Photo saved to gallery", Toast.LENGTH_SHORT).show()
                            }
                        )
                    }
                }
            }
        }
    }
}
