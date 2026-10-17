package jp.jphacks.sd2606.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// backend/app/schemas.py と対応させること。
// 選択肢の値（occupation など）は Labels.kt の表示名と対応している。

@Serializable
data class UserProfile(
    val age: Int = 25,
    val occupation: String = "employee",
    @SerialName("annual_income") val annualIncome: Int = 0,
    @SerialName("health_insurance") val healthInsurance: String = "unknown",
    @SerialName("has_spouse") val hasSpouse: Boolean = false,
    val dependents: Int = 0,
    @SerialName("life_events") val lifeEvents: List<String> = emptyList(),
)

@Serializable
data class Recommendation(
    @SerialName("procedure_id") val procedureId: String,
    val title: String,
    val category: String,
    val summary: String,
    val priority: Int,
    val deadline: String? = null,
    val reasons: List<String> = emptyList(),
    @SerialName("personal_note") val personalNote: String? = null,
)

@Serializable
data class RecommendationResponse(
    val items: List<Recommendation>,
    @SerialName("overall_advice") val overallAdvice: String? = null,
    @SerialName("ai_generated") val aiGenerated: Boolean = false,
)

@Serializable
data class FlowStep(
    val title: String,
    val detail: String,
    val where: String? = null,
    val documents: List<String> = emptyList(),
)

@Serializable
data class Link(val label: String, val url: String)

@Serializable
data class Procedure(
    val id: String,
    val title: String,
    val category: String,
    val summary: String,
    val priority: Int,
    val deadline: String? = null,
    val office: String? = null,
    val steps: List<FlowStep> = emptyList(),
    val links: List<Link> = emptyList(),
)
