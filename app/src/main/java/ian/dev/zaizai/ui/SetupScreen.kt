package ian.dev.zaizai.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import ian.dev.zaizai.data.ApiClient
import ian.dev.zaizai.data.ApiException
import ian.dev.zaizai.data.Config
import ian.dev.zaizai.ui.theme.ZaiZai
import kotlinx.coroutines.launch
import java.io.IOException

private const val MAX_NAME = 20

/**
 * 首次设置 / 设置（一般由家长来填）：
 * 第 1 步填服务器地址和家庭口令，连得上才进第 2 步；第 2 步选“我是谁”和播报开关。
 */
@Composable
fun SetupScreen(
    initial: Config,
    canCancel: Boolean,
    onSave: (Config) -> Unit,
    onCancel: () -> Unit,
) {
    var step by rememberSaveable { mutableStateOf(1) }
    var server by rememberSaveable { mutableStateOf(initial.server) }
    var familyKey by rememberSaveable { mutableStateOf(initial.familyKey) }
    var showKey by rememberSaveable { mutableStateOf(false) }
    var recorder by rememberSaveable { mutableStateOf(initial.recorder) }
    var speak by rememberSaveable { mutableStateOf(initial.speak) }
    var caregivers by remember { mutableStateOf(emptyList<String>()) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    fun connect() {
        val url = ApiClient.normalizeServer(server)
        if (url == null) {
            error = "服务器地址不对，例如 zaizai-board.xxx.workers.dev"
            return
        }
        if (familyKey.isBlank()) {
            error = "请填写家庭口令"
            return
        }
        busy = true
        error = null
        scope.launch {
            try {
                val api = ApiClient(url, familyKey.trim(), recorder.ifBlank { "照护人" })
                api.ping()
                caregivers = api.settings().caregivers
                server = url
                step = 2
            } catch (e: IOException) {
                error = "连不上服务器，请检查网络和地址"
            } catch (e: ApiException) {
                error = if (e.status == 401) "家庭口令不对" else e.message
            } finally {
                busy = false
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ZaiZai.colors.background)
            .safeDrawingPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (step == 1) {
            Text("连上家里的看板", style = MaterialTheme.typography.headlineMedium, color = ZaiZai.colors.ink)
            Text("这一步请家长来填。地址和口令在家长网页的部署说明里。", style = MaterialTheme.typography.bodyLarge, color = ZaiZai.colors.muted)
            OutlinedTextField(
                value = server,
                onValueChange = { server = it },
                label = { Text("服务器地址") },
                placeholder = { Text("zaizai-board.xxx.workers.dev") },
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Next),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = familyKey,
                onValueChange = { familyKey = it },
                label = { Text("家庭口令") },
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge,
                visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                trailingIcon = {
                    TextButton(onClick = { showKey = !showKey }) { Text(if (showKey) "隐藏" else "显示") }
                },
                modifier = Modifier.fillMaxWidth(),
            )
            ErrorText(error)
            Button(
                onClick = { connect() },
                enabled = !busy,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp),
            ) {
                Text(if (busy) "正在连接…" else "下一步", style = MaterialTheme.typography.labelLarge)
            }
            if (canCancel) {
                TextButton(onClick = onCancel, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                    Text("取消", style = MaterialTheme.typography.titleMedium)
                }
            }
        } else {
            Text("我是谁", style = MaterialTheme.typography.headlineMedium, color = ZaiZai.colors.ink)
            Text("登记时会显示这个名字，全家都能看到。", style = MaterialTheme.typography.bodyLarge, color = ZaiZai.colors.muted)
            caregivers.forEach { name ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 56.dp)
                        .selectable(selected = recorder == name, role = Role.RadioButton) { recorder = name },
                ) {
                    RadioButton(selected = recorder == name, onClick = null)
                    Spacer(Modifier.padding(start = 12.dp))
                    Text(name, style = MaterialTheme.typography.titleLarge, color = ZaiZai.colors.ink)
                }
            }
            OutlinedTextField(
                value = if (recorder in caregivers) "" else recorder,
                onValueChange = { recorder = it.take(MAX_NAME) },
                label = { Text(if (caregivers.isEmpty()) "名字，例如：奶奶" else "或者填写名字") },
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.fillMaxWidth(),
            )
            if (caregivers.isEmpty()) {
                Text("家长可以在网页“设置”里添加照护人，这里就能直接选。", style = MaterialTheme.typography.bodyMedium, color = ZaiZai.colors.muted)
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 64.dp)
                    .selectable(selected = speak, role = Role.Switch) { speak = !speak },
            ) {
                Column(Modifier.weight(1f)) {
                    Text("打开时语音播报", style = MaterialTheme.typography.titleLarge, color = ZaiZai.colors.ink)
                    Text("念出还没做的事，最多 3 件", style = MaterialTheme.typography.bodyMedium, color = ZaiZai.colors.muted)
                }
                Switch(checked = speak, onCheckedChange = null)
            }
            Button(
                onClick = {
                    onSave(Config(server = server, familyKey = familyKey.trim(), recorder = recorder.trim().take(MAX_NAME), speak = speak))
                },
                enabled = recorder.isNotBlank(),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp),
            ) {
                Text("完成", style = MaterialTheme.typography.labelLarge)
            }
            TextButton(onClick = { step = 1 }, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                Text("上一步", style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

@Composable
private fun ErrorText(error: String?) {
    if (error == null) return
    Text(error, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.error)
}
