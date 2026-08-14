package com.example.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun VantageBrandingHeader(
    modifier: Modifier = Modifier
) {
    // Elegant breathing animation for the brand logo icon mark
    val infiniteTransition = rememberInfiniteTransition(label = "logo_pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.98f,
        targetValue = 1.02f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "logo_scale"
    )

    val primaryColor = MaterialTheme.colorScheme.primary
    val secondaryColor = MaterialTheme.colorScheme.secondary
    val surfaceColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp)
            .testTag("vantage_branding_header"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // 1. Canvas-drawn Custom Brand Logo Icon (The Vantage Upward Prism / Interlocking Apex)
        Box(
            modifier = Modifier
                .size(72.dp)
                .padding(4.dp),
            contentAlignment = Alignment.Center
        ) {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .aspectRatio(1f)
            ) {
                val w = size.width
                val h = size.height

                // Draw a sleek background diamond/shield
                val shieldPath = Path().apply {
                    moveTo(w / 2f, 0f)
                    lineTo(w, h * 0.35f)
                    lineTo(w * 0.8f, h * 0.95f)
                    lineTo(w * 0.2f, h * 0.95f)
                    lineTo(0f, h * 0.35f)
                    close()
                }
                drawPath(
                    path = shieldPath,
                    color = surfaceColor
                )

                // Interlocking Rising Chevron 1 (Back Wing in Secondary Theme Color)
                val backWing = Path().apply {
                    moveTo(w * 0.3f, h * 0.7f)
                    lineTo(w / 2f, h * 0.45f)
                    lineTo(w * 0.7f, h * 0.7f)
                    lineTo(w / 2f, h * 0.58f)
                    close()
                }
                drawPath(
                    path = backWing,
                    brush = Brush.linearGradient(
                        colors = listOf(secondaryColor.copy(alpha = 0.8f), secondaryColor)
                    )
                )

                // Interlocking Rising Apex 2 (Front Wing in Primary Theme Color)
                val frontWing = Path().apply {
                    moveTo(w * 0.2f, h * 0.55f)
                    lineTo(w / 2f, h * 0.22f)
                    lineTo(w * 0.8f, h * 0.55f)
                    lineTo(w / 2f, h * 0.4f)
                    close()
                }
                drawPath(
                    path = frontWing,
                    brush = Brush.linearGradient(
                        colors = listOf(primaryColor, primaryColor.copy(alpha = 0.7f))
                    )
                )

                // Upward trend / growth vector lines overlay
                val trendLine = Path().apply {
                    moveTo(w * 0.25f, h * 0.85f)
                    lineTo(w * 0.42f, h * 0.68f)
                    lineTo(w * 0.58f, h * 0.76f)
                    lineTo(w * 0.75f, h * 0.52f)
                }
                drawPath(
                    path = trendLine,
                    color = primaryColor,
                    style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // 2. High-fashion Typographic Brand Heading with Premium Spacing
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Text(
                text = "V A N T A G E",
                fontSize = 24.sp,
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.onBackground,
                letterSpacing = 4.sp,
                textAlign = TextAlign.Center
            )
            
            // Premium turquoise accent dot representing precision
            Box(
                modifier = Modifier
                    .size(5.dp)
                    .clip(RoundedCornerShape(100))
                    .background(MaterialTheme.colorScheme.primary)
                    .offset(x = 2.dp, y = (-2).dp)
            )
        }

        Text(
            text = "FINANCE",
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f),
            letterSpacing = 6.sp,
            modifier = Modifier.offset(y = (-2).dp)
        )

        Spacer(modifier = Modifier.height(6.dp))

        // 3. The Brand Tagline / Punchline
        Text(
            text = "Elevate your financial perspective. v1.1",
            fontSize = 13.sp,
            fontStyle = FontStyle.Italic,
            fontWeight = FontWeight.Normal,
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.Center,
            letterSpacing = 0.2.sp
        )

        Spacer(modifier = Modifier.height(4.dp))

        // Subtle, elegant horizontal dividing hairline with dual gradient fade
        Canvas(
            modifier = Modifier
                .fillMaxWidth(0.5f)
                .height(1.5.dp)
                .padding(top = 6.dp)
        ) {
            val primary = primaryColor
            drawRect(
                brush = Brush.horizontalGradient(
                    colors = listOf(
                        primary.copy(alpha = 0f),
                        primary.copy(alpha = 0.3f),
                        primary.copy(alpha = 0f)
                    )
                )
            )
        }
    }
}
