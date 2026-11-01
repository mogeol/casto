package com.mogeol.atvcon.ui.components

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.mogeol.atvcon.R
import com.mogeol.atvcon.data.AppConstants
import com.mogeol.atvcon.ui.theme.*

@Composable
fun AboutDialog(
    onDismissRequest: () -> Unit
) {
    val context = LocalContext.current
    var showQrPreview by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismissRequest) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = Color(0xFF1A1D24),
            border = BorderStroke(1.dp, Color.DarkGray.copy(alpha = 0.5f)),
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header with App Icon and Close button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFFFF5252)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.Tv,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(24.dp)
                            )
                        }

                        Column {
                            Text(
                                text = AppConstants.APP_NAME,
                                color = Color.White,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "v${AppConstants.APP_VERSION}",
                                color = AccentCyan,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

                    IconButton(
                        onClick = onDismissRequest,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Close",
                            tint = TextSecondary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = "High-performance ADB remote & low-latency scrcpy screen mirror for Android TV and mobile devices.",
                    color = TextSecondary,
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                    textAlign = TextAlign.Start,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(16.dp))

                // License & Open Source Card
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFF222630),
                    border = BorderStroke(1.dp, Color.DarkGray.copy(alpha = 0.35f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(Icons.Default.Code, contentDescription = null, tint = AccentCyan, modifier = Modifier.size(16.dp))
                            Text("Open Source (Apache 2.0)", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        Text(
                            text = "Powered by pure Kotlin ADB (dadb) and scrcpy-server. Fully open-source with zero trackers and zero ads.",
                            color = TextSecondary,
                            fontSize = 11.sp,
                            lineHeight = 15.sp
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        OutlinedButton(
                            onClick = {
                                runCatching {
                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(AppConstants.GITHUB_REPO_URL))
                                    context.startActivity(intent)
                                }.onFailure {
                                    Toast.makeText(context, "Could not open browser", Toast.LENGTH_SHORT).show()
                                }
                            },
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, AccentCyan.copy(alpha = 0.6f)),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = AccentCyan),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            modifier = Modifier.fillMaxWidth().height(34.dp)
                        ) {
                            Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("View on GitHub", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Donation & Support Section
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFF252127),
                    border = BorderStroke(1.dp, Color(0xFFFF5252).copy(alpha = 0.35f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(Icons.Default.Favorite, contentDescription = null, tint = Color(0xFFFF5252), modifier = Modifier.size(16.dp))
                            Text("Support & Donations", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        Text(
                            text = "Casto is developed as a passionate open-source project. If it makes your TV experience smoother, voluntary donations keep active development alive!",
                            color = TextSecondary,
                            fontSize = 11.sp,
                            lineHeight = 15.sp
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        // Alipay Direct Jump Button
                        Button(
                            onClick = {
                                runCatching {
                                    // Try launching directly into Alipay app first
                                    val alipayIntent = Intent(Intent.ACTION_VIEW, Uri.parse(AppConstants.ALIPAY_SCHEME_URL)).apply {
                                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    }
                                    context.startActivity(alipayIntent)
                                }.onFailure {
                                    // Fallback to web link
                                    runCatching {
                                        val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse(AppConstants.ALIPAY_QR_URL))
                                        context.startActivity(webIntent)
                                    }.onFailure {
                                        showQrPreview = true
                                    }
                                }
                            },
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF1677FF),
                                contentColor = Color.White
                            ),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            modifier = Modifier.fillMaxWidth().height(36.dp)
                        ) {
                            Icon(Icons.Default.AccountBalanceWallet, contentDescription = null, modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("支付宝支持 / Alipay", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // View QR code modal button
                            OutlinedButton(
                                onClick = { showQrPreview = true },
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(1.dp, Color(0xFF1677FF).copy(alpha = 0.5f)),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF1677FF)),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                modifier = Modifier.weight(1f).height(34.dp)
                            ) {
                                Icon(Icons.Default.QrCode2, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("查看收款码", fontSize = 11.sp, fontWeight = FontWeight.Medium)
                            }

                            // PayPal Button
                            OutlinedButton(
                                onClick = {
                                    runCatching {
                                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(AppConstants.PAYPAL_URL))
                                        context.startActivity(intent)
                                    }.onFailure {
                                        Toast.makeText(context, "Could not open browser", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(1.dp, Color(0xFF0079C1).copy(alpha = 0.5f)),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF0079C1)),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                modifier = Modifier.weight(1f).height(34.dp)
                            ) {
                                Icon(Icons.Default.Payment, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("PayPal", fontSize = 11.sp, fontWeight = FontWeight.Medium)
                            }
                        }
                    }
                }
            }
        }
    }

    // Modal QR Code Preview Dialog
    if (showQrPreview) {
        Dialog(onDismissRequest = { showQrPreview = false }) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Color.White,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "支付宝赞助",
                            color = Color(0xFF1677FF),
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                        IconButton(
                            onClick = { showQrPreview = false },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.Gray, modifier = Modifier.size(18.dp))
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Image(
                        painter = painterResource(id = R.drawable.alipay_qr),
                        contentDescription = "Alipay QR Code",
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(360f / 440f)
                            .clip(RoundedCornerShape(8.dp))
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Button(
                        onClick = {
                            runCatching {
                                val alipayIntent = Intent(Intent.ACTION_VIEW, Uri.parse(AppConstants.ALIPAY_SCHEME_URL)).apply {
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                }
                                context.startActivity(alipayIntent)
                            }.onFailure {
                                Toast.makeText(context, "Could not open Alipay directly", Toast.LENGTH_SHORT).show()
                            }
                        },
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF1677FF),
                            contentColor = Color.White
                        ),
                        modifier = Modifier.fillMaxWidth().height(36.dp)
                    ) {
                        Text("跳转至支付宝 App", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
