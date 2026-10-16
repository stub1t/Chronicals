package io.github.stub1t.chronicals.data

// Generated from src/lib/data/seed.ts so the stored JSON matches what the Capacitor app wrote.

internal class BuiltInType(val id: String, val name: String, val icon: String, val color: String, val category: String, val sortOrder: Int, val metricSchema: String)

internal val BUILT_IN_TYPES = listOf(
    BuiltInType("builtin-headache", "Headache", "H", "#3b82f6", "symptom", 0, "{\"fields\":[{\"key\":\"intensity\",\"label\":\"Intensity\",\"type\":\"slider\",\"required\":true,\"config\":{\"min\":1,\"max\":5,\"step\":1}},{\"key\":\"location\",\"label\":\"Where it hurts\",\"type\":\"select\",\"required\":false,\"config\":{\"extendable\":true,\"options\":[\"temples\",\"forehead\",\"back of head\",\"left side\",\"right side\",\"all over\",\"behind the eye\",\"neck\"]}},{\"key\":\"side_effects\",\"label\":\"Symptoms\",\"type\":\"checklist\",\"required\":false,\"config\":{\"extendable\":true,\"options\":[\"aura\",\"light sensitivity\",\"sound sensitivity\",\"nausea\",\"vomiting\",\"neck stiffness\",\"dizziness\",\"eye pain\",\"facial pain\",\"sinus pressure\",\"brain fog\",\"fatigue\"]}},{\"key\":\"notes\",\"label\":\"Notes\",\"type\":\"textarea\",\"required\":false,\"config\":{\"placeholder\":\"Additional notes...\"}}]}"),
    BuiltInType("builtin-stomachache", "Stomachache", "S", "#a855f7", "symptom", 1, "{\"fields\":[{\"key\":\"food_intake\",\"label\":\"What did you eat before this started?\",\"type\":\"textarea\",\"required\":false,\"config\":{\"placeholder\":\"Describe recent food intake...\"}},{\"key\":\"intensity\",\"label\":\"Intensity\",\"type\":\"slider\",\"required\":true,\"config\":{\"min\":1,\"max\":5,\"step\":1}},{\"key\":\"symptoms\",\"label\":\"Symptoms\",\"type\":\"checklist\",\"required\":false,\"config\":{\"extendable\":true,\"options\":[\"nausea\",\"cramps\",\"bloating\",\"reflux\",\"heartburn\",\"diarrhea\",\"constipation\",\"vomiting\",\"loss of appetite\",\"belching\"]}},{\"key\":\"notes\",\"label\":\"Notes\",\"type\":\"textarea\",\"required\":false,\"config\":{\"placeholder\":\"Additional notes...\"}}]}"),
    BuiltInType("builtin-allergy", "Allergy / hay fever", "A", "#22c55e", "symptom", 2, "{\"fields\":[{\"key\":\"intensity\",\"label\":\"Intensity\",\"type\":\"slider\",\"required\":true,\"config\":{\"min\":1,\"max\":5,\"step\":1}},{\"key\":\"symptoms\",\"label\":\"Symptoms\",\"type\":\"checklist\",\"required\":false,\"config\":{\"extendable\":true,\"options\":[\"sneezing\",\"itchy eyes\",\"runny nose\",\"hives\",\"skin rash\",\"shortness of breath\",\"itchy skin\",\"swollen eyes\",\"scratchy throat\",\"coughing\",\"ear pressure\"]}},{\"key\":\"notes\",\"label\":\"Notes\",\"type\":\"textarea\",\"required\":false,\"config\":{\"placeholder\":\"Additional notes...\"}}]}"),
    BuiltInType("builtin-hypo", "Blood sugar", "!", "#f59e0b", "event", 3, "{\"fields\":[{\"key\":\"direction\",\"label\":\"Blood sugar\",\"type\":\"select\",\"required\":true,\"config\":{\"options\":[\"too low\",\"too high\"]}},{\"key\":\"blood_glucose\",\"label\":\"Blood sugar reading\",\"type\":\"number\",\"required\":false,\"config\":{\"units\":[\"mg/dL\",\"mmol/L\"]}},{\"key\":\"intensity\",\"label\":\"Intensity\",\"type\":\"slider\",\"required\":true,\"config\":{\"min\":1,\"max\":5,\"step\":1}},{\"key\":\"symptoms\",\"label\":\"Symptoms\",\"type\":\"checklist\",\"required\":false,\"config\":{\"extendable\":true,\"options\":[\"shaking\",\"sweating\",\"confusion\",\"dizziness\",\"fatigue\",\"palpitations\",\"irritability\",\"hunger\",\"thirst\",\"frequent urination\",\"dry mouth\",\"blurred vision\",\"pale skin\",\"headache\"]}},{\"key\":\"notes\",\"label\":\"Notes\",\"type\":\"textarea\",\"required\":false,\"config\":{\"placeholder\":\"Additional notes...\"}}]}"),
    BuiltInType("builtin-period", "Period", "P", "#ec4899", "symptom", 5, "{\"fields\":[{\"key\":\"type\",\"label\":\"Type\",\"type\":\"select\",\"required\":true,\"config\":{\"options\":[\"bleeding\",\"spotting\"]}},{\"key\":\"flow\",\"label\":\"Flow\",\"type\":\"slider\",\"required\":false,\"config\":{\"min\":1,\"max\":5,\"step\":1}},{\"key\":\"symptoms\",\"label\":\"Symptoms\",\"type\":\"checklist\",\"required\":false,\"config\":{\"extendable\":true,\"options\":[\"cramps\",\"bloating\",\"mood changes\",\"fatigue\",\"back pain\",\"headache\",\"breast tenderness\",\"acne\"]}},{\"key\":\"notes\",\"label\":\"Notes\",\"type\":\"textarea\",\"required\":false,\"config\":{\"placeholder\":\"Additional notes...\"}}]}"),
    BuiltInType("builtin-meal", "Meal", "M", "#e07a5f", "meal", 6, "{\"fields\":[{\"key\":\"meal\",\"label\":\"Meal\",\"type\":\"select\",\"required\":false,\"config\":{\"options\":[\"breakfast\",\"lunch\",\"dinner\",\"snack\"]}},{\"key\":\"foods\",\"label\":\"Foods\",\"type\":\"checklist\",\"required\":false,\"config\":{\"extendable\":true,\"options\":[\"bread\",\"pasta\",\"dairy\",\"cheese\",\"meat\",\"fish\",\"fruit\",\"vegetables\",\"sweets\",\"chocolate\",\"coffee\",\"alcohol\",\"fast food\"]}},{\"key\":\"what\",\"label\":\"What exactly?\",\"type\":\"text\",\"required\":false,\"config\":{\"placeholder\":\"e.g. pasta with tomato sauce\"}}]}"),
    BuiltInType("builtin-note", "Note", "N", "#94a3b8", "note", 7, "{\"fields\":[{\"key\":\"notes\",\"label\":\"Note\",\"type\":\"textarea\",\"required\":true,\"config\":{\"placeholder\":\"Write your note...\"}}]}"),
)

/** id, name, category */
internal val BUILT_IN_TRIGGERS = listOf(
    Triple("trig-gluten", "Gluten", "food"),
    Triple("trig-dairy", "Dairy", "food"),
    Triple("trig-alcohol", "Alcohol", "food"),
    Triple("trig-caffeine", "Caffeine", "food"),
    Triple("trig-sugar", "Sugar", "food"),
    Triple("trig-processed", "Processed food", "food"),
    Triple("trig-spicy", "Spicy food", "food"),
    Triple("trig-nuts", "Nuts", "food"),
    Triple("trig-shellfish", "Shellfish", "food"),
    Triple("trig-weather-change", "Weather change", "environment"),
    Triple("trig-heat", "Heat", "environment"),
    Triple("trig-cold", "Cold", "environment"),
    Triple("trig-mold", "Mold", "environment"),
    Triple("trig-animal", "Animal hair", "environment"),
    Triple("trig-dust", "Dust", "environment"),
    Triple("trig-perfume", "Perfume / chemicals", "environment"),
    Triple("trig-stress", "Stress", "activity"),
    Triple("trig-poor-sleep", "Poor sleep", "activity"),
    Triple("trig-exercise", "Exercise", "activity"),
    Triple("trig-screen", "Screen time", "activity"),
    Triple("trig-travel", "Travel", "activity"),
    Triple("trig-dehydration", "Dehydration", "activity"),
    Triple("trig-skipped-meal", "Skipped meal", "activity"),
    Triple("trig-medication-change", "Medication change", "medication"),
)
