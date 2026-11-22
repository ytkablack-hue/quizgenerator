package kz.quizgenerator
import androidx.compose.foundation.Image
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Official light Google style remains readable in either application theme. */
@Composable internal fun GoogleBrandButton(enabled: Boolean,onClick: ()->Unit) {
    OutlinedButton(onClick,enabled=enabled,modifier=Modifier.fillMaxWidth().heightIn(min=52.dp),
        shape=RoundedCornerShape(16.dp),border=BorderStroke(1.dp,Color(0xFF747775)),
        colors=ButtonDefaults.outlinedButtonColors(containerColor=Color.White,contentColor=Color(0xFF1F1F1F)),
        contentPadding=PaddingValues(horizontal=12.dp,vertical=12.dp)) {
        Image(painterResource(R.drawable.google_g),null,Modifier.size(20.dp))
        Spacer(Modifier.width(10.dp))
        Text("Продолжить с Google",fontFamily=FontFamily(Font(R.font.google_sans,FontWeight.Medium)),fontSize=14.sp,lineHeight=20.sp)
    }
}
