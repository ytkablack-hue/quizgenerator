package kz.quizgenerator

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview

// Debug-only preview fixtures. These are not user data or screenshots of an Android execution.
@Preview(name="Home 360dp Light",widthDp=360,heightDp=800,fontScale=1f)
@Composable private fun HomeLightPreview()=HomePreview("light",false)
@Preview(name="Home 360dp Light 1.3x",widthDp=360,heightDp=800,fontScale=1.3f)
@Composable private fun HomeLargeLightPreview()=HomePreview("light",true)
@Preview(name="Home 360dp Dark",widthDp=360,heightDp=800,fontScale=1f)
@Composable private fun HomeDarkPreview()=HomePreview("dark",false)
@Preview(name="Home 360dp Dark 1.3x",widthDp=360,heightDp=800,fontScale=1.3f)
@Composable private fun HomeLargeDarkPreview()=HomePreview("dark",true)

@Composable private fun HomePreview(theme: String,longText: Boolean) {
    val name=if(longText)"Пользователь с длинным именем" else "Студент"
    val card=Flashcard("preview-card","Вопрос-пример","Ответ-пример",reviews=if(longText)1 else 0)
    val set=StudySet("preview-set",if(longText)"Длинное название учебного набора по физике и механике" else "Мой учебный набор",cardIds=listOf(card.id))
    SoftStudyTheme(theme) {
        Scaffold(topBar={StudyHomeHeader(name,{},{})},bottomBar={StudyHomeNavigation(listOf("Главная" to Glyph.HOME,"Тесты" to Glyph.LIBRARY,"Карточки" to Glyph.CARDS,"Профиль" to Glyph.PROFILE),0){}}) {padding->
            Box(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding)) {
                StudyHomeFeed(name,listOf(set),emptyList(),StudyData(cards=listOf(card)),false,emptyList(),
                    onOpen={},onCreate={},onRename={},onExport={},onArchive={},onRestore={},onPreparation={},onRetry={},onLegacy={})
            }
        }
    }
}
