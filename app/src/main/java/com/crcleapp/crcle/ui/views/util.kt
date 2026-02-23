package com.crcleapp.crcle.ui.views

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.crcleapp.crcle.ui.theme.LeagueSpartan

@Composable
fun getApplication(): Application {
    return LocalContext.current.applicationContext as Application
}

@Composable
fun CircleLogo(
    modifier: Modifier = Modifier,
    text: String = "circle",
    fontSize: TextUnit = 24.sp,
    textColor: Color = MaterialTheme.colorScheme.onSurface,
    circleColor: Color = Color.Transparent,
    borderColor: Color = MaterialTheme.colorScheme.onSurface,
    borderWidth: Dp = 2.dp
) {
    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(circleColor)
            .border(borderWidth, borderColor, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = textColor,
            fontSize = fontSize,
            fontFamily = LeagueSpartan,
            fontWeight = FontWeight.Bold
        )
    }
}