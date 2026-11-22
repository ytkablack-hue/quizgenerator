package kz.quizgenerator.ui.screens
object CardSessionRules {
    fun difficult(cards: List<CardRow>)=cards.filter {it.rating=="hard"}.map {it.id}
    fun afterSave(reviewed: List<String>,id: String,saved: Boolean): List<String> = if(saved)(reviewed+id).distinct() else reviewed
    fun count(available: List<String>,reviewed: List<String>)=available.distinct().count {it in reviewed}
    fun completed(available: List<String>,reviewed: List<String>)=available.isNotEmpty() && count(available,reviewed)==available.distinct().size
}
