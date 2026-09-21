package com.vcompanion.shared.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.vcompanion.shared.designsystem.theme.StudioTheme
import com.vcompanion.shared.resources.Res
import com.vcompanion.shared.resources.qr_description
import com.vcompanion.shared.resources.qr_title
import com.vcompanion.shared.resources.qr_waiting_host
import org.jetbrains.compose.resources.stringResource

/**
 * Contenedor estilizado para la presentación del Código QR de emparejamiento.
 * Provee superficie elevada, bordes sutiles, quiet zone de alto contraste para
 * legibilidad por cámara y footer opcional de estado con StudioIndicator.
 *
 * Cumple con RF-001, RF-006, RNF-001 y Constitución Principio 1 y 8.
 */
@Composable
fun QrCard(
    modifier: Modifier = Modifier,
    title: String = stringResource(Res.string.qr_title),
    description: String? = stringResource(Res.string.qr_description),
    footer: (@Composable () -> Unit)? = {
        DefaultQrCardFooter()
    },
    qrContent: @Composable () -> Unit
) {
    val colors = StudioTheme.colors
    val spacing = StudioTheme.spacing
    val typography = StudioTheme.typography

    val cardShape = RoundedCornerShape(spacing.extraLarge)
    val qrFrameShape = RoundedCornerShape(spacing.medium)

    Column(
        modifier = modifier
            .background(color = colors.surfaceElevated, shape = cardShape)
            .border(width = 1.dp, color = colors.borderSubtle, shape = cardShape)
            .padding(spacing.extraLarge),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Título del código de emparejamiento
        Text(
            text = title,
            style = typography.titleMedium,
            color = colors.textPrimary,
            textAlign = TextAlign.Center
        )

        // Descripción o instrucción secundaria
        if (description != null) {
            Spacer(modifier = Modifier.height(spacing.small))
            Text(
                text = description,
                style = typography.bodyMedium,
                color = colors.textSecondary,
                textAlign = TextAlign.Center
            )
        }

        Spacer(modifier = Modifier.height(spacing.large))

        // Marco contenedor del QR con "quiet zone" blanco de alto contraste
        // garantizando escaneo confiable por cámaras de dispositivos móviles
        Box(
            modifier = Modifier
                .background(color = Color.White, shape = qrFrameShape)
                .border(width = 1.dp, color = colors.borderSubtle, shape = qrFrameShape)
                .padding(spacing.large),
            contentAlignment = Alignment.Center
        ) {
            qrContent()
        }

        // Pie de tarjeta con indicador de espera / estado
        if (footer != null) {
            Spacer(modifier = Modifier.height(spacing.large))
            footer()
        }
    }
}

/**
 * Pie de tarjeta por defecto con indicador luminoso pulsante y texto de espera.
 */
@Composable
private fun DefaultQrCardFooter() {
    val colors = StudioTheme.colors
    val spacing = StudioTheme.spacing
    val typography = StudioTheme.typography

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        StudioIndicator(
            color = colors.standbyAccent,
            isPulsing = true
        )
        Spacer(modifier = Modifier.width(spacing.medium))
        Text(
            text = stringResource(Res.string.qr_waiting_host),
            style = typography.labelSmall,
            color = colors.textSecondary
        )
    }
}
