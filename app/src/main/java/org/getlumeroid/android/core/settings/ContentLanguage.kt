package org.getlumeroid.android.core.settings

private val EXCLUDED_SHORT_WORDS = setOf(
    "IN", "IT", "TO", "IS", "SO", "NO", "ME", "OR", "AM", "DO", "GO", "HE", "US", "AS", "AT", "BY", "MY", "ON", "UP"
)

data class ContentLanguage(
    val code: String,
    val displayName: String,
    val hint: String,
    val prefixes: List<String> = emptyList(),
    val tags: List<String> = emptyList(),
    val keywords: List<String> = emptyList()
) {
    val upperPrefixes: List<String> = prefixes.map { it.uppercase() }
    val upperKeywords: List<String> = keywords.map { it.uppercase() }
    val upperTags: List<String> = tags.map { it.uppercase() }

    // Precomputed patterns: 0 allocations at runtime!
    val precomputedTagPatterns: List<String> = buildList {
        for (tag in upperTags) {
            add("[$tag]")
            add("|$tag|")
            add("($tag)")
            add("|$tag ")
            add("| $tag ")
            add("| $tag|")
            add("|$tag:")
            add("| $tag:")
            add("|$tag-")
            add(" $tag]")
            add(" $tag)")
            add(" $tag|")
            add(" $tag - ")
            add(" $tag: ")

            // Only 3+ letter tags can match surrounded by spaces (e.g. " TAM ", " USA ", " FRA ")
            // Never 2-letter tags, which collide with prepositions/words
            if (tag.length >= 3 && tag !in EXCLUDED_SHORT_WORDS) {
                add(" $tag ")
            }
        }
    }

    val precomputedStartTags: List<String> = buildList {
        for (tag in upperTags) {
            // Structured start prefixes: MUST have trailing delimiter!
            add("$tag - ")
            add("$tag: ")
            add("$tag:")
            add("$tag|")
            add("[$tag]")
            add("|$tag|")
            add("($tag)")
            // Only 3+ letter tags can match followed by space at start (e.g. "USA NBC")
            if (tag.length >= 3 && tag !in EXCLUDED_SHORT_WORDS) {
                add("$tag ")
            }
        }
    }

    fun matchesPrefix(upperTitle: String): Boolean {
        for (i in upperPrefixes.indices) {
            if (upperTitle.startsWith(upperPrefixes[i])) return true
        }
        return false
    }

    fun matches(upperTitle: String, upperCat: String): Boolean {
        // 1. Direct configured prefix matches
        for (i in upperPrefixes.indices) {
            val p = upperPrefixes[i]
            if (upperTitle.startsWith(p) || upperCat.startsWith(p)) return true
        }

        // 2. Structured IPTV tag matches
        for (i in precomputedTagPatterns.indices) {
            val pat = precomputedTagPatterns[i]
            if (upperTitle.contains(pat) || upperCat.contains(pat)) return true
        }

        // Check start tags
        for (i in precomputedStartTags.indices) {
            val st = precomputedStartTags[i]
            if (upperTitle.startsWith(st) || upperCat.startsWith(st)) return true
        }

        // 3. Keyword matches
        for (i in upperKeywords.indices) {
            val kw = upperKeywords[i]
            if (upperTitle.contains(kw) || upperCat.contains(kw)) return true
        }

        return false
    }
}

object ContentLanguageCatalog {
    val allLanguages: List<ContentLanguage> = listOf(
        ContentLanguage(
            code = "af",
            displayName = "African / Afrikaans",
            hint = "AF, AFR",
            prefixes = listOf("AF - ", "AFR - "),
            tags = listOf("AF", "AFR"),
            keywords = listOf("AFRICAN", "AFRIKAANS")
        ),
        ContentLanguage(
            code = "sq",
            displayName = "Albanian",
            hint = "AL, ALB",
            prefixes = listOf("AL - ", "ALB - "),
            tags = listOf("AL", "ALB"),
            keywords = listOf("ALBANIAN", "ALBANIA", "SHQIP")
        ),
        ContentLanguage(
            code = "ar",
            displayName = "Arabic",
            hint = "AR, ARA",
            prefixes = listOf("AR - ", "AR: ", "ARA - "),
            tags = listOf("AR", "ARA"),
            keywords = listOf("ARABIC", "أفلام", "عربية", "مسرحيات", "مسلسلات", "رمضان", "SOURAT ", "AL-FATIHA", "وثائقية", "كرتون")
        ),
        ContentLanguage(
            code = "bn",
            displayName = "Bengali",
            hint = "BEN",
            prefixes = listOf("BEN - ", "BEN "),
            tags = listOf("BEN", "BENGALI"),
            keywords = listOf("BENGALI", "BANGLA")
        ),
        ContentLanguage(
            code = "zh",
            displayName = "Chinese",
            hint = "ZH, CHI",
            prefixes = listOf("ZH - ", "ZH ", "CHI - "),
            tags = listOf("ZH", "CHI"),
            keywords = listOf("CHINA", "CHINESE", "MANDARIN", "CANTONESE")
        ),
        ContentLanguage(
            code = "cs",
            displayName = "Czech / Slovak",
            hint = "CS, SK",
            prefixes = listOf("CS - ", "SK - ", "CZE - "),
            tags = listOf("CS", "SK", "CZE"),
            keywords = listOf("CZECH", "SLOVAK")
        ),
        ContentLanguage(
            code = "da",
            displayName = "Danish",
            hint = "DK, DAN",
            prefixes = listOf("DK - ", "DK "),
            tags = listOf("DK", "DAN"),
            keywords = listOf("DANMARK", "DANISH")
        ),
        ContentLanguage(
            code = "nl",
            displayName = "Dutch",
            hint = "NL, NLD",
            prefixes = listOf("NL - ", "NL ", "NLD - "),
            tags = listOf("NL", "NLD", "BE", "BEL"),
            keywords = listOf("DUTCH", "NETHERLAND")
        ),
        ContentLanguage(
            code = "en",
            displayName = "English",
            hint = "EN, USA, UK, CA, AU",
            prefixes = listOf("EN - ", "EN: ", "USA - ", "USA: ", "UK - ", "UK: ", "US - ", "US: ", "CA - ", "AU - ", "ENG - ", "ENG: "),
            tags = listOf("EN", "USA", "UK", "US", "NA", "CA", "AU", "NZ", "AUS", "ENG"),
            keywords = listOf("ENGLISH", "|EN|", "|NA|", "[EN]")
        ),
        ContentLanguage(
            code = "exyu",
            displayName = "Ex-Yu / Balkan",
            hint = "EXYU, ALB, BG",
            prefixes = listOf("EXYU - ", "ALB - ", "BG - "),
            tags = listOf("EXYU", "ALB", "BG", "BLN", "BALKAN"),
            keywords = listOf("ALBANIAN", "BULGARIA", "CRTANE SERIJE")
        ),
        ContentLanguage(
            code = "fi",
            displayName = "Finnish",
            hint = "FI, FIN",
            prefixes = listOf("FI - ", "FI "),
            tags = listOf("FI", "FIN"),
            keywords = listOf("FINLAND", "FINNISH")
        ),
        ContentLanguage(
            code = "fr",
            displayName = "French",
            hint = "FR, FRA",
            prefixes = listOf("FR - ", "FR ", "FRA - "),
            tags = listOf("FR", "FRA"),
            keywords = listOf("FRENCH", "FRANCE", "CANAL+", "TF1", "DOCUMENTAIRES", "LIGUE 1")
        ),
        ContentLanguage(
            code = "de",
            displayName = "German",
            hint = "DE, DEU",
            prefixes = listOf("DE - ", "DE ", "DEU - "),
            tags = listOf("DE", "DEU"),
            keywords = listOf("GERMAN", "GERMANY", "KINDER SERIEN", "BUNDESLIGA")
        ),
        ContentLanguage(
            code = "el",
            displayName = "Greek",
            hint = "GR, GRE",
            prefixes = listOf("GR - ", "GR ", "GRE - "),
            tags = listOf("GR", "GRE"),
            keywords = listOf("GREECE", "GREEK")
        ),
        ContentLanguage(
            code = "gu",
            displayName = "Gujarati",
            hint = "GUJ",
            prefixes = listOf("GUJ - ", "GUJ "),
            tags = listOf("GUJ"),
            keywords = listOf("GUJARATI")
        ),
        ContentLanguage(
            code = "he",
            displayName = "Hebrew",
            hint = "HE, HEB",
            prefixes = listOf("HE - ", "HEB - "),
            tags = listOf("HE", "HEB"),
            keywords = listOf("HEBREW", "ISRAEL")
        ),
        ContentLanguage(
            code = "hi",
            displayName = "Hindi / Bollywood",
            hint = "HIN, IND",
            prefixes = listOf("HIN - ", "HIN: ", "HINDI - ", "HINDI: ", "IN - ", "IN: ", "IN-", "IND - ", "IND: "),
            tags = listOf("HIN", "IND"),
            keywords = listOf("HINDI", "BOLLYWOOD")
        ),
        ContentLanguage(
            code = "hu",
            displayName = "Hungarian",
            hint = "HU, HUN",
            prefixes = listOf("HU - ", "HUN - "),
            tags = listOf("HU", "HUN"),
            keywords = listOf("HUNGARIA", "HUNGARIAN")
        ),
        ContentLanguage(
            code = "id",
            displayName = "Indonesian",
            hint = "ID, INDO",
            prefixes = listOf("ID - ", "INDO - "),
            tags = listOf("ID", "INDO"),
            keywords = listOf("INDONESIA", "INDONESIAN")
        ),
        ContentLanguage(
            code = "it",
            displayName = "Italian",
            hint = "IT, ITA",
            prefixes = listOf("IT - ", "IT ", "ITA - "),
            tags = listOf("IT", "ITA"),
            keywords = listOf("ITALIAN", "ITALY", "SERIE A")
        ),
        ContentLanguage(
            code = "ja",
            displayName = "Japanese",
            hint = "JA, JPN",
            prefixes = listOf("JA - ", "JA ", "JPN - "),
            tags = listOf("JA", "JPN"),
            keywords = listOf("JAPAN", "JAPANESE", "MANGA")
        ),
        ContentLanguage(
            code = "kn",
            displayName = "Kannada",
            hint = "KAN",
            prefixes = listOf("KAN - ", "KAN: ", "KAN:", "KANNADA - ", "KANNADA: ", "KN - ", "KN: "),
            tags = listOf("KAN", "KANNADA"),
            keywords = listOf("KANNADA", "|AS| KANNADA", "ಕನ್ನಡ")
        ),
        ContentLanguage(
            code = "ko",
            displayName = "Korean",
            hint = "KO, KOR",
            prefixes = listOf("KO - ", "KO ", "KOR - "),
            tags = listOf("KO", "KOR"),
            keywords = listOf("KOREA", "KOREAN", "K-DRAMA")
        ),
        ContentLanguage(
            code = "ku",
            displayName = "Kurdish",
            hint = "KURD",
            prefixes = listOf("KURD - "),
            tags = listOf("KURD"),
            keywords = listOf("KURDISH", "KURDISTAN")
        ),
        ContentLanguage(
            code = "ml",
            displayName = "Malayalam",
            hint = "MAL",
            prefixes = listOf("MAL - ", "MAL: ", "MAL:", "MALAYALAM - ", "MALAYALAM: ", "MA - ", "MA: "),
            tags = listOf("MAL", "MALAYALAM"),
            keywords = listOf("MALAYALAM", "|AS| MALAYALAM", "മലയാളം")
        ),
        ContentLanguage(
            code = "mr",
            displayName = "Marathi",
            hint = "MAR",
            prefixes = listOf("MAR - ", "MAR "),
            tags = listOf("MAR"),
            keywords = listOf("MARATHI")
        ),
        ContentLanguage(
            code = "no",
            displayName = "Norwegian",
            hint = "NO, NOR",
            prefixes = listOf("NO - ", "NOR - "),
            tags = listOf("NO", "NOR"),
            keywords = listOf("NORWAY", "NORWEGIAN")
        ),
        ContentLanguage(
            code = "fa",
            displayName = "Persian / Farsi",
            hint = "FA, PER",
            prefixes = listOf("FA - ", "PER - "),
            tags = listOf("FA", "PER"),
            keywords = listOf("FARSI", "IRAN")
        ),
        ContentLanguage(
            code = "pl",
            displayName = "Polish",
            hint = "PL, POL",
            prefixes = listOf("PL - ", "PL ", "POL - "),
            tags = listOf("PL", "POL"),
            keywords = listOf("POLISH", "POLAND")
        ),
        ContentLanguage(
            code = "pt",
            displayName = "Portuguese",
            hint = "PT, POR",
            prefixes = listOf("PT - ", "PT ", "POR - "),
            tags = listOf("PT", "POR"),
            keywords = listOf("PORTUGAL", "PORTUGUESE", "BRAZIL")
        ),
        ContentLanguage(
            code = "pa",
            displayName = "Punjabi",
            hint = "PUN",
            prefixes = listOf("PUN - ", "PUN "),
            tags = listOf("PUN"),
            keywords = listOf("PUNJABI")
        ),
        ContentLanguage(
            code = "ro",
            displayName = "Romanian",
            hint = "RO, ROM",
            prefixes = listOf("RO - ", "ROM - "),
            tags = listOf("RO", "ROM"),
            keywords = listOf("ROMANIA", "ROMANIAN")
        ),
        ContentLanguage(
            code = "ru",
            displayName = "Russian",
            hint = "RU, RUS",
            prefixes = listOf("RU - ", "RU ", "RUS - "),
            tags = listOf("RU", "RUS"),
            keywords = listOf("RUSSIAN", "RUSSIA")
        ),
        ContentLanguage(
            code = "es",
            displayName = "Spanish",
            hint = "ES, SPA, Latino",
            prefixes = listOf("ES - ", "ES ", "SPA - "),
            tags = listOf("ES", "SPA"),
            keywords = listOf("SPANISH", "LATINO", "SPAIN", "MOVISTAR+", "LA LIGA")
        ),
        ContentLanguage(
            code = "sv",
            displayName = "Swedish",
            hint = "SE, SWE",
            prefixes = listOf("SE - ", "SWE - "),
            tags = listOf("SE", "SWE", "SW"),
            keywords = listOf("SWEDEN", "SWEDISH")
        ),
        ContentLanguage(
            code = "tl",
            displayName = "Tagalog / Filipino",
            hint = "TL, FIL",
            prefixes = listOf("TL - ", "FIL - "),
            tags = listOf("TL", "FIL"),
            keywords = listOf("PHILIPPINES", "TAGALOG")
        ),
        ContentLanguage(
            code = "ta",
            displayName = "Tamil",
            hint = "TAM",
            prefixes = listOf("TAM - ", "TAM: ", "TAM:", "TAMIL - ", "TAMIL: ", "TAMIL ", "TM - ", "TM: "),
            tags = listOf("TAM", "TAMIL"),
            keywords = listOf("TAMIL", "|AS| TAMIL", "தமிழ்")
        ),
        ContentLanguage(
            code = "te",
            displayName = "Telugu",
            hint = "TEL",
            prefixes = listOf("TEL - ", "TEL: ", "TEL:", "TELUGU - ", "TELUGU: ", "TELUGU ", "TG - ", "TG: "),
            tags = listOf("TEL", "TELUGU"),
            keywords = listOf("TELUGU", "|AS| TELUGU", "తెలుగు")
        ),
        ContentLanguage(
            code = "th",
            displayName = "Thai",
            hint = "TH, THA",
            prefixes = listOf("TH - ", "THA - "),
            tags = listOf("TH", "THA"),
            keywords = listOf("THAILAND", "THAI")
        ),
        ContentLanguage(
            code = "tr",
            displayName = "Turkish",
            hint = "TR, TUR",
            prefixes = listOf("TR - ", "TR: ", "TUR - ", "TUR: "),
            tags = listOf("TR", "TUR"),
            keywords = listOf("TURKISH", "TURKEY", "تركية", "تركيه", "مسلسلات تركية", "مسلسلات تركيه", "DİZİ", "DIZI")
        ),
        ContentLanguage(
            code = "uk",
            displayName = "Ukrainian",
            hint = "UKR",
            prefixes = listOf("UKR - "),
            tags = listOf("UKR"),
            keywords = listOf("UKRAINE", "UKRAINIAN")
        ),
        ContentLanguage(
            code = "ur",
            displayName = "Urdu / Pakistani",
            hint = "URD, PAK",
            prefixes = listOf("URD - ", "URD ", "PAK - ", "PAK "),
            tags = listOf("URD", "PAK"),
            keywords = listOf("URDU", "PAKISTAN")
        ),
        ContentLanguage(
            code = "vi",
            displayName = "Vietnamese",
            hint = "VI, VIE",
            prefixes = listOf("VI - ", "VIE - "),
            tags = listOf("VI", "VIE"),
            keywords = listOf("VIETNAM", "VIETNAMESE")
        )
    )

    private val byCodeMap: Map<String, ContentLanguage> by lazy {
        val map = HashMap<String, ContentLanguage>(allLanguages.size * 2)
        for (lang in allLanguages) {
            map[lang.code.lowercase()] = lang
            map[lang.displayName.lowercase()] = lang
        }
        map
    }

    val allPrefixPairs: List<Pair<String, ContentLanguage>> by lazy {
        allLanguages.flatMap { lang ->
            lang.upperPrefixes.map { it to lang }
        }.sortedByDescending { it.first.length }
    }

    fun findPrefixLanguage(upperTitle: String): ContentLanguage? {
        for (i in allPrefixPairs.indices) {
            val pair = allPrefixPairs[i]
            if (upperTitle.startsWith(pair.first)) {
                return pair.second
            }
        }
        return null
    }

    fun findByCode(code: String): ContentLanguage? {
        return byCodeMap[code.lowercase().trim()]
    }
}
