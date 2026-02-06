package com.example.circleapp.ui.views

import android.app.Application
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

@Composable
fun getApplication(): Application {
    return LocalContext.current.applicationContext as Application
}