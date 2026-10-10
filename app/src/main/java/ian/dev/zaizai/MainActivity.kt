package ian.dev.zaizai

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import ian.dev.zaizai.ui.HomeScreen
import ian.dev.zaizai.ui.theme.ZaiZaiTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ZaiZaiTheme {
                HomeScreen()
            }
        }
    }
}
