package com.example.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.OpenInBrowser
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.ui.theme.LocalTorrentClawColors
import com.example.util.TorrentIntentHelper

@Composable
fun AuthScreen(
  isBusy: Boolean,
  errorMessage: String?,
  onSignInWithApiKey: (apiKey: String, username: String?, email: String?) -> Unit,
  onContinueWithoutAccount: () -> Unit,
  onClearError: () -> Unit,
) {
  val context = LocalContext.current
  val semantic = LocalTorrentClawColors.current

  var apiKey by remember { mutableStateOf("") }
  var username by remember { mutableStateOf("") }
  var showKey by remember { mutableStateOf(false) }

  Box(
    modifier =
      Modifier.fillMaxSize()
        .background(MaterialTheme.colorScheme.background)
        .statusBarsPadding()
        .navigationBarsPadding(),
  ) {
    Column(
      modifier =
        Modifier.fillMaxSize()
          .verticalScroll(rememberScrollState())
          .padding(horizontal = 20.dp, vertical = 16.dp)
          .widthIn(max = 560.dp)
          .align(Alignment.TopCenter),
      horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      // Hero artwork card
      Box(
        modifier =
          Modifier.fillMaxWidth()
            .height(172.dp)
            .clip(RoundedCornerShape(20.dp)),
      ) {
        Image(
          painter = painterResource(id = R.drawable.img_discovery_hero_1795456050067),
          contentDescription = "TorrentClaw cinema discovery banner",
          contentScale = ContentScale.Crop,
          modifier = Modifier.fillMaxSize(),
        )
        Box(
          modifier =
            Modifier.fillMaxSize()
              .background(
                Brush.verticalGradient(
                  listOf(Color.Black.copy(alpha = 0.25f), Color.Black.copy(alpha = 0.85f))
                )
              ),
        )
        Row(
          modifier = Modifier.align(Alignment.BottomStart).padding(16.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Image(
            painter = painterResource(id = R.drawable.torrentclaw_icon_fg_1795456049965),
            contentDescription = "TorrentClaw logo",
            modifier = Modifier.size(54.dp).clip(RoundedCornerShape(14.dp)),
          )
          Spacer(Modifier.width(14.dp))
          Column {
            Text(
              text = "TorrentClaw",
              style = MaterialTheme.typography.headlineMedium,
              color = Color.White,
            )
            Text(
              text = "30+ Aggregated Sources • TrueSpec Verified",
              style = MaterialTheme.typography.bodySmall,
              color = Color.White.copy(alpha = 0.82f),
            )
          }
        }
      }

      Spacer(Modifier.height(20.dp))

      Text(
        text = "Connect Your TorrentClaw Account",
        style = MaterialTheme.typography.titleLarge,
        color = MaterialTheme.colorScheme.onBackground,
        modifier = Modifier.fillMaxWidth(),
      )
      Spacer(Modifier.height(4.dp))
      Text(
        text =
          "Sign in with your official TorrentClaw API key for higher rate limits (120–1,000 req/min), TrueSpec priority, and Pro features. Credentials are encrypted via Android Keystore.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.fillMaxWidth(),
      )

      Spacer(Modifier.height(16.dp))

      OutlinedTextField(
        value = username,
        onValueChange = { username = it },
        label = { Text("Account Name (optional)") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth().testTag("auth_username_input"),
      )

      Spacer(Modifier.height(10.dp))

      OutlinedTextField(
        value = apiKey,
        onValueChange = {
          apiKey = it
          if (errorMessage != null) onClearError()
        },
        label = { Text("TorrentClaw API Key") },
        placeholder = { Text("Paste key from torrentclaw.com/dashboard") },
        leadingIcon = { Icon(Icons.Rounded.Key, contentDescription = null) },
        trailingIcon = {
          IconButton(onClick = { showKey = !showKey }) {
            Icon(
              if (showKey) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
              contentDescription = if (showKey) "Hide key" else "Show key",
            )
          }
        },
        visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
        singleLine = true,
        isError = errorMessage != null,
        modifier = Modifier.fillMaxWidth().testTag("auth_apikey_input"),
      )

      if (errorMessage != null) {
        Spacer(Modifier.height(8.dp))
        Surface(
          color = MaterialTheme.colorScheme.error.copy(alpha = 0.12f),
          shape = RoundedCornerShape(10.dp),
          modifier = Modifier.fillMaxWidth(),
        ) {
          Text(
            text = errorMessage,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.padding(12.dp),
          )
        }
      }

      Spacer(Modifier.height(14.dp))

      Button(
        onClick = { onSignInWithApiKey(apiKey, username.ifBlank { null }, null) },
        enabled = !isBusy && apiKey.trim().length >= 8,
        modifier = Modifier.fillMaxWidth().height(50.dp).testTag("auth_signin_button"),
      ) {
        if (isBusy) {
          CircularProgressIndicator(
            modifier = Modifier.size(20.dp),
            strokeWidth = 2.dp,
            color = MaterialTheme.colorScheme.onPrimary,
          )
          Spacer(Modifier.width(10.dp))
          Text("Verifying with TorrentClaw…")
        } else {
          Icon(Icons.Rounded.VerifiedUser, contentDescription = null, modifier = Modifier.size(18.dp))
          Spacer(Modifier.width(8.dp))
          Text("Verify & Sign In")
        }
      }

      Spacer(Modifier.height(10.dp))

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        OutlinedButton(
          onClick = { TorrentIntentHelper.openBrowserUrl(context, "https://torrentclaw.com/login") },
          modifier = Modifier.weight(1f).height(48.dp).testTag("auth_web_login_button"),
        ) {
          Icon(Icons.Rounded.OpenInBrowser, contentDescription = null, modifier = Modifier.size(17.dp))
          Spacer(Modifier.width(6.dp))
          Text("Web Login", maxLines = 1)
        }
        OutlinedButton(
          onClick = { TorrentIntentHelper.openBrowserUrl(context, "https://torrentclaw.com/register") },
          modifier = Modifier.weight(1f).height(48.dp).testTag("auth_register_button"),
        ) {
          Icon(Icons.Rounded.PersonAdd, contentDescription = null, modifier = Modifier.size(17.dp))
          Spacer(Modifier.width(6.dp))
          Text("Register", maxLines = 1)
        }
      }

      Spacer(Modifier.height(16.dp))
      HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
      Spacer(Modifier.height(12.dp))

      // Tier summary
      Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth(),
      ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Shield, contentDescription = null, tint = semantic.verified, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Service Tiers", style = MaterialTheme.typography.titleSmall)
          }
          TierRow("Anonymous", "30 req/min • Public search & magnets")
          TierRow("Free Account", "120 req/min • 1,000/day • Watchlist & alerts")
          TierRow("TorrentClaw Pro", "1,000 req/min • Priority TrueSpec & Torznab")
        }
      }

      Spacer(Modifier.height(12.dp))

      TextButton(
        onClick = onContinueWithoutAccount,
        modifier = Modifier.fillMaxWidth().height(48.dp).testTag("auth_guest_button"),
      ) {
        Text("Continue without account (Anonymous)", fontWeight = FontWeight.SemiBold)
      }
    }
  }
}

@Composable
private fun TierRow(name: String, detail: String) {
  Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
    Text(name, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
    Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
  }
}
