package com.devora.mencare.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.devora.mencare.core.designsystem.theme.Bone
import com.devora.mencare.core.designsystem.theme.Cormorant
import com.devora.mencare.core.designsystem.theme.Ink
import com.devora.mencare.core.designsystem.theme.OliveWood
import java.io.File

/**
 * Foto profilo con le iniziali come ripiego: è la stessa in tutte le aree.
 * [editable] aggiunge il segno della macchina fotografica, per dire che
 * toccandolo si cambia la foto; senza, l'avatar può comunque portare altrove.
 */
@Composable
fun ProfileAvatar(
    initials: String,
    photoPath: String?,
    modifier: Modifier = Modifier,
    size: Dp = 56.dp,
    corner: Dp = 17.dp,
    onClick: (() -> Unit)? = null,
    editable: Boolean = false,
    contentDescription: String? = null,
) {
    val shape = RoundedCornerShape(corner)
    val base = modifier.size(size).clip(shape).background(OliveWood)
    Box(
        modifier = if (onClick != null) base.clickable(onClick = onClick) else base,
        contentAlignment = Alignment.Center,
    ) {
        if (photoPath != null) {
            AsyncImage(
                // Con il backend la foto è un URL; con i dati finti era un file
                // nello spazio dell'app. Coil sa caricare entrambi, basta dargli
                // il tipo giusto — un `File` con dentro "https://…" no.
                model = if (photoPath.startsWith("http")) photoPath else File(photoPath),
                contentDescription = contentDescription,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(size).clip(shape),
            )
        } else {
            Text(
                initials.uppercase(),
                fontFamily = Cormorant,
                fontSize = (size.value * 0.34).sp,
                color = Bone,
            )
        }
        if (editable) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .size(size * 0.36f)
                    .clip(RoundedCornerShape(corner * 0.5f))
                    .background(Ink),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Outlined.PhotoCamera,
                    contentDescription = null,
                    tint = Bone,
                    modifier = Modifier.size(size * 0.2f),
                )
            }
        }
    }
}
