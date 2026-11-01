package com.mogeol.atvcon.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mogeol.atvcon.data.TvApp
import com.mogeol.atvcon.ui.theme.AccentCyan
import com.mogeol.atvcon.ui.theme.AccentRed
import com.mogeol.atvcon.ui.theme.DarkSurfaceVariant
import com.mogeol.atvcon.ui.theme.TextSecondary

@Composable
fun AppLauncherSheet(
    apps: List<TvApp>,
    isLoading: Boolean,
    onRefresh: () -> Unit,
    onLaunch: (String) -> Unit,
    onKill: (String) -> Unit,
    onOpenLinkClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var searchQuery by remember { mutableStateOf("") }

    val filteredApps = remember(apps, searchQuery) {
        if (searchQuery.isBlank()) {
            apps
        } else {
            val q = searchQuery.trim().lowercase()
            apps.filter {
                it.displayName.lowercase().contains(q) ||
                it.packageName.lowercase().contains(q) ||
                it.category.lowercase().contains(q)
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Top Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Installed TV Apps (${apps.size})",
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                color = Color.White
            )

            IconButton(onClick = onRefresh, enabled = !isLoading) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = AccentCyan
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Refresh Apps",
                        tint = AccentCyan
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Remote Intent Link Launcher Card
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onOpenLinkClick() },
            shape = RoundedCornerShape(12.dp),
            color = DarkSurfaceVariant
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Link,
                        contentDescription = "Launch Link",
                        tint = AccentCyan,
                        modifier = Modifier.size(20.dp)
                    )
                    Column {
                        Text(
                            text = "Launch Link / Intent",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp,
                            color = Color.White
                        )
                        Text(
                            text = "Open URL remotely via default TV app",
                            fontSize = 11.sp,
                            color = TextSecondary
                        )
                    }
                }
                Icon(
                    imageVector = Icons.Default.Launch,
                    contentDescription = null,
                    tint = AccentCyan,
                    modifier = Modifier.size(16.dp)
                )
            }
        }

        // Search Filter Field if more than 4 apps
        if (apps.size > 4) {
            Spacer(modifier = Modifier.height(10.dp))
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                singleLine = true,
                placeholder = { Text("Filter apps by name...", fontSize = 12.sp, color = TextSecondary) },
                leadingIcon = {
                    Icon(Icons.Default.Search, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(18.dp))
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }, modifier = Modifier.size(20.dp)) {
                            Icon(Icons.Default.Close, contentDescription = "Clear", tint = TextSecondary, modifier = Modifier.size(14.dp))
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = AccentCyan,
                    unfocusedBorderColor = Color.DarkGray,
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                )
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (filteredApps.isEmpty() && !isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (apps.isEmpty()) "No third-party apps found.\nTap refresh to reload." else "No apps match \"$searchQuery\"",
                    color = TextSecondary,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f)
            ) {
                items(filteredApps, key = { it.packageName }) { app ->
                    val (iconVector, badgeBg) = getAppIconAndColor(app.iconType)

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = DarkSurfaceVariant,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onLaunch(app.packageName) }
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Styled App Icon Badge
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(badgeBg),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = iconVector,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(22.dp)
                                )
                            }

                            Spacer(modifier = Modifier.width(8.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = app.displayName,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 12.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    color = Color.White
                                )
                                Text(
                                    text = app.category,
                                    fontSize = 10.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    color = TextSecondary
                                )
                            }

                            IconButton(
                                onClick = { onKill(app.packageName) },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Force Stop",
                                    tint = AccentRed.copy(alpha = 0.6f),
                                    modifier = Modifier.size(15.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun getAppIconAndColor(iconType: String): Pair<ImageVector, Color> {
    return when (iconType) {
        "youtube" -> Icons.Default.SmartDisplay to Color(0xFFD32F2F)
        "vlc" -> Icons.Default.PlayCircle to Color(0xFFE65100)
        "video" -> Icons.Default.Movie to Color(0xFF7E57C2)
        "music" -> Icons.Default.MusicNote to Color(0xFF2E7D32)
        "browser" -> Icons.Default.Language to Color(0xFF1976D2)
        "folder" -> Icons.Default.Folder to Color(0xFFFFA000)
        "terminal" -> Icons.Default.Terminal to Color(0xFF00796B)
        "network" -> Icons.Default.VpnKey to Color(0xFF3F51B5)
        "store" -> Icons.Default.Storefront to Color(0xFF0097A7)
        "download" -> Icons.Default.Download to Color(0xFF388E3C)
        "cast" -> Icons.Default.Cast to Color(0xFF8E24AA)
        "launcher" -> Icons.Default.Apps to Color(0xFF00897B)
        "remote" -> Icons.Default.DesktopWindows to Color(0xFF0288D1)
        "document" -> Icons.Default.Description to Color(0xFFE53935)
        "settings" -> Icons.Default.Settings to Color(0xFF546E7A)
        "finance" -> Icons.Default.TrendingUp to Color(0xFF1565C0)
        "todo" -> Icons.Default.CheckCircle to Color(0xFF43A047)
        "bluetooth" -> Icons.Default.Bluetooth to Color(0xFF1E88E5)
        "image" -> Icons.Default.Image to Color(0xFF00ACC1)
        "shield" -> Icons.Default.Security to Color(0xFF455A64)
        else -> Icons.Default.Tv to Color(0xFF37474F)
    }
}
