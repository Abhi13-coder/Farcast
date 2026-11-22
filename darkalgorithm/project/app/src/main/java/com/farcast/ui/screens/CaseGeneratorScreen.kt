package com.farcast.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.farcast.ui.theme.*

data class GeneratorFormState(
    val title: String = "",
    val authorizedScope: String = "",
    val domain: String = "",
    val usernames: String = "",   // comma-separated in the form, split on submit
    val ips: String = "",
    val freeTextQuery: String = ""
)

/**
 * "Generate JSON" / "Feed JSON" as an actual form — ownerAuthorizedScope is
 * asked first and explained plainly (per SafetyGate's real dependency on
 * it), not buried as an afterthought field at the bottom.
 */
@Composable
fun CaseGeneratorScreen(
    onGenerate: (GeneratorFormState) -> Unit,
    onFeedGenerated: () -> Unit,
    generatedJsonPreview: String?
) {
    var form by remember { mutableStateOf(GeneratorFormState()) }

    Column(
        Modifier.fillMaxSize().background(FarcastBackground).verticalScroll(rememberScrollState()).padding(20.dp)
    ) {
        Text("New case", style = FarcastTypography.headlineMedium, color = FarcastTextPrimary)
        Spacer(Modifier.height(4.dp))
        Text(
            "Fill in whatever you already know — DarkMixer only activates the branches that apply.",
            style = FarcastTypography.bodyMedium, color = FarcastTextMuted
        )
        Spacer(Modifier.height(20.dp))

        FarcastField("Case title", form.title) { form = form.copy(title = it) }
        FarcastField(
            "Authorized scope", form.authorizedScope,
            helper = "What you're actually authorized to examine — SafetyGate checks every infrastructure-recon lead against this exact text."
        ) { form = form.copy(authorizedScope = it) }
        FarcastField("Domain (optional)", form.domain) { form = form.copy(domain = it) }
        FarcastField("Usernames, comma-separated (optional)", form.usernames) { form = form.copy(usernames = it) }
        FarcastField("IP addresses, comma-separated (optional)", form.ips) { form = form.copy(ips = it) }
        FarcastField("Free-text search query (optional)", form.freeTextQuery) { form = form.copy(freeTextQuery = it) }

        Spacer(Modifier.height(20.dp))
        Button(
            onClick = { onGenerate(form) },
            enabled = form.title.isNotBlank() && form.authorizedScope.isNotBlank(),
            colors = ButtonDefaults.buttonColors(containerColor = FarcastAmber, contentColor = FarcastBackground)
        ) { Text("Generate JSON") }

        generatedJsonPreview?.let { json ->
            Spacer(Modifier.height(20.dp))
            Text("Preview", style = FarcastTypography.titleMedium, color = FarcastTextPrimary)
            Text(json, style = FarcastDataStyleSmall, color = FarcastTextMuted, modifier = Modifier.padding(top = 8.dp))
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = onFeedGenerated,
                colors = ButtonDefaults.buttonColors(containerColor = FarcastCyan, contentColor = FarcastBackground)
            ) { Text("Feed JSON — start running") }
        }
    }
}

@Composable
private fun FarcastField(label: String, value: String, helper: String? = null, onChange: (String) -> Unit) {
    Column(Modifier.padding(bottom = 14.dp)) {
        OutlinedTextField(
            value = value,
            onValueChange = onChange,
            label = { Text(label, style = FarcastTypography.labelSmall) },
            textStyle = FarcastTypography.bodyLarge,
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = FarcastAmber,
                unfocusedBorderColor = FarcastHairline,
                focusedTextColor = FarcastTextPrimary,
                unfocusedTextColor = FarcastTextPrimary
            )
        )
        helper?.let { Text(it, style = FarcastDataStyleSmall, color = FarcastTextMuted, modifier = Modifier.padding(top = 4.dp)) }
    }
}
