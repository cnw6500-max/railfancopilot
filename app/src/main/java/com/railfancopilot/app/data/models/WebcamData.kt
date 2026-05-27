package com.railfancopilot.app.data.models

data class RailcamEntry(
    val id: String,
    val name: String,
    val location: String,
    val railroad: String,
    val subdivision: String,
    val description: String,
    val url: String
)

val ALL_RAILCAMS = listOf(
    RailcamEntry(
        id = "rochelle_il",
        name = "Rochelle Railroad Park",
        location = "Rochelle, IL",
        railroad = "BNSF / UP",
        subdivision = "BNSF Chillicothe Sub / UP Overland",
        description = "Live crossing of BNSF and UP mainlines — one of the busiest rail intersections in the US.",
        url = "https://www.youtube.com/results?search_query=rochelle+railroad+park+live+cam"
    ),
    RailcamEntry(
        id = "cajon_pass",
        name = "Cajon Pass",
        location = "Cajon, CA",
        railroad = "BNSF / UP",
        subdivision = "BNSF Transcon / UP LA Sub",
        description = "Fan-operated cameras at the summit of Cajon Pass watching heavy freights climb the grade.",
        url = "https://www.youtube.com/results?search_query=cajon+pass+live+train+cam"
    ),
    RailcamEntry(
        id = "tehachapi_loop",
        name = "Tehachapi Loop",
        location = "Tehachapi, CA",
        railroad = "BNSF / UP",
        subdivision = "BNSF Mojave Sub",
        description = "Classic spiral loop where trains visually pass over themselves. Stunning mountain scenery.",
        url = "https://www.youtube.com/results?search_query=tehachapi+loop+live+train+cam"
    ),
    RailcamEntry(
        id = "donner_pass",
        name = "Donner Pass",
        location = "Donner Summit, CA",
        railroad = "UP",
        subdivision = "UP Overland Route",
        description = "UP's dramatic crossing of the Sierra Nevada — snow sheds, helpers, and spectacular mountain scenery year-round.",
        url = "https://www.youtube.com/results?search_query=donner+pass+union+pacific+live+train+cam"
    ),
    RailcamEntry(
        id = "horseshoe_curve",
        name = "Horseshoe Curve",
        location = "Altoona, PA",
        railroad = "NS",
        subdivision = "NS Pittsburgh Line",
        description = "Historic NS horseshoe curve cutting through the Allegheny Mountains. NPS visitor center on-site.",
        url = "https://www.youtube.com/results?search_query=horseshoe+curve+altoona+live+train"
    ),
    RailcamEntry(
        id = "fostoria_oh",
        name = "Fostoria Iron Triangle",
        location = "Fostoria, OH",
        railroad = "CSX / NS",
        subdivision = "Multiple mainlines",
        description = "Three mainlines converge at Fostoria — watch CSX and NS freights roll through the triangle.",
        url = "https://www.youtube.com/results?search_query=fostoria+ohio+iron+triangle+live+cam"
    ),
    RailcamEntry(
        id = "up_north_platte",
        name = "UP Bailey Yard — Golden Spike Tower",
        location = "North Platte, NE",
        railroad = "UP",
        subdivision = "UP Overland Route",
        description = "World's largest rail yard. Golden Spike Tower offers live webcam views of over 10,000 cars per day.",
        url = "https://www.goldenspike.org/webcam/"
    ),
    RailcamEntry(
        id = "raton_pass",
        name = "Raton Pass",
        location = "Raton, NM",
        railroad = "BNSF",
        subdivision = "BNSF Raton Sub",
        description = "Steep 3.5% grade crossing the Sangre de Cristo Mountains — helpers required on almost every train.",
        url = "https://www.youtube.com/results?search_query=raton+pass+bnsf+live+train+cam"
    ),
    RailcamEntry(
        id = "marias_pass",
        name = "Marias Pass",
        location = "Essex, MT",
        railroad = "BNSF",
        subdivision = "BNSF Havre Sub",
        description = "Lowest pass through the Rockies — BNSF's northern Transcon through glacier country.",
        url = "https://www.youtube.com/results?search_query=marias+pass+bnsf+montana+trains"
    ),
    RailcamEntry(
        id = "bnsf_galesburg",
        name = "BNSF Galesburg",
        location = "Galesburg, IL",
        railroad = "BNSF",
        subdivision = "BNSF Chillicothe Sub",
        description = "Major BNSF yard and mainline. Heavy intermodal, coal, and manifest traffic throughout the day.",
        url = "https://www.youtube.com/results?search_query=bnsf+galesburg+illinois+trains+live"
    ),
    RailcamEntry(
        id = "kingman_az",
        name = "Kingman",
        location = "Kingman, AZ",
        railroad = "BNSF",
        subdivision = "BNSF Transcon / Seligman Sub",
        description = "High-desert BNSF Transcon — constant intermodal and manifest traffic through the Mojave.",
        url = "https://www.youtube.com/results?search_query=kingman+arizona+bnsf+live+train+cam"
    ),
    RailcamEntry(
        id = "kansas_city",
        name = "Kansas City",
        location = "Kansas City, MO",
        railroad = "BNSF / UP / NS / CSX",
        subdivision = "Multiple mainlines",
        description = "One of the largest rail hubs in North America — nearly every Class I railroad passes through.",
        url = "https://www.youtube.com/results?search_query=kansas+city+railroad+live+train+cam"
    ),
    RailcamEntry(
        id = "ogden_ut",
        name = "Ogden",
        location = "Ogden, UT",
        railroad = "UP",
        subdivision = "UP Overland Route",
        description = "Historic transcontinental junction where the Golden Spike was driven in 1869 — heavy UP mainline traffic.",
        url = "https://www.youtube.com/results?search_query=ogden+utah+union+pacific+trains+live"
    ),
    RailcamEntry(
        id = "csx_cincinnati",
        name = "CSX Cincinnati",
        location = "Cincinnati, OH",
        railroad = "CSX",
        subdivision = "CSX Cincinnati Hub",
        description = "CSX gateway hub — intermodal, automotive, and mixed-freight trains through the Queen City.",
        url = "https://www.youtube.com/results?search_query=csx+cincinnati+live+train+cam"
    ),
    RailcamEntry(
        id = "selkirk_yard",
        name = "Selkirk Yard",
        location = "Selkirk, NY",
        railroad = "CSX",
        subdivision = "CSX Boston Line",
        description = "Largest rail yard in the northeast — CSX's main classification hub for New England traffic.",
        url = "https://www.youtube.com/results?search_query=selkirk+yard+csx+new+york+trains"
    ),
    RailcamEntry(
        id = "bnsf_barstow",
        name = "BNSF Barstow Yard",
        location = "Barstow, CA",
        railroad = "BNSF",
        subdivision = "BNSF Mojave Sub",
        description = "Key desert interchange yard on the BNSF Transcon — trains staging for the climb to Cajon Pass.",
        url = "https://www.youtube.com/results?search_query=bnsf+barstow+yard+trains+live"
    ),
    RailcamEntry(
        id = "corwith_yard",
        name = "BNSF Corwith Yard",
        location = "Chicago, IL",
        railroad = "BNSF",
        subdivision = "BNSF Chicago Terminal",
        description = "BNSF's major intermodal hub in Chicago — one of the highest-volume container terminals in the country.",
        url = "https://www.youtube.com/results?search_query=bnsf+corwith+yard+chicago+trains"
    ),
    RailcamEntry(
        id = "ns_crestline",
        name = "NS Crestline",
        location = "Crestline, OH",
        railroad = "NS",
        subdivision = "NS Ft. Wayne Line",
        description = "Busy NS diamond crossing and crew change point in north-central Ohio.",
        url = "https://www.youtube.com/results?search_query=norfolk+southern+crestline+ohio+trains"
    ),
    RailcamEntry(
        id = "up_cheyenne",
        name = "UP Cheyenne",
        location = "Cheyenne, WY",
        railroad = "UP",
        subdivision = "UP Overland Route",
        description = "Union Pacific's historic Cheyenne terminal — gateway to Sherman Hill and the Rocky Mountain climb.",
        url = "https://www.youtube.com/results?search_query=union+pacific+cheyenne+wyoming+live+trains"
    ),
    RailcamEntry(
        id = "cn_memphis",
        name = "CN Memphis Bridge",
        location = "Memphis, TN",
        railroad = "CN",
        subdivision = "CN Memphis Sub",
        description = "CN's crossing over the Mississippi River — a constant parade of manifest and intermodal trains.",
        url = "https://www.youtube.com/results?search_query=cn+railroad+memphis+bridge+trains"
    ),
    RailcamEntry(
        id = "strasburg_rr",
        name = "Strasburg Railroad",
        location = "Strasburg, PA",
        railroad = "Strasburg RR",
        subdivision = "Strasburg Branch",
        description = "America's oldest operating short line — live steam locomotives hauling passenger trains through Pennsylvania Dutch Country.",
        url = "https://www.strasburgrailroad.com"
    ),

    RailcamEntry(
        id = "decatur_ar",
        name = "Decatur",
        location = "Decatur, AR",
        railroad = "BNSF / CPKC",
        subdivision = "BNSF Tulsa Sub / CPKC Shreveport Sub",
        description = "Northwest Arkansas rail corridor — BNSF and CPKC freight traffic through the Ozark foothills.",
        url = "https://www.youtube.com/results?search_query=decatur+arkansas+railroad+trains"
    ),

    // ── Southwest ─────────────────────────────────────────────────────────────

    RailcamEntry(
        id = "colton_crossing",
        name = "Colton Crossing",
        location = "Colton, CA",
        railroad = "BNSF / UP",
        subdivision = "BNSF Transcon / UP Sunset Route",
        description = "One of the busiest at-grade rail diamonds in the US — BNSF Transcon crosses UP's Sunset Route with trains every few minutes.",
        url = "https://www.youtube.com/results?search_query=colton+crossing+bnsf+up+live+train+cam"
    ),
    RailcamEntry(
        id = "san_bernardino",
        name = "San Bernardino",
        location = "San Bernardino, CA",
        railroad = "BNSF / UP / Metrolink",
        subdivision = "BNSF Transcon / UP Sunset Route",
        description = "Major southwest junction where BNSF and UP mainlines converge alongside Metrolink commuter service.",
        url = "https://www.youtube.com/results?search_query=san+bernardino+bnsf+up+trains+live"
    ),
    RailcamEntry(
        id = "needles_ca",
        name = "Needles",
        location = "Needles, CA",
        railroad = "BNSF",
        subdivision = "BNSF Transcon / Needles Sub",
        description = "BNSF Transcon crossing the Colorado River into Arizona — relentless intermodal traffic through the Mojave Desert.",
        url = "https://www.youtube.com/results?search_query=needles+california+bnsf+transcon+trains"
    ),
    RailcamEntry(
        id = "flagstaff_az",
        name = "Flagstaff",
        location = "Flagstaff, AZ",
        railroad = "BNSF",
        subdivision = "BNSF Seligman Sub",
        description = "BNSF Transcon through the high desert at 7,000 ft elevation — trains battle grade through ponderosa pine country.",
        url = "https://www.youtube.com/results?search_query=flagstaff+arizona+bnsf+live+train+cam"
    ),
    RailcamEntry(
        id = "winslow_az",
        name = "Winslow",
        location = "Winslow, AZ",
        railroad = "BNSF",
        subdivision = "BNSF Seligman Sub",
        description = "Classic Route 66 desert town on the BNSF Transcon — high-volume intermodal and manifest freights through the Arizona high desert.",
        url = "https://www.youtube.com/results?search_query=winslow+arizona+bnsf+trains+live"
    ),
    RailcamEntry(
        id = "albuquerque_nm",
        name = "Albuquerque",
        location = "Albuquerque, NM",
        railroad = "BNSF",
        subdivision = "BNSF Transcon / Albuquerque Sub",
        description = "BNSF mainline through the Rio Grande valley — intermodal, manifest, and occasional Amtrak Southwest Chief.",
        url = "https://www.youtube.com/results?search_query=albuquerque+new+mexico+bnsf+trains+live"
    ),
    RailcamEntry(
        id = "belen_nm",
        name = "Belen",
        location = "Belen, NM",
        railroad = "BNSF",
        subdivision = "BNSF Belen Cut-off",
        description = "BNSF's Belen Cut-off junction — southern Transcon route bypassing the grades of Raton Pass via Lubbock.",
        url = "https://www.youtube.com/results?search_query=belen+new+mexico+bnsf+trains+live"
    ),
    RailcamEntry(
        id = "el_paso_tx",
        name = "El Paso",
        location = "El Paso, TX",
        railroad = "UP / BNSF",
        subdivision = "UP Sunset Route / BNSF Transcon",
        description = "Major international rail gateway on the US–Mexico border — UP and BNSF traffic plus cross-border interchange.",
        url = "https://www.youtube.com/results?search_query=el+paso+texas+union+pacific+bnsf+trains"
    ),
    RailcamEntry(
        id = "tucson_az",
        name = "Tucson",
        location = "Tucson, AZ",
        railroad = "UP",
        subdivision = "UP Sunset Route",
        description = "UP's Sunset Route through the Sonoran Desert — copper, intermodal, and mixed-freight trains between LA and New Orleans.",
        url = "https://www.youtube.com/results?search_query=tucson+arizona+union+pacific+trains+live"
    ),
    RailcamEntry(
        id = "yuma_az",
        name = "Yuma",
        location = "Yuma, AZ",
        railroad = "UP",
        subdivision = "UP Sunset Route",
        description = "UP crossing the Colorado River at Yuma — gateway between California and Arizona on the Sunset Route.",
        url = "https://www.youtube.com/results?search_query=yuma+arizona+union+pacific+trains+live"
    ),
    RailcamEntry(
        id = "tucumcari_nm",
        name = "Tucumcari",
        location = "Tucumcari, NM",
        railroad = "BNSF",
        subdivision = "BNSF Belen Cut-off",
        description = "Remote high-plains junction on the BNSF southern Transcon — trains rolling through New Mexico ranch country.",
        url = "https://www.youtube.com/results?search_query=tucumcari+new+mexico+bnsf+trains"
    ),
    RailcamEntry(
        id = "san_antonio_tx",
        name = "San Antonio",
        location = "San Antonio, TX",
        railroad = "UP / BNSF",
        subdivision = "UP Sunset Route / BNSF Belen Cut-off",
        description = "Major Texas rail hub where UP and BNSF routes converge — heavy manifest and intermodal traffic.",
        url = "https://www.youtube.com/results?search_query=san+antonio+texas+union+pacific+trains+live"
    )
)
