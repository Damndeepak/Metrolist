package com.metrolist.music.lyrics

import java.util.Locale

internal val verciEmojis = buildMap {
    listOf(
        "❤️" to "love loved lover loves dil pyaar pyar ishq mohabbat",
        "💔" to "broken heartbreak hurt pain dard",
        "💋" to "kiss kisses kissing lips",
        "🌙" to "moon moonlight chand raat night midnight tonight",
        "✨" to "stars shine glow sparkle taare magic",
        "🔥" to "fire flame burn burning aag lit",
        "🌧️" to "rain raining baarish tears cry crying",
        "🌊" to "ocean sea waves paani water",
        "🦋" to "butterfly butterflies fly flying freedom",
        "💭" to "dream dreams dreaming think thought khwaab yaad",
        "🥀" to "lonely alone tanha miss missing",
        "☀️" to "sun sunshine morning day subah",
        "🌹" to "rose roses flower flowers gulab",
        "👀" to "eyes eye look looking see aankhen",
        "💫" to "crazy dizzy pagal spinning",
        "💃" to "dance dancing party groove",
        "🎶" to "music song singing melody gaana",
        "🫶" to "care together saath always forever",
        "⚡" to "thunder lightning electric power",
        "🚀" to "sky space rocket high aasman",
        "🧊" to "ice cold frozen freeze",
        "👑" to "king queen crown royal",
        "💀" to "dead death dying die",
        "😈" to "devil demon demons wicked",
        "😇" to "angel heaven paradise",
        "💸" to "money cash rich dollar dollars paisa",
        "🚬" to "smoke smoking cigarette",
        "🍷" to "wine drunk drinking",
        "😊" to "smile smiling happy laugh",
        "🤫" to "secret secrets quiet silence whisper",
        "🏠" to "home ghar house",
        "🌍" to "world duniya earth",
        "⏳" to "time waqt waiting wait",
        "🛣️" to "road roads journey safar",
    ).forEach { (emoji, words) -> words.split(' ').forEach { put(it, emoji) } }
}


private val hindiEmojis = buildMap {
    listOf(
        "❤️" to "प्यार प्रेम इश्क़ इश्क मोहब्बत दिल दिलों हृदय pyar pyaar pyaari pyaare dilon",
        "💔" to "दर्द दर्दों टूटा टूटी टूटे तड़प जुदाई दर्दभरी dard dardon toota tooti juda judai",
        "👀" to "आँख आंख आँखें आंखें आँखों आंखों नयन नैन निगाह निगाहें नज़रों नजर nazar nazrein aankh aankhein aankhen aankhon naina nain",
        "💋" to "चूम चूमना चुम्बन होंठ होंठों लब लबों chum chumna hoth hothon lab labon",
        "😊" to "हँसी हंसी हँसना हंसना मुस्कान मुस्कुराना खुशी खुश खुशियाँ खुशियां khushi khushiyan muskaan hasi hansi",
        "😢" to "आँसू आंसू आँसुओं आंसुओं रोना रोती रोते rona roti rote aansu aansuon",
        "🌙" to "चाँद चांद चाँदनी चांदनी रात रातें रातों रात्रि chand chaand chandni raatein raaton",
        "☀️" to "सूरज धूप सुबह सवेरा suraj dhoop savera",
        "✨" to "तारा तारे तारों सितारा सितारे सितारों चमक roshni taara taaron sitare sitaron चमकना",
        "🌧️" to "बारिश बरसात बरस बरसे बरसती बूंद बूंदें बूँदें baarish barish barsaat boond boondein",
        "🔥" to "आग जल जलता जलती जले जला आगों aag jalta jalti jala",
        "🌊" to "पानी समंदर सागर समुद्र लहर लहरें लहरों नदी नदियाँ paani samandar sagar leher lehrein nadi",
        "🌹" to "गुलाब फूल फूलों कली कलियाँ kali kaliyan phool phoolon gulab",
        "🍃" to "हवा हवाएँ हवाएं पत्ता पत्ते पत्तों हवाों hawa hawaein patta patte",
        "🦋" to "तितली तितलियाँ उड़ उड़ना उड़ते उड़ती titli titliyan udna udte",
        "💭" to "सपना सपने सपनों ख़्वाब ख्वाब याद यादें यादों सोच khwab khwaab khwabon sapna sapne sapno yaadein yaadon soch",
        "🥀" to "तन्हा तन्हाई अकेला अकेली अकेले tanha tanhai akela akeli akele",
        "🫶" to "साथ हमेशा forever saath hamesha साथों",
        "💃" to "नाच नाचना नाचती नाचे naach nachna nachti",
        "🎶" to "गीत गाना गाने गानों संगीत सुर धुन धुनों geet gaane gaano sangeet dhun",
        "🧊" to "ठंड ठंडी बर्फ sardi thand thandi barf",
        "👑" to "राजा रानी बादशाह ताज raja rani badshah taaj",
        "💸" to "पैसा पैसे पैसों दौलत पैसाों paisa paise paison daulat",
        "⏳" to "वक्त वक़्त समय इंतज़ार इंतजार waqt intezar intzaar samay",
        "🏠" to "घर घरों मकान ghar gharon makaan",
        "🌍" to "दुनिया जहाँ जहां संसार duniya jahaan jahan sansar",
        "🛣️" to "रास्ता रास्ते रास्तों राह राहों सफर सफ़र rasta raaste raaston raah raahon safar",
        "😈" to "शैतान शैतानों shaitaan shaitan",
        "😇" to "फरिश्ता फ़रिश्ता फरिश्ते जन्नत farishta jannat",
        "🩸" to "खून लहू khoon lahu",
        "💪" to "ताकत शक्ति taqat shakti",
        "🙏" to "दुआ दुआएं दुआएँ भगवान खुदा ख़ुदा dua duayein khuda bhagwan",
        "🤫" to "चुप खामोश ख़ामोश खामोशी राज़ raaz chup khamosh khamoshi",
        "🥰" to "जान जानू जानम जानेमन sanam jaan jaanu jaanam janam सनम",
        "⚡" to "बिजली तूफान तूफ़ान bijli toofan tufaan",
        "🍷" to "शराब नशा नशे जाम शराबी sharaab sharab nasha nashe jaam",
        "🤗" to "बाहें बाहों बाँहें बाँहों गले gale baahein baahon",
    ).forEach { (emoji, words) -> words.split(' ').forEach { put(it, emoji) } }
}

private val extraEmojis = buildMap {
    listOf(
        "🐈" to "cat cats kitten kitty", "🐕" to "dog dogs puppy puppies", "🕊️" to "bird birds dove doves wing wings",
        "🌳" to "tree trees forest forests leaf leaves", "📖" to "story stories book books page pages",
        "💍" to "ring rings marry married wedding marriage", "🤝" to "friend friends friendship buddy buddies",
        "🚗" to "car cars drive driving ride rides", "🚆" to "train trains station stations",
        "✈️" to "plane planes flight flights airport", "🧳" to "travel traveling travelling journey journeys",
        "🏃" to "run runs running ran chase chasing", "🚶" to "walk walks walking walked",
        "🛌" to "bed beds sleep sleeps sleeping slept", "⏰" to "wake waking awake alarm alarms",
        "💫" to "spin spinning dizzy dizziness", "💥" to "boom bang explode explosion",
        "💎" to "diamond diamonds gem gems jewel jewels", "🪞" to "mirror mirrors reflection reflections",
        "📞" to "call calls calling phone phones telephone", "💌" to "letter letters message messages texting",
        "🔗" to "chain chains tied bond bonds", "🔓" to "free freed escape escaping freedom",
        "🚪" to "door doors open opening closed closing", "🧲" to "magnet magnets attract attraction",
        "🏔️" to "mountain mountains hill hills peak peaks", "🏖️" to "beach beaches shore shores sand",
        "❄️" to "snow snowy winter winters frozen freezing", "☁️" to "cloud clouds cloudy",
        "🫀" to "heart hearts heartbeat heartbeats", "🧠" to "brain brains mind minds memory memories",
        "😤" to "hate hates hating hated anger angry", "😨" to "fear fears afraid scared scary",
        "😴" to "tired sleepy exhausted", "😏" to "tease teasing naughty flirting flirt",
        "🏆" to "win wins winning won winner winners", "🎲" to "chance chances luck lucky gamble gambling",
        "🧨" to "danger dangerous dynamite", "🪄" to "spell spells magic magical witch witches",
        "⚔️" to "fight fights fighting fought sword swords battle battles", "🛡️" to "protect protecting safe safety",
        "☠️" to "poison poisonous toxic toxicity", "🖤" to "dark darkness black shadow shadows",
        "🕯️" to "candle candles flame flames", "📸" to "picture pictures photo photos photograph photographs",
        "💯" to "real really true truth honest honesty", "♾️" to "eternal eternity infinite infinity",
    ).forEach { (emoji, words) -> words.split(' ').forEach { put(it, emoji) } }
}

internal fun verciEmoji(text: String): String {
    val word = text.lowercase(Locale.ROOT).filter {
        it.isLetter() || Character.getType(it) == Character.NON_SPACING_MARK.toInt() ||
            Character.getType(it) == Character.COMBINING_SPACING_MARK.toInt()
    }
    fun lookup(value: String) = hindiEmojis[value] ?: verciEmojis[value] ?: extraEmojis[value]
    lookup(word)?.let { return it }
    val candidates = buildList {
        if (word.endsWith("ies")) add(word.dropLast(3) + "y")
        if (word.endsWith("ves")) { add(word.dropLast(3) + "f"); add(word.dropLast(3) + "fe") }
        for (suffix in listOf("ing", "ed", "es", "s")) if (word.length > suffix.length + 2 && word.endsWith(suffix)) {
            val root = word.dropLast(suffix.length)
            add(root); add(root + "e")
            if (root.length > 2 && root.last() == root[root.lastIndex - 1]) add(root.dropLast(1))
        }
    }
    return candidates.firstNotNullOfOrNull { lookup(it) } ?: ""
}
