package com.infikey.settings

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.clickable

/** Guided 3-step setup: enable → switch → load AI. */
class SetupActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { SetupUi() }
    }
}

private val Ink = Color(0xFF101014)
private val Card = Color(0xFF1C1C26)
private val TextPrimary = Color(0xFFF4F4FA)
private val TextHint = Color(0xFF8A8A9A)
private val Accent = Color(0xFF8B5CF6)

@Composable
private fun SetupUi() {
    val ctx = LocalContext.current
    Surface(color = Ink) {
        Column(
            Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text("\u2726 Set up InfiKey", color = Accent, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Text(
                "Three taps and the AI keyboard is yours.",
                color = TextHint, fontSize = 14.sp
            )
            Step("1", "Enable InfiKey", "System settings → Enable the keyboard once") {
                ctx.startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
            }
            Step("2", "Switch to it", "Open the picker and choose InfiKey") {
                (ctx.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager)
                    ?.showInputMethodPicker()
            }
            Step("3", "Load the AI brain", "Open InfiKey → AI tab → download the model or add any cloud API") {
                ctx.startActivity(Intent(ctx, MainActivity::class.java))
            }
        }
    }
}

@Composable
private fun Step(num: String, title: String, desc: String, onTap: () -> Unit) {
    Row(
        Modifier.fillMaxWidth()
            .background(Card, RoundedCornerShape(16.dp))
            .clickable(onClick = onTap)
            .padding(18.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(num, color = Accent, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.width(14.dp))
        Column {
            Text(title, color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Text(desc, color = TextHint, fontSize = 13.sp)
        }
    }
}
