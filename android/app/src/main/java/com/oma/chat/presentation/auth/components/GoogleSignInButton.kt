package com.oma.chat.presentation.auth.components

import android.app.Activity
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException

private const val WEB_CLIENT_ID = "836902266336-on367et8g14g1qva151sv5oafsimjl8n.apps.googleusercontent.com"

@Composable
fun GoogleSignInSection(
    isLoading: Boolean,
    onGoogleTokenReceived: (String) -> Unit,
    onError: (String) -> Unit,
    buttonText: String = "Continue with Google",
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    val googleSignInClient = remember {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(WEB_CLIENT_ID)
            .requestEmail()
            .requestProfile()
            .build()
        GoogleSignIn.getClient(context, gso)
    }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
            try {
                val account = task.getResult(ApiException::class.java)
                val idToken = account?.idToken
                if (!idToken.isNullOrBlank()) {
                    onGoogleTokenReceived(idToken)
                } else {
                    onError("Google Sign-In did not return a valid ID Token.")
                }
            } catch (e: ApiException) {
                Log.e("GoogleSignIn", "ApiException: ${e.statusCode} - ${e.localizedMessage}", e)
                val msg = when (e.statusCode) {
                    7 -> "Network error. Please check your internet connection."
                    12500 -> "Google Play Services error. Please update Google Play Services."
                    12501 -> "Google Sign-In canceled."
                    else -> "Google Sign-In failed (${e.statusCode}): ${e.localizedMessage ?: "Unknown error"}"
                }
                if (e.statusCode != 12501) {
                    onError(msg)
                }
            } catch (e: Exception) {
                Log.e("GoogleSignIn", "Unexpected error: ${e.localizedMessage}", e)
                onError("Google Sign-In error: ${e.localizedMessage ?: "Unknown error"}")
            }
        } else if (result.resultCode != Activity.RESULT_CANCELED) {
            onError("Google Sign-In was canceled or encountered an issue.")
        }
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        HorizontalDivider(
            modifier = Modifier.weight(1f),
            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
        )
        Text(
            text = "OR",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            modifier = Modifier.padding(horizontal = 14.dp),
            fontWeight = FontWeight.Medium
        )
        HorizontalDivider(
            modifier = Modifier.weight(1f),
            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
        )
    }

    OutlinedButton(
        onClick = {
            if (!isLoading) {
                // Clear any prior cached account so user can pick an account
                googleSignInClient.signOut().addOnCompleteListener {
                    val intent = googleSignInClient.signInIntent
                    launcher.launch(intent)
                }
            }
        },
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp),
        enabled = !isLoading,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)),
        colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface
        )
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(22.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.primary
            )
        } else {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                GoogleLogoIcon(modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = buttonText,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold
                    ),
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

/**
 * Authentic 4-color Google "G" Logo Canvas
 */
@Composable
fun GoogleLogoIcon(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val width = size.width
        val height = size.height
        val cx = width / 2f
        val cy = height / 2f
        val radius = width / 2f
        val innerRadius = radius * 0.58f

        // Google Brand Colors
        val googleRed = Color(0xFFEA4335)
        val googleYellow = Color(0xFFFBBC05)
        val googleGreen = Color(0xFF34A853)
        val googleBlue = Color(0xFF4285F4)

        // Draw standard clean multi-colored Google G segments
        // Red Top Arc
        drawArc(
            color = googleRed,
            startAngle = 195f,
            sweepAngle = 135f,
            useCenter = true,
            size = Size(width, height)
        )

        // Yellow Left Arc
        drawArc(
            color = googleYellow,
            startAngle = 135f,
            sweepAngle = 60f,
            useCenter = true,
            size = Size(width, height)
        )

        // Green Bottom Arc
        drawArc(
            color = googleGreen,
            startAngle = 45f,
            sweepAngle = 90f,
            useCenter = true,
            size = Size(width, height)
        )

        // Blue Right Arc & Center Bar
        drawArc(
            color = googleBlue,
            startAngle = -45f,
            sweepAngle = 90f,
            useCenter = true,
            size = Size(width, height)
        )

        // Center White/Background cutout
        drawCircle(
            color = Color.White,
            radius = innerRadius,
            center = Offset(cx, cy)
        )

        // Blue horizontal crossbar
        val barHeight = radius * 0.38f
        val barWidth = radius * 0.95f
        drawRect(
            color = googleBlue,
            topLeft = Offset(cx - radius * 0.1f, cy - barHeight / 2f),
            size = Size(barWidth, barHeight)
        )

        // Upper right inner cutout
        val cutoutPath = Path().apply {
            moveTo(cx, cy)
            lineTo(width, cy)
            lineTo(width, 0f)
            lineTo(cx, 0f)
            close()
        }
        drawPath(
            path = cutoutPath,
            color = Color.White,
            style = Fill
        )

        // Redraw Top Red Arc to seal top edge neatly
        drawArc(
            color = googleRed,
            startAngle = 205f,
            sweepAngle = 110f,
            useCenter = false,
            topLeft = Offset(cx - radius + (radius - innerRadius) / 2f, cy - radius + (radius - innerRadius) / 2f),
            size = Size(radius * 2 - (radius - innerRadius), radius * 2 - (radius - innerRadius)),
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = radius - innerRadius)
        )
    }
}
