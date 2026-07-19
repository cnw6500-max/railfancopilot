package com.railfancopilot.shared.tutorial

enum class TutorialStep(
    val title: String,
    val description: String,
    val featureTag: String
) {
    LIVE_TRAINS(
        title = "Live Train Map",
        description = "See real-time positions for Amtrak, SEPTA, MBTA, Metra, MTA, Caltrain, and SoundTransit trains on an interactive map.",
        featureTag = "map"
    ),
    TRAIN_DETAILS(
        title = "Train Details",
        description = "Tap any train to see its route, current delay status, next stops, and equipment information.",
        featureTag = "train_detail"
    ),
    LOCO_ID(
        title = "Locomotive Identifier",
        description = "Take a photo of a locomotive and the AI will identify the type, railroad, and tell you what it knows about that unit.",
        featureTag = "loco_id"
    ),
    SYMBOL_DECODER(
        title = "Train Symbol Decoder",
        description = "Enter a train symbol like 'QNYLA' and the AI will decode the origin, destination, and commodity.",
        featureTag = "symbol_decoder"
    ),
    RADIO_FREQUENCIES(
        title = "Radio Frequencies",
        description = "Look up AAR channel frequencies so you can listen to train crew communications on your scanner.",
        featureTag = "radio"
    ),
    GOLDEN_HOUR(
        title = "Golden Hour Calculator",
        description = "Find the best lighting times for photography at your location — sunrise, sunset, and golden hour windows.",
        featureTag = "golden_hour"
    ),
    NEARBY_SEARCH(
        title = "Nearby Trains",
        description = "Search for trains passing near any location. Great for scouting a new spot before you drive out.",
        featureTag = "nearby_search"
    )
}
