package com.jvillada.movi.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jvillada.movi.shared.model.Scope
import com.jvillada.movi.theme.*

@Composable
fun ScopeToggle(
    value: Scope,
    onChange: (Scope) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(999.dp))
            .background(MinSurfaceContainerLow)
            .border(1.dp, MinBorder, RoundedCornerShape(999.dp))
            .padding(3.dp),
    ) {
        listOf(Scope.SELF to "Individual", Scope.FAMILY to "Familiar").forEach { (scope, label) ->
            val isActive = value == scope
            Row(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(999.dp))
                    .background(if (isActive) MinSurfaceContainerHigh else Color.Transparent)
                    .clickable { onChange(scope) }
                    .padding(horizontal = 18.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (isActive) {
                    Text(
                        text = "✓ ",
                        fontSize = 13.sp,
                        color = MinPrimary,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Text(
                    text = label,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = if (isActive) MinText else MinTextDim,
                    letterSpacing = 0.1.sp,
                )
            }
        }
    }
}
