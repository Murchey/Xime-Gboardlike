package com.kingzcheung.xime.ui.emoji

import android.content.Context
import android.os.Bundle
import android.os.ResultReceiver
import android.view.Gravity
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.kingzcheung.xime.settings.AppLocale
import com.kingzcheung.xime.ui.theme.XimeTheme

class EmojiPickerActivity : ComponentActivity() {
    private var sent = false
    private val receiver: ResultReceiver? by lazy {
        @Suppress("DEPRECATION")
        intent.getParcelableExtra("receiver")
    }
    override fun attachBaseContext(newBase: Context) { super.attachBaseContext(AppLocale.wrap(newBase)) }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sent = savedInstanceState?.getBoolean("result_sent") ?: false
        enableEdgeToEdge()
        window.setGravity(Gravity.BOTTOM)
        window.setBackgroundDrawableResource(android.R.color.transparent)
        window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        window.setDimAmount(0.12f)
        window.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT)
        if (receiver == null) { finish(); return }
        setContent {
            XimeTheme(darkTheme = intent.getBooleanExtra("dark", false)) {
                BoxWithConstraints(Modifier.fillMaxSize().systemBarsPadding()) {
                    val sheetHeight = maxHeight * 0.62f
                    Box(Modifier.fillMaxSize().clickable(
                        interactionSource = remember { MutableInteractionSource() }, indication = null,
                        onClick = { finish() },
                    ))
                    Surface(
                        modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                            .height(sheetHeight).imePadding(),
                        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                    ) {
                        EmojiPickerScreen(
                            onSelect = { complete(Bundle().apply { putString("text", it) }) },
                            onImageSelect = { complete(Bundle().apply { putString("image", it) }) },
                            onBack = { finish() },
                            onDelete = { complete(Bundle().apply { putBoolean("delete", true) }) },
                        )
                    }
                }
            }
        }
    }
    private fun complete(data: Bundle) {
        if (sent) return
        sent = true
        receiver?.send(1, data)
        finish()
    }
    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean("result_sent", sent)
        super.onSaveInstanceState(outState)
    }
    override fun onDestroy() {
        if (isFinishing && !sent) receiver?.send(0, null)
        super.onDestroy()
    }
}
