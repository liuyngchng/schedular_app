package com.example.applauncher.ui.screens

import android.content.Intent
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.applauncher.AppLauncherApp
import com.example.applauncher.BridgeActivity
import com.example.applauncher.model.AppInfo
import com.example.applauncher.model.Diagnostics
import com.example.applauncher.model.ExecutionLog
import com.example.applauncher.model.Schedule
import com.example.applauncher.model.TimeSlot
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    currentSchedule: Schedule?,
    diagnostics: Diagnostics?,
    onSave: (Schedule) -> Unit,
    onToggleEnabled: (Boolean) -> Unit
) {
    var selectedApp by remember { mutableStateOf<AppInfo?>(null) }
    var showAppPicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }
    var selectedSlot by remember { mutableStateOf(0) }

    var s1StartH by remember { mutableStateOf(currentSchedule?.slot1?.startHour ?: 9) }
    var s1StartM by remember { mutableStateOf(currentSchedule?.slot1?.startMinute ?: 0) }

    var s2StartH by remember { mutableStateOf(currentSchedule?.slot2?.startHour ?: 14) }
    var s2StartM by remember { mutableStateOf(currentSchedule?.slot2?.startMinute ?: 0) }

    var enabled by remember { mutableStateOf(currentSchedule?.enabled ?: false) }

    LaunchedEffect(currentSchedule) {
        if (currentSchedule != null && selectedApp == null) {
            selectedApp = AppInfo(currentSchedule.packageName, currentSchedule.appName)
            s1StartH = currentSchedule.slot1.startHour
            s1StartM = currentSchedule.slot1.startMinute
            s2StartH = currentSchedule.slot2.startHour
            s2StartM = currentSchedule.slot2.startMinute
            enabled = currentSchedule.enabled
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("定时启动器") })
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            val context = LocalContext.current

            // Self-check diagnostics
            var showDiagnostics by remember { mutableStateOf(false) }
            if (diagnostics != null) {
                val problemCount = listOf(
                    !diagnostics.exactAlarmGranted,
                    !diagnostics.batteryWhitelisted,
                    diagnostics.hasLockScreen,
                    !diagnostics.targetAppInstalled,
                    diagnostics.scheduleEnabled && !diagnostics.alarmsRegistered,
                    diagnostics.missedSchedules.isNotEmpty()
                ).count { it }
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (problemCount > 0)
                            MaterialTheme.colorScheme.errorContainer
                        else
                            MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = if (problemCount > 0) "⚠ 自检 — $problemCount 项异常"
                                       else "✓ 自检 — 一切正常",
                                style = MaterialTheme.typography.titleSmall
                            )
                            TextButton(onClick = { showDiagnostics = !showDiagnostics }) {
                                Text(if (showDiagnostics) "收起" else "展开")
                            }
                        }
                        if (showDiagnostics) {
                            Spacer(modifier = Modifier.height(8.dp))
                            DiagnosticRow(
                                ok = diagnostics.exactAlarmGranted,
                                okText = "精确闹钟权限：已授予",
                                failText = "精确闹钟权限：未授予 → 点击展开后去系统设置开启"
                            )
                            DiagnosticRow(
                                ok = diagnostics.batteryWhitelisted,
                                okText = "电池优化白名单：已加入",
                                failText = "电池优化白名单：未加入 → 建议设为不优化"
                            )
                            DiagnosticRow(
                                ok = !diagnostics.hasLockScreen,
                                okText = "锁屏状态：无锁屏密码",
                                failText = "锁屏状态：有锁屏密码 → 目标App会启动在锁屏后面"
                            )
                            DiagnosticRow(
                                ok = diagnostics.targetAppInstalled,
                                okText = "目标应用：${diagnostics.targetAppName} 已安装",
                                failText = "目标应用：${diagnostics.targetAppName} 可能已卸载"
                            )
                            if (diagnostics.scheduleEnabled) {
                                DiagnosticRow(
                                    ok = diagnostics.alarmsRegistered,
                                    okText = "闹钟注册：已注册",
                                    failText = "闹钟注册：未找到已注册的闹钟 → 重新保存试试"
                                )
                            } else {
                                DiagnosticRow(
                                    ok = false,
                                    okText = "",
                                    failText = "定时未启用 → 请打开「启用定时」开关"
                                )
                            }
                            if (!diagnostics.hasSchedule) {
                                DiagnosticRow(
                                    ok = false,
                                    okText = "",
                                    failText = "尚未设置定时任务 → 请选择应用和时间后保存"
                                )
                            }
                            diagnostics.missedSchedules.forEach { missed ->
                                DiagnosticRow(
                                    ok = false,
                                    okText = "",
                                    failText = "错过执行：$missed"
                                )
                            }
                        }
                    }
                }
            }

            // App selection
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showAppPicker = true },
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Apps, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(40.dp))
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("选择应用", style = MaterialTheme.typography.titleMedium)
                        Text(
                            text = selectedApp?.appName ?: "未选择",
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (selectedApp != null) MaterialTheme.colorScheme.onSurface
                                    else MaterialTheme.colorScheme.error
                        )
                    }
                }
            }

            // Time slot
            Card(elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("时段一", "时段二").forEachIndexed { index, label ->
                                FilterChip(
                                    selected = selectedSlot == index,
                                    onClick = { selectedSlot = index },
                                    label = { Text(label) }
                                )
                            }
                        }
                        Spacer(modifier = Modifier.weight(1f))
                        TextButton(
                            onClick = {
                                selectedApp?.let { app ->
                                    val intent = Intent(context, BridgeActivity::class.java).apply {
                                        putExtra(BridgeActivity.EXTRA_PACKAGE, app.packageName)
                                        putExtra(BridgeActivity.EXTRA_APP_NAME, app.appName)
                                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    }
                                    context.startActivity(intent)
                                }
                            },
                            enabled = selectedApp != null
                        ) {
                            Text("测试")
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    val (h, m) = if (selectedSlot == 0) s1StartH to s1StartM else s2StartH to s2StartM
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showTimePicker = true }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("启动时间", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                        Icon(Icons.Default.AccessTime, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            String.format("%02d:%02d", h, m),
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }
            }

            // Enable switch & execution log
            var showLog by remember { mutableStateOf(false) }
            val app = LocalContext.current.applicationContext as AppLauncherApp
            val logs by app.logRepository.logs.collectAsState()
            Card(elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("启用定时", style = MaterialTheme.typography.titleMedium)
                        Spacer(modifier = Modifier.width(16.dp))
                        Switch(checked = enabled, onCheckedChange = { enabled = it; onToggleEnabled(it) })
                        Spacer(modifier = Modifier.weight(1f))
                        TextButton(onClick = { showLog = !showLog }) {
                            Text("执行日志")
                        }
                    }
                    if (showLog) {
                        Spacer(modifier = Modifier.height(8.dp))
                        if (logs.isEmpty()) {
                            Text(
                                "暂无执行记录",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            val df = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault())
                            logs.forEach { log ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 2.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        log.appName,
                                        style = MaterialTheme.typography.bodySmall,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Text(
                                        df.format(Date(log.timestamp)),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Save button
            Button(
                onClick = {
                    val app = selectedApp ?: return@Button
                    onSave(
                        Schedule(
                            packageName = app.packageName,
                            appName = app.appName,
                            slot1 = TimeSlot(s1StartH, s1StartM),
                            slot2 = TimeSlot(s2StartH, s2StartM),
                            daysOfWeek = (Calendar.SUNDAY..Calendar.SATURDAY).toSet(),
                            enabled = enabled
                        )
                    )
                },
                modifier = Modifier.fillMaxWidth().height(50.dp),
                enabled = selectedApp != null
            ) {
                Text("保存")
            }

            if (selectedApp == null) {
                Text(
                    "请选择应用",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )
            }

        }
    }

    // App picker dialog
    if (showAppPicker) {
        AppPickerDialog(
            onDismiss = { showAppPicker = false },
            onAppSelected = { selectedApp = it; showAppPicker = false }
        )
    }

    // Time picker dialog
    if (showTimePicker) {
        val title = if (selectedSlot == 0) "时段一 启动时间" else "时段二 启动时间"
        val initH = if (selectedSlot == 0) s1StartH else s2StartH
        val initM = if (selectedSlot == 0) s1StartM else s2StartM
        TimePickerDialog(
            title = title,
            initialHour = initH,
            initialMinute = initM,
            onConfirm = { h, m ->
                if (selectedSlot == 0) { s1StartH = h; s1StartM = m }
                else { s2StartH = h; s2StartM = m }
                showTimePicker = false
            },
            onDismiss = { showTimePicker = false }
        )
    }
}


@Composable
private fun DiagnosticRow(ok: Boolean, okText: String, failText: String) {
    val text = if (ok) okText else failText
    val color = if (ok) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = if (ok) "✓" else "✗",
            color = color,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.width(20.dp)
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = color,
            modifier = Modifier.weight(1f)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimePickerDialog(
    title: String,
    initialHour: Int,
    initialMinute: Int,
    onConfirm: (Int, Int) -> Unit,
    onDismiss: () -> Unit
) {
    val state = rememberTimePickerState(initialHour = initialHour, initialMinute = initialMinute, is24Hour = true)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { TimePicker(state = state) },
        confirmButton = {
            TextButton(onClick = { onConfirm(state.hour, state.minute) }) { Text("确定") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

@Composable
private fun AppPickerDialog(onDismiss: () -> Unit, onAppSelected: (AppInfo) -> Unit) {
    val context = LocalContext.current
    val pm = context.packageManager
    var apps by remember { mutableStateOf<List<AppInfo>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        val intent = Intent(Intent.ACTION_MAIN).apply { addCategory(Intent.CATEGORY_LAUNCHER) }
        apps = pm.queryIntentActivities(intent, 0)
            .map { AppInfo(it.activityInfo.packageName, it.loadLabel(pm).toString()) }
            .filter { it.packageName != context.packageName }
            .sortedBy { it.appName }
        loading = false
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("选择应用") },
        text = {
            if (loading) {
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (apps.isEmpty()) {
                Text("未找到已安装的应用")
            } else {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    apps.forEach { app ->
                        Text(
                            app.appName,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.fillMaxWidth().clickable { onAppSelected(app) }
                                .padding(vertical = 12.dp, horizontal = 4.dp)
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}
