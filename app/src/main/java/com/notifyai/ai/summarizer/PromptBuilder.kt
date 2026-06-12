package com.notifyai.ai.summarizer

import com.notifyai.domain.model.NotificationCluster

object PromptBuilder {

    private const val SYSTEM_PROMPT = """You are a helpful notification assistant running entirely on-device. 
Your job is to read raw notifications from various apps, understand their context, and summarize them logically.

Classify the information into these exact JSON categories:
1. "importantItems": Critical alerts, meeting updates, direct messages requiring attention.
2. "actionItems": Things the user needs to reply to, approve, or do.
3. "promotions": Marketing, sales, discounts, or offers.
4. "socialUpdates": Non-critical social media interactions (likes, generic views).
5. "summary": A very brief, human-readable 1-sentence overview of everything.

Rules:
- NEVER hallucinate information. Only use the provided notifications.
- IF a category has no items, return an empty array `[]`.
- OUTPUT EXACTLY AND ONLY VALID JSON. Do not include markdown blocks like ```json.
- Use double quotes for every key and string.
- Do not wrap the JSON in prose, bullets, code fences, or explanation.
- The top-level object must contain only these keys: "importantItems", "actionItems", "promotions", "socialUpdates", "summary".
"""

    fun buildPrompt(clusters: List<NotificationCluster>): String {
        val notificationText = clusters.joinToString("\n\n") { it.toPromptText() }
        
        return """
$SYSTEM_PROMPT

Example JSON:
{"importantItems":["Meeting moved to 3 PM"],"actionItems":["Reply to Sam"],"promotions":[],"socialUpdates":[],"summary":"You have one important update and one reply pending."}

--- NOTIFICATIONS ---
$notificationText

--- JSON OUTPUT ---
""".trimIndent()
    }
}
