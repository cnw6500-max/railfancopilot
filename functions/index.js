'use strict';

const functions = require('firebase-functions');
const fetch = require('node-fetch');

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
