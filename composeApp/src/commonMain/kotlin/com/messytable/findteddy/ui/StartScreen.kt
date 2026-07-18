package com.messytable.findteddy.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.messytable.findteddy.game.Teddy
import kotlin.math.min
import messytable.composeapp.generated.resources.Res
import messytable.composeapp.generated.resources.app_title
import messytable.composeapp.generated.resources.play
import messytable.composeapp.generated.resources.start_subtitle
import org.jetbrains.compose.resources.stringResource

@Composable
fun StartScreen(onPlay: () -> Unit) {
    Column(
        Modifier.fillMaxSize().background(RoomBackground),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            stringResource(Res.string.app_title),
            fontSize = 46.sp,
            fontWeight = FontWeight.ExtraBold,
            color = Color(0xFF37474F),
        )
        Canvas(Modifier.size(240.dp).padding(vertical = 8.dp)) {
            drawTeddy(
                Teddy(
                    cx = size.width / 2f,
                    bottom = size.height,
                    scale = min(size.width, size.height) * 2.1f,
                )
            )
        }
        Text(
            stringResource(Res.string.start_subtitle),
            fontSize = 18.sp,
            fontWeight = FontWeight.Medium,
            color = Color(0xFF546E7A),
            modifier = Modifier.padding(top = 12.dp, bottom = 28.dp),
        )
        Button(
            onClick = onPlay,
            shape = RoundedCornerShape(32.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF43A047)),
        ) {
            Text(
                stringResource(Res.string.play),
                fontSize = 28.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Color.White,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            )
        }
    }
}
