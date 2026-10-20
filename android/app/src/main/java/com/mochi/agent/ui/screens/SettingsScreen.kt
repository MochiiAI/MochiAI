package com.mochi.agent.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mochi.agent.ui.theme.MochiBlack
import com.mochi.agent.ui.theme.MochiBlue
import com.mochi.agent.ui.theme.MochiBlueSoft
import com.mochi.agent.ui.theme.MochiPanel
import com.mochi.agent.ui.theme.MochiWhite

@Composable
fun SettingsScreen(
    githubToken: String,
    onSaveToken: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var token by remember { mutableStateOf(githubToken) }
    var cloudMode by remember { mutableStateOf(true) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MochiBlack)
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Text("Settings", style = MaterialTheme.typography.headlineSmall, color = MochiWhite)

        // Connection mode pill toggle
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text("Cloud sync", color = MochiWhite, fontSize = 16.sp)
            Switch(
                checked = cloudMode,
                onCheckedChange = { cloudMode = it },
                colors = SwitchDefaults.colors(
                    checkedTrackColor = MochiBlue,
                    checkedThumbColor = MochiWhite,
                    uncheckedTrackColor = MochiPanel,
                    uncheckedThumbColor = MochiBlueSoft,
                ),
            )
        }

        Text(
            "GitHub integration",
            style = MaterialTheme.typography.titleMedium,
            color = MochiWhite,
        )
        OutlinedTextField(
            value = token,
            onValueChange = { token = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("GitHub Personal Access Token", color = Color(0xFF8B93A7)) },
            placeholder = { Text("Paste a PAT to override the default account") },
            shape = RoundedCornerShape(18.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MochiBlue,
                unfocusedBorderColor = Color(0xFF2A2F45),
                focusedContainerColor = MochiPanel,
                unfocusedContainerColor = MochiPanel,
                cursorColor = MochiBlue,
                focusedTextColor = MochiWhite,
                unfocusedTextColor = MochiWhite,
            ),
        )

        Button(
            onClick = { onSaveToken(token) },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(26.dp),
            colors = ButtonDefaults.buttonColors(containerColor = MochiBlue),
        ) {
            Text("Save token", modifier = Modifier.padding(vertical = 6.dp))
        }
    }
}