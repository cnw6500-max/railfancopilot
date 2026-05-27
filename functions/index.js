'use strict';

const functions = require('firebase-functions');
const fetch = require('node-fetch');
const admin = require('firebase-admin');

admin.initializeApp();

// ── Anthropic helper ──────────────────────────────────────────────────────────

async function callAnthropic(body) {
    const apiKey = process.env.ANTHROPIC_KEY;
    const resp = await fetch('https://api.anthropic.com/v1/messages', {
        method: 'POST',
        headers: {
            'x-api-key': apiKey,
            'anthropic-version': '2023-06-01',
            'Content-Type': 'application/json',
        },
        body: JSON.stringify(body),
    });
    if (!resp.ok) {
        const errText = await resp.text().catch(() => resp.status.toString());
        throw new functions.https.HttpsError('internal', `Anthropic error ${resp.status}: ${errText}`);
    }
    return resp.json();
}

// ── decodeTrainSymbol ─────────────────────────────────────────────────────────

exports.decodeTrainSymbol = functions.runWith({ secrets: ['ANTHROPIC_KEY'] }).https.onCall(async (data) => {
    const symbol = (data.symbol || '').trim().toUpperCase();
    const localContext = data.localContext || '(no local match found)';
    if (!symbol) throw new functions.https.HttpsError('invalid-argument', 'symbol is required');

    const systemPrompt = `You are an expert railfan and railroad operations specialist with deep knowledge of North American freight railroad train symbols and operations.

SYMBOL TYPE GUIDE (first letter):
A=Automotive, C=Coal, E=Equipment/Power Move, G=Grain, I=Intermodal,
M=Manifest, Z=Priority Intermodal, U=Bulk/Unit, Q=Tank/Liquid,
J=BNSF Interchange, L=Local, S=Special

UP CITY CODES: CH=Chicago, LA=Los Angeles, KC=Kansas City, NO=New Orleans,
PO=Portland, SE=Seattle, DN=Denver, SL=Salt Lake City, HO=Houston,
EP=El Paso, OM=Omaha, SX=Sioux City IA, CB=Council Bluffs IA,
NE=North Platte NE, LN=Lincoln NE, DM=Des Moines IA, RO=Roseville CA,
FW=Fort Worth TX, SA=San Antonio TX, TU=Tucson AZ, OG=Ogden UT

BNSF: Q/Z=Intermodal, H=Grain, M=Manifest
NS: Numbers only (11N, 24Q)
AMTRAK: Named trains + number

LOCAL DATABASE LOOKUP RESULT:
${localContext}

Use the local lookup data above as the primary source. Fill in schedule, consist, and additional operational details using your railroad knowledge.

Return ONLY valid JSON, no markdown, no code blocks:
{
  "symbol": "string",
  "type": "string",
  "origin": "string",
  "destination": "string",
  "schedule": "string",
  "typicalConsist": ["string"],
  "railroad": "string (BNSF/UP/CSX/NS/CN/CP/AMTRAK/KCS/OTHER)",
  "notes": "string",
  "priority": "string (High/Medium/Low)"
}`;

    const resp = await callAnthropic({
        model: 'claude-sonnet-4-6',
        max_tokens: 1024,
        system: systemPrompt,
        messages: [{ role: 'user', content: `Decode this train symbol: ${symbol}` }],
    });

    return { text: resp.content?.[0]?.text ?? '{}' };
});

// ── identifyLocomotive ────────────────────────────────────────────────────────

exports.identifyLocomotive = functions.runWith({ secrets: ['ANTHROPIC_KEY'] }).https.onCall(async (data) => {
    const base64Image = data.base64Image;
    if (!base64Image) throw new functions.https.HttpsError('invalid-argument', 'base64Image is required');

    const resp = await callAnthropic({
        model: 'claude-sonnet-4-6',
        max_tokens: 1024,
        system: `You are an expert railfan and locomotive identifier with deep knowledge of all North American locomotives — including Class I railroads, shortlines, regional railroads, private industrial operators, agricultural cooperatives, port authorities, mine railways, and museum/heritage railroads. You can identify diesel, electric, steam, and industrial switcher models from any era. When reading a photo, always read ALL visible text, logos, road numbers, and background signage before forming your answer — private and industrial operators often have their name painted directly on the unit or on nearby buildings. Never assume a locomotive belongs to a Class I railroad if the markings suggest otherwise. Be precise and only state what you can clearly see.`,
        messages: [{
            role: 'user',
            content: [
                {
                    type: 'image',
                    source: { type: 'base64', media_type: 'image/jpeg', data: base64Image },
                },
                {
                    type: 'text',
                    text: `Identify the locomotive(s) in this photo. Structure your response exactly as:

**Model:** [manufacturer and model — include industrial switchers, e.g. GE 44-ton, EMD SW1500, Plymouth ML-8, Trackmobile, etc.]
**Owner/Railroad:** [exact name shown on the locomotive or visible signage — this may be a private company, agricultural cooperative, industrial facility, shortline, or Class I railroad. Read any lettering or logos carefully.]
**Road Number:** [number if visible, or "Not visible"]
**Type:** [one of: Industrial Switcher / Yard Switcher / Road Switcher / Road Locomotive / Narrow Gauge / Other]
**Era:** [approximate decade(s) built, e.g. 1950s–1960s]
**Details:** [2–3 sentences on paint scheme, body style, notable features, and operator type — e.g. private industrial, shortline, Class I railroad]

If the image does not show a locomotive clearly, respond only with: "No locomotive clearly visible in this photo."
Read all visible text, logos, and signage in the image carefully before answering — the owner name is often painted directly on the unit. Do not default to Class I railroad names if the markings show otherwise.`,
                },
            ],
        }],
    });

    return { text: resp.content?.[0]?.text ?? 'Unable to identify locomotive.' };
});

// ── analyzeConsist ────────────────────────────────────────────────────────────

exports.analyzeConsist = functions.runWith({ secrets: ['ANTHROPIC_KEY'] }).https.onCall(async (data) => {
    const base64Image = data.base64Image;
    if (!base64Image) throw new functions.https.HttpsError('invalid-argument', 'base64Image is required');

    const resp = await callAnthropic({
        model: 'claude-sonnet-4-6',
        max_tokens: 2048,
        system: `You are an expert railfan with encyclopedic knowledge of North American locomotive models and freight car types. You identify consists precisely — listing each unit from front to back with model, road number, railroad, and notable features. You know the difference between an ES44AC and an ES44DC, between a 5-bay covered hopper and a 3-bay, between a 53-foot well car and a 48-foot platform. You read every visible number, logo, and marking before answering.`,
        messages: [{
            role: 'user',
            content: [
                {
                    type: 'image',
                    source: { type: 'base64', media_type: 'image/jpeg', data: base64Image },
                },
                {
                    type: 'text',
                    text: `Analyze this consist photo. List every unit you can identify from front to back.

For each unit use this format:
**[N]. [Model]**
- Railroad: [name shown on unit]
- Road #: [number if visible, or "Not visible"]
- Type: [Locomotive / Boxcar / Tank Car / Hopper / Gondola / Flatcar / Intermodal Well Car / Caboose / Other]
- Notes: [paint scheme, heritage livery, condition, special features]

Finish with a one-line consist summary, e.g.:
**Consist:** 2× BNSF ES44AC + 1× BNSF C44-9W + 94 grain hoppers (mixed reporting marks)

If the consist extends out of frame, note it. If a unit is partially obscured, describe what you can. If no train is clearly visible, say so.`,
                },
            ],
        }],
    });

    return { text: resp.content?.[0]?.text ?? 'Unable to analyze consist.' };
});

// ── GTFS-RT helpers ───────────────────────────────────────────────────────────

async function fetchGtfsRt(url, headers) {
    const resp = await fetch(url, { headers: { 'User-Agent': 'RailfanCopilot/1.0', ...headers } });
    if (!resp.ok) {
        throw new functions.https.HttpsError('internal', `GTFS-RT fetch error ${resp.status} from ${url}`);
    }
    const buffer = await resp.buffer();
    return { data: buffer.toString('base64') };
}

// ── getMetraPositions ─────────────────────────────────────────────────────────

exports.getMetraPositions = functions.runWith({ secrets: ['METRA_USER'] }).https.onCall(async () => {
    const credentials = Buffer.from(`${process.env.METRA_USER}:`).toString('base64');
    return fetchGtfsRt(
        'https://gtfspublic.metrarr.com/gtfs/public/positions',
        { Authorization: `Basic ${credentials}` }
    );
});

// ── getMtaLirrPositions ───────────────────────────────────────────────────────

exports.getMtaLirrPositions = functions.https.onCall(async () => {
    return fetchGtfsRt(
        'https://api-endpoint.mta.info/Dataservice/mtagtfsfeeds/lirr%2Fgtfs-lirr',
        {}
    );
});

// ── getMtaMetroNorthPositions ─────────────────────────────────────────────────

exports.getMtaMetroNorthPositions = functions.https.onCall(async () => {
    return fetchGtfsRt(
        'https://api-endpoint.mta.info/Dataservice/mtagtfsfeeds/mnr%2Fgtfs-mnr',
        {}
    );
});

// ── getCaltrainPositions ──────────────────────────────────────────────────────

exports.getCaltrainPositions = functions.https.onCall(async () => {
    return fetchGtfsRt(
        'https://api.511.org/Transit/VehiclePositions?agency=CT',
        {}
    );
});

// ── Heritage unit reference data (mirrors HeritageData.kt) ───────────────────
// Key: "RAILROAD_ROADNUMBER" (uppercase).  Value: scheme display name.
const HERITAGE_MAP = {
    'UP_844':   'UP Northern Steam',      'UP_3985':  'UP Challenger Steam',
    'UP_4014':  'UP Big Boy',             'UP_4141':  'Bush 41 Presidential',
    'UP_1983':  'Reagan Presidential',    'UP_1969':  'Nixon Presidential',
    'UP_1976':  'Spirit of the Union Pacific',
    'BNSF_100': 'ATSF Warbonnet',         'BNSF_700': 'GN Empire Builder',
    'BNSF_1521':'NP Monad',               'BNSF_2194':'CB&Q Chinese Red',
    'BNSF_2650':'FW&D Mineral Red',       'BNSF_3140':'SF&SF Mustard Yellow',
    'BNSF_4059':'BN Cascade Green',
    'NS_8025':  'NS Pennsylvania RR',     'NS_8026':  'NS Norfolk & Western',
    'NS_8027':  'NS Southern Railway',    'NS_8028':  'NS New York Central',
    'NS_8029':  'NS Virginian Railway',   'NS_8030':  'NS Erie Lackawanna',
    'NS_8031':  'NS Reading Company',     'NS_8032':  'NS Western Maryland',
    'NS_8033':  'NS Lehigh Valley',       'NS_8034':  'NS Central of Georgia',
    'NS_8035':  'NS Monon Railroad',      'NS_8036':  'NS Wabash Railroad',
    'NS_8037':  'NS Southern Railway — Original', 'NS_8038': 'NS Nickel Plate Road',
    'NS_8039':  'NS N&W Original',        'NS_8040':  'NS Conrail',
    'NS_8100':  'NS 21C Spirit',
    'CSX_911':  'CSX Spirit of Louisville','CSX_1776': 'CSX Spirit of America',
    'CPKC_4062':'CP Beaver Logo Heritage','CPKC_7021':'KCS Southern Belle Heritage',
    'CPKC_7022':'MKT Katy Heritage',
};

// Returns the first matching HeritageUnit or null.
// Checks "RAILROAD_NUMBER" and then any railroad prefix for well-known steam numbers.
function matchHeritage(railroad, roadNumbers) {
    const rr = (railroad || '').toUpperCase().replace(/[^A-Z0-9]/g, '');
    for (const rn of roadNumbers) {
        const n = rn.toUpperCase().trim();
        const withRr  = `${rr}_${n}`;
        const withUp  = `UP_${n}`;
        // Check exact railroad match first, then try all keys ending in _NUMBER
        if (HERITAGE_MAP[withRr]) return { roadNumber: rn, schemeName: HERITAGE_MAP[withRr] };
        // Common steam units known by number alone regardless of reporting railroad
        if (HERITAGE_MAP[withUp] && ['844','3985','4014'].includes(n))
            return { roadNumber: rn, schemeName: HERITAGE_MAP[withUp] };
    }
    return null;
}

// ── heritageUnitAlert ─────────────────────────────────────────────────────────
// Fires on every new sighting. If the consist contains a known heritage road
// number, writes an alert to rail_alerts and (if reporter is trusted) pushes FCM.

exports.heritageUnitAlert = functions.firestore
    .document('sightings/{sightingId}')
    .onCreate(async (snap) => {
        const data    = snap.data();
        const db      = admin.firestore();
        const messaging = admin.messaging();

        // ── 1. Resolve reporter score ─────────────────────────────────────────
        const reporterUid = data.reporterUid || null;
        let reporterScore = 0;
        if (reporterUid) {
            const userDoc = await db.collection('users').doc(reporterUid).get();
            reporterScore = userDoc.exists ? (userDoc.data().reporterScore || 0) : 0;
        }

        // ── 2. Extract road numbers from consist JSON or free-text notes ──────
        const roadNumbers = [];
        try {
            const consist = JSON.parse(data.consist || '[]');
            if (Array.isArray(consist)) {
                consist.forEach(e => { if (e.roadNumber) roadNumbers.push(e.roadNumber); });
            }
        } catch (_) {}
        // Also scan notes for standalone 3-5 digit numbers
        const notesMatches = (data.notes || '').match(/\b\d{3,5}\b/g) || [];
        roadNumbers.push(...notesMatches);

        if (roadNumbers.length === 0) return null;

        // ── 3. Heritage match ─────────────────────────────────────────────────
        const matched = matchHeritage(data.railroad || '', roadNumbers);
        if (!matched) return null;

        const isVerified = reporterScore >= 3;
        const location   = data.location || 'Unknown location';
        const reporter   = data.reporterName || 'A railfan';

        // ── 4. Write to rail_alerts ───────────────────────────────────────────
        await db.collection('rail_alerts').add({
            type:         'HERITAGE_UNIT',
            title:        `Heritage Unit: ${matched.schemeName}`,
            message:      `#${matched.roadNumber} reported by ${reporter} near ${location}`,
            timestampMs:  Date.now(),
            latitude:     data.latitude  || null,
            longitude:    data.longitude || null,
            trainSymbol:  data.trainSymbol || null,
            locoNumber:   matched.roadNumber,
            heritageName: matched.schemeName,
            isVerified,
            reporterScore,
            sightingId:   snap.id,
        });

        // ── 5. Push to opted-in devices only if reporter is trusted ───────────
        if (!isVerified) return null;

        const usersSnap = await db.collection('users')
            .where('fcmToken', '!=', '')
            .limit(500)
            .get();

        const tokens = usersSnap.docs.map(d => d.data().fcmToken).filter(Boolean);
        if (tokens.length === 0) return null;

        await Promise.all(tokens.map(token =>
            messaging.send({
                token,
                notification: {
                    title: `🏆 Heritage Spotted: ${matched.schemeName}`,
                    body:  `#${matched.roadNumber} near ${location}`,
                },
                android: { priority: 'high', notification: { channelId: 'heritage_alerts' } },
                apns:    { payload: { aps: { sound: 'default' } } },
            }).catch(() => null)
        ));

        return null;
    });

// ── onSightingUpvoted ─────────────────────────────────────────────────────────
// Called by the Android app when a user upvotes a sighting.
// Increments the original reporter's score in users/{uid}.

exports.onSightingUpvoted = functions.https.onCall(async (data) => {
    const { sightingId } = data;
    if (!sightingId) throw new functions.https.HttpsError('invalid-argument', 'sightingId required');

    const db = admin.firestore();
    const sightingDoc = await db.collection('sightings').doc(sightingId).get();
    if (!sightingDoc.exists) return { ok: false };

    const reporterUid = sightingDoc.data().reporterUid;
    if (!reporterUid) return { ok: false };

    await db.collection('users').doc(reporterUid).set(
        { reporterScore: admin.firestore.FieldValue.increment(1) },
        { merge: true }
    );
    return { ok: true };
});

// ── watchlistSightingAlert ────────────────────────────────────────────────────
// Fires when any new sighting is written to the "sightings" collection.
// Scans all users' watchlist subcollections for matching symbols or road numbers,
// then sends an FCM push to each matched user's device.

exports.watchlistSightingAlert = functions.firestore
    .document('sightings/{sightingId}')
    .onCreate(async (snap) => {
        const data = snap.data();
        const symbol = (data.trainSymbol || '').toUpperCase().trim();
        const notes  = (data.notes || '').toUpperCase();
        const location = data.location || 'Unknown location';
        const reporter = data.reporterName || 'A railfan';

        if (!symbol && !notes) return null;

        const db = admin.firestore();
        const messaging = admin.messaging();

        // Query watchlist entries across all users using a collection group query.
        // Two passes: one for SYMBOL matches, one for LOCO keyword matches.
        const matchedTokens = new Set();

        // Pass 1: exact symbol match
        if (symbol) {
            const symbolSnap = await db.collectionGroup('watchlist')
                .where('type', '==', 'SYMBOL')
                .where('value', '==', symbol)
                .get();

            for (const entryDoc of symbolSnap.docs) {
                // Parent path: users/{uid}/watchlist/{entryId}
                const uid = entryDoc.ref.parent.parent.id;
                const userDoc = await db.collection('users').doc(uid).get();
                const token = userDoc.exists && userDoc.data().fcmToken;
                if (token) matchedTokens.add(token);
            }
        }

        // Pass 2: road number keyword match in notes or symbol
        const locoSnap = await db.collectionGroup('watchlist')
            .where('type', '==', 'LOCO')
            .get();

        for (const entryDoc of locoSnap.docs) {
            const entryData = entryDoc.data();
            const watchedNum = (entryData.value || '').toUpperCase().trim();
            const watchedRR  = (entryData.railroad || '').toUpperCase().trim();
            if (!watchedNum) continue;

            const inSymbol = symbol.includes(watchedNum);
            const inNotes  = notes.includes(watchedNum);
            const rrMatch  = !watchedRR || notes.includes(watchedRR) || symbol.includes(watchedRR);

            if ((inSymbol || inNotes) && rrMatch) {
                const uid = entryDoc.ref.parent.parent.id;
                const userDoc = await db.collection('users').doc(uid).get();
                const token = userDoc.data && userDoc.data() && userDoc.data().fcmToken;
                if (token) matchedTokens.add(token);
            }
        }

        if (matchedTokens.size === 0) return null;

        const title = symbol
            ? `🔍 ${symbol} spotted!`
            : `🔍 Watchlist match`;
        const body = `${reporter} spotted it at ${location}`;

        // Send individually so one bad token doesn't block others
        const sends = [...matchedTokens].map(token =>
            messaging.send({
                token,
                notification: { title, body },
                android: { priority: 'high', notification: { channelId: 'watchlist_alerts' } },
                apns: { payload: { aps: { sound: 'default' } } },
            }).catch(() => null)  // ignore invalid/expired tokens
        );

        await Promise.all(sends);
        return null;
    });
