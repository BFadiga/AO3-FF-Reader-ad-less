package com.ao3reader.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.SubcomposeAsyncImage

/**
 * A work's cover: the real image for FanFiction.net stories that have one, otherwise a tile in a
 * color derived from the title with its initials, so AO3 works still have something to recognize.
 */
@Composable
fun Cover(url: String?, title: String, rating: String, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(6.dp)
    Box(modifier.clip(shape)) {
        if (url != null) {
            SubcomposeAsyncImage(
                model = url,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
                loading = { TitleTile(title) },
                error = { TitleTile(title) },
            )
        } else {
            TitleTile(title)
        }
        if (rating.isNotBlank()) {
            Text(
                ratingShort(rating),
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 9.sp,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .clip(RoundedCornerShape(topStart = 4.dp))
                    .background(ratingColor(rating))
                    .padding(horizontal = 3.dp),
            )
        }
    }
}

private val tileColors = listOf(
    0xFF5C6BC0, 0xFF26A69A, 0xFF8D6E63, 0xFFAB47BC, 0xFF42A5F5, 0xFFEF5350, 0xFF66BB6A, 0xFFFFA726, 0xFF78909C, 0xFFEC407A,
)

@Composable
private fun TitleTile(title: String) {
    val color = Color(tileColors[Math.floorMod(title.hashCode(), tileColors.size)])
    val initials = title.split(Regex("""\s+""")).filter { w -> w.firstOrNull()?.isLetterOrDigit() == true }
        .take(2).joinToString("") { it.first().uppercase() }.ifBlank { "?" }
    Box(Modifier.fillMaxSize().background(color.copy(alpha = 0.85f)), contentAlignment = Alignment.Center) {
        Text(
            initials,
            color = Color.White,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Clip,
        )
    }
}
