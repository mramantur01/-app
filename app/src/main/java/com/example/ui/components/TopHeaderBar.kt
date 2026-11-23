package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.AppRole
import com.example.i18n.AppLanguage
import com.example.i18n.KgStrings
import com.example.ui.theme.UberBlue
import com.example.ui.theme.UberGreen
import com.example.ui.theme.UberAmber

@Composable
fun TopHeaderBar(
    strings: KgStrings,
    role: AppRole,
    language: AppLanguage,
    darkTheme: Boolean,
    soundEnabled: Boolean,
    offlineMode: Boolean,
    isSignedIn: Boolean,
    userName: String,
    isSigningIn: Boolean,
    onSelectRole: (AppRole) -> Unit,
    onCycleLanguage: () -> Unit,
    onToggleSound: () -> Unit,
    onToggleTheme: () -> Unit,
    onToggleOffline: () -> Unit,
    onOpenProfile: () -> Unit,
    onClickSignIn: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp,
        shadowElevation = 3.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Top Row: Brand + Quick Toggles + Profile (always visible!) + Sign-In button
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Brand Mark
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.primary),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "KG",
                            color = MaterialTheme.colorScheme.onPrimary,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 14.sp
                        )
                    }
                    Column {
                        Text(
                            text = strings.appTitle,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold,
                            maxLines = 1
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .clickable { onToggleOffline() }
                                .testTag("offline_toggle")
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(7.dp)
                                    .clip(CircleShape)
                                    .background(if (offlineMode) UberAmber else UberGreen)
                            )
                            Text(
                                text = if (offlineMode) strings.offlineMode else strings.onlineMode,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // Right Controls: Language, Sound, Theme, Profile (always accessible!), Sign-In
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // Language Pill
                    Surface(
                        onClick = onCycleLanguage,
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier
                            .heightIn(min = 36.dp)
                            .testTag("language_switcher")
                    ) {
                        Box(
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = language.label,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    // Sound Toggle
                    IconButton(
                        onClick = onToggleSound,
                        modifier = Modifier
                            .size(38.dp)
                            .testTag("sound_toggle")
                    ) {
                        Icon(
                            imageVector = if (soundEnabled) Icons.AutoMirrored.Filled.VolumeUp else Icons.AutoMirrored.Filled.VolumeOff,
                            contentDescription = "Sound notifications",
                            tint = if (soundEnabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    // Dark/Light Theme Toggle
                    IconButton(
                        onClick = onToggleTheme,
                        modifier = Modifier
                            .size(38.dp)
                            .testTag("theme_toggle")
                    ) {
                        Icon(
                            imageVector = if (darkTheme) Icons.Default.LightMode else Icons.Default.DarkMode,
                            contentDescription = "Toggle theme",
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    // Profile Button (accessible IMMEDIATELY even without signing in!)
                    Surface(
                        onClick = onOpenProfile,
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier
                            .size(38.dp)
                            .border(
                                width = 1.5.dp,
                                color = if (isSignedIn) UberGreen else MaterialTheme.colorScheme.outline,
                                shape = CircleShape
                            )
                            .testTag("header_profile_button")
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Person,
                                contentDescription = strings.profile,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    // Google Sign-In button right beside Profile when signed out
                    if (!isSignedIn) {
                        Button(
                            onClick = onClickSignIn,
                            enabled = !isSigningIn,
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = UberBlue,
                                contentColor = Color.White
                            ),
                            modifier = Modifier
                                .height(36.dp)
                                .testTag("header_sign_in_button")
                        ) {
                            if (isSigningIn) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(14.dp),
                                    color = Color.White,
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Text(
                                    text = strings.signIn,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1
                                )
                            }
                        }
                    } else {
                        Surface(
                            onClick = onOpenProfile,
                            shape = RoundedCornerShape(10.dp),
                            color = UberGreen.copy(alpha = 0.14f),
                            modifier = Modifier.height(36.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CloudDone,
                                    contentDescription = "Synced",
                                    tint = UberGreen,
                                    modifier = Modifier.size(15.dp)
                                )
                                Text(
                                    text = userName.substringBefore(" ").take(8),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = UberGreen,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }

            // Second Row: Stark Monochrome Role Switcher (Пассажир / Водитель / Диспетчер)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(3.dp)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                RolePill(
                    label = strings.rolePassenger,
                    icon = Icons.Default.DirectionsCar,
                    selected = role == AppRole.PASSENGER,
                    onClick = { onSelectRole(AppRole.PASSENGER) },
                    tag = "role_passenger",
                    modifier = Modifier.weight(1f, fill = false)
                )
                RolePill(
                    label = strings.roleDriver,
                    icon = Icons.Default.LocalTaxi,
                    selected = role == AppRole.DRIVER,
                    onClick = { onSelectRole(AppRole.DRIVER) },
                    tag = "role_driver",
                    modifier = Modifier.weight(1f, fill = false)
                )
                RolePill(
                    label = strings.roleDispatcher,
                    icon = Icons.Default.Dashboard,
                    selected = role == AppRole.DISPATCHER,
                    onClick = { onSelectRole(AppRole.DISPATCHER) },
                    tag = "role_dispatcher",
                    modifier = Modifier.weight(1f, fill = false)
                )
            }
        }
    }
}

@Composable
private fun RolePill(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
    tag: String,
    modifier: Modifier = Modifier
) {
    val bg = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent
    val fg = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(9.dp))
            .background(bg)
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 7.dp)
            .testTag(tag),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Icon(imageVector = icon, contentDescription = label, tint = fg, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text(
            text = label,
            color = fg,
            style = MaterialTheme.typography.labelLarge,
            maxLines = 1
        )
    }
}
