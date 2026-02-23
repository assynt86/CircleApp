package com.crcleapp.crcle.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.crcleapp.crcle.R

// Define the League Spartan FontFamily
// Make sure you have the font files in res/font/league_spartan.ttf (and others if applicable)
val LeagueSpartan = FontFamily(
    Font(R.font.league_spartan_regular, FontWeight.Normal),
    Font(R.font.league_spartan_bold, FontWeight.Bold),
    Font(R.font.league_spartan_medium, FontWeight.Medium),
    Font(R.font.league_spartan_semibold, FontWeight.SemiBold),
    Font(R.font.league_spartan_light, FontWeight.Light),
    Font(R.font.league_spartan_extrabold, FontWeight.ExtraBold),
    Font(R.font.league_spartan_thin, FontWeight.Thin)
)

// Default Typography to derive styles from
private val defaultTypography = Typography()

// Set of Material typography styles to start with
val Typography = Typography(
    displayLarge = defaultTypography.displayLarge.copy(fontFamily = LeagueSpartan),
    displayMedium = defaultTypography.displayMedium.copy(fontFamily = LeagueSpartan),
    displaySmall = defaultTypography.displaySmall.copy(fontFamily = LeagueSpartan),

    headlineLarge = defaultTypography.headlineLarge.copy(fontFamily = LeagueSpartan),
    headlineMedium = defaultTypography.headlineMedium.copy(fontFamily = LeagueSpartan),
    headlineSmall = defaultTypography.headlineSmall.copy(fontFamily = LeagueSpartan),

    titleLarge = defaultTypography.titleLarge.copy(fontFamily = LeagueSpartan),
    titleMedium = defaultTypography.titleMedium.copy(fontFamily = LeagueSpartan),
    titleSmall = defaultTypography.titleSmall.copy(fontFamily = LeagueSpartan),

    bodyLarge = defaultTypography.bodyLarge.copy(fontFamily = LeagueSpartan),
    bodyMedium = defaultTypography.bodyMedium.copy(fontFamily = LeagueSpartan),
    bodySmall = defaultTypography.bodySmall.copy(fontFamily = LeagueSpartan),

    labelLarge = defaultTypography.labelLarge.copy(fontFamily = LeagueSpartan),
    labelMedium = defaultTypography.labelMedium.copy(fontFamily = LeagueSpartan),
    labelSmall = defaultTypography.labelSmall.copy(fontFamily = LeagueSpartan)
)
