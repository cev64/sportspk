package com.personal.sportspk.ui.screens.about

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.personal.sportspk.R
import com.personal.sportspk.domain.model.League
import com.personal.sportspk.ui.components.SectionHeader

/**
 * About: what this build is, where the data comes from, and what it stores.
 *
 * The data-source caveats are stated plainly here rather than buried in a README, because when
 * scores look wrong the first question is "is the source down or is the app broken?".
 */
@Composable
fun AboutScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val versionLabel = remember(context) {
        runCatching {
            val info = context.packageManager.getPackageInfo(context.packageName, 0)
            @Suppress("DEPRECATION")
            val code = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                info.longVersionCode
            } else {
                info.versionCode.toLong()
            }
            "${info.versionName} (build $code)"
        }.getOrDefault("unknown")
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(PaddingValues(bottom = 24.dp)),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = stringResource(R.string.cd_back),
                )
            }
            Text(text = "About", style = MaterialTheme.typography.headlineSmall)
        }

        Column(modifier = Modifier.padding(horizontal = 20.dp)) {
            Text(
                text = "Sportspk",
                style = MaterialTheme.typography.titleLarge,
            )
            Text(
                text = "Version $versionLabel",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            SectionHeader("Leagues")
            Text(
                text = League.entries.joinToString(" · ") { it.shortName },
                style = MaterialTheme.typography.bodyMedium,
            )

            SectionHeader("Where scores come from")
            Text(
                text = "Scores, schedules, and standings come from ESPN's public JSON endpoints. " +
                    "Those endpoints are free and need no account, but they are unofficial and " +
                    "undocumented: there is no service guarantee, and they can change or go quiet " +
                    "without notice. When that happens the app keeps showing the last scores it " +
                    "saved and tells you they may be behind.",
                style = MaterialTheme.typography.bodyMedium,
            )

            SectionHeader("What this app stores")
            Text(
                text = "Everything stays on this device. Your My Teams list and settings are saved " +
                    "locally and are kept when you install a new build over this one. Scores are " +
                    "cached so screens open instantly and work offline. There is no account, no " +
                    "sync server, and no analytics.",
                style = MaterialTheme.typography.bodyMedium,
            )

            SectionHeader("What it deliberately does not do")
            Text(
                text = "No betting odds, lines, or expected value. No fantasy integration. " +
                    "No play-by-play notification spam - alerts are limited to a favorite team's " +
                    "game starting and its final score.",
                style = MaterialTheme.typography.bodyMedium,
            )

            Spacer(Modifier.height(16.dp))
            HorizontalDivider()
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Built for the Samsung Galaxy Z Fold 8 Ultra.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
