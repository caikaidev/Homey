package ian.dev.zaizai.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.graphics.BitmapFactory
import android.view.WindowManager
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.io.File

/**
 * 全屏看图（宝宝屋扫码）：白底大图，屏幕调到最亮、不自动熄屏，方便工作人员扫。
 * 离开这个页面恢复原来的亮度。图片还没下载好时提示联网重试。
 */
@Composable
fun ImageScreen(title: String, file: File?, syncing: Boolean, onRetry: () -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    DisposableEffect(Unit) {
        val window = context.findActivity()?.window
        val before = window?.attributes?.screenBrightness
        window?.let {
            it.attributes = it.attributes.apply { screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_FULL }
            it.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        onDispose {
            window?.let {
                it.attributes = it.attributes.apply {
                    screenBrightness = before ?: WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
                }
                it.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
        }
    }
    val bitmap = remember(file?.path, file?.lastModified()) {
        file?.let { runCatching { BitmapFactory.decodeFile(it.path)?.asImageBitmap() }.getOrNull() }
    }
    Column(
        modifier = Modifier.fillMaxSize().background(Color.White).safeDrawingPadding().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(title, fontSize = 36.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1F2A24))
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            if (bitmap != null) {
                Image(bitmap, contentDescription = "$title 二维码图片", contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
            } else {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text(
                        if (syncing) "正在下载图片…" else "图片还没下载好，请联网后点重试",
                        fontSize = 28.sp,
                        lineHeight = 36.sp,
                        textAlign = TextAlign.Center,
                        color = Color(0xFF1F2A24),
                    )
                    if (!syncing) {
                        Button(onClick = onRetry, modifier = Modifier.fillMaxWidth().heightIn(min = 72.dp)) {
                            Text("重试", fontSize = 26.sp)
                        }
                    }
                }
            }
        }
        Button(
            onClick = onBack,
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1F2A24), contentColor = Color.White),
            modifier = Modifier.fillMaxWidth().heightIn(min = 80.dp),
        ) {
            Text("扫好了，返回", fontSize = 28.sp, fontWeight = FontWeight.Bold)
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
