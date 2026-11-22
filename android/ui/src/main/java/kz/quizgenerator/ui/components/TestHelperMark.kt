package kz.quizgenerator.ui.components
import androidx.compose.foundation.Canvas
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription

/** Original TestHelper mark: two study cards and a clear check, no borrowed artwork. */
@Composable fun TestHelperMark(modifier: Modifier=Modifier) {
    val colors=MaterialTheme.colorScheme
    Canvas(modifier.semantics {contentDescription="TestHelper"}) {
        val radius=CornerRadius(size.width*.14f)
        drawRoundRect(colors.primaryContainer,Offset(size.width*.06f,size.height*.22f),Size(size.width*.72f,size.height*.73f),radius)
        drawRoundRect(colors.primary,Offset(size.width*.26f,size.height*.05f),Size(size.width*.70f,size.height*.76f),radius)
        drawLine(colors.onPrimary,Offset(size.width*.40f,size.height*.43f),Offset(size.width*.54f,size.height*.57f),size.width*.075f,StrokeCap.Round)
        drawLine(colors.onPrimary,Offset(size.width*.54f,size.height*.57f),Offset(size.width*.80f,size.height*.29f),size.width*.075f,StrokeCap.Round)
    }
}
