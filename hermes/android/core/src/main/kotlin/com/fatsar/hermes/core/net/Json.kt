package com.fatsar.hermes.core.net

import kotlinx.serialization.json.Json

/** Tüm katmanların paylaştığı, hataya dayanıklı JSON yapılandırması. */
val HermesJson: Json = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
    isLenient = true
    explicitNulls = false
    coerceInputValues = true
    prettyPrint = false
}

/** Dosyaya yazarken okunabilir olsun diye. */
val HermesPrettyJson: Json = Json(HermesJson) { prettyPrint = true }
