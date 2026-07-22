# SABR compatibility profiles

SABR compatibility profiles describe volatile WEB and MWEB protocol layouts. They are data, not
programs. PipePipeExtractor owns the native engine, validates every profile value, and exposes only
predefined sources, normalized response targets, predicates, and actions.

Profiles cannot perform network requests, read arbitrary process state, inspect media payloads,
access cookies or PO tokens directly, allocate unbounded state, define functions, or execute code.
The Host still owns transport, URL validation, request limits, tokens, cookies, UMP framing, media
assembly, retries, generations, seek, cache, and download behavior.

## Document

The UTF-8 JSON envelope is strict: malformed UTF-8 plus unknown and missing properties are
rejected. The maximum size is 1 MiB, nesting depth is 16, and structural token count is 8192.

```json
{
  "format": 1,
  "revision": 12,
  "validFromMs": 1784678400000,
  "validUntilMs": 1792454400000,
  "minimumExtractorRevision": 1,
  "capabilities": [
    "request-template-v1",
    "response-schema-v1",
    "recovery-rules-v1"
  ],
  "clients": {
    "MWEB": {
      "mediaParts": {"header": 20, "payload": 21, "end": 22},
      "initialRequest": [
        {"field": 1, "wireType": "BYTES", "source": "CLIENT_ABR_STATE",
          "required": true},
        {"field": 2, "wireType": "BYTES", "source": "SELECTED_FORMATS",
          "required": false},
        {"field": 3, "wireType": "BYTES", "source": "BUFFERED_RANGES",
          "required": false},
        {"field": 4, "wireType": "VARINT", "source": "PLAYER_TIME_MS",
          "required": false},
        {"field": 5, "wireType": "BYTES", "source": "USTREAMER_CONFIG",
          "required": true},
        {"field": 16, "wireType": "BYTES", "source": "PREFERRED_AUDIO_FORMATS",
          "required": false},
        {"field": 17, "wireType": "BYTES", "source": "PREFERRED_VIDEO_FORMATS",
          "required": false},
        {"field": 19, "wireType": "BYTES", "source": "CLIENT_CONTEXT",
          "required": true}
      ],
      "followingRequest": [
        {"field": 1, "wireType": "BYTES", "source": "CLIENT_ABR_STATE",
          "required": true},
        {"field": 2, "wireType": "BYTES", "source": "SELECTED_FORMATS",
          "required": false},
        {"field": 3, "wireType": "BYTES", "source": "BUFFERED_RANGES",
          "required": false},
        {"field": 4, "wireType": "VARINT", "source": "PLAYER_TIME_MS",
          "required": false},
        {"field": 5, "wireType": "BYTES", "source": "USTREAMER_CONFIG",
          "required": true},
        {"field": 16, "wireType": "BYTES", "source": "PREFERRED_AUDIO_FORMATS",
          "required": false},
        {"field": 17, "wireType": "BYTES", "source": "PREFERRED_VIDEO_FORMATS",
          "required": false},
        {"field": 19, "wireType": "BYTES", "source": "CLIENT_CONTEXT",
          "required": true}
      ],
      "responseMappings": [
        {"partType": 35, "target": "NEXT_REQUEST.BACKOFF_MS", "path": [4],
          "wireType": "VARINT", "required": false},
        {"partType": 20, "target": "MEDIA_HEADER.SEQUENCE", "path": [9],
          "wireType": "VARINT", "required": true}
      ],
      "recovery": {
        "maximumOmissions": 3,
        "maximumElapsedMs": 15000,
        "forwardThresholdMs": 30000,
        "retryDelayMs": 0
      },
      "rules": [
        {"whenAll": ["HAS_ERROR"], "actions": ["FAIL_SESSION"]},
        {"whenAll": ["RELOAD_REQUESTED"], "actions": ["TRY_RELOAD"]},
        {"whenAll": ["HAS_PROTECTION_BOUNDARY", "FETCH_SEGMENT_MODE"],
         "actions": ["REQUIRE_PO_TOKEN", "RETRY"]},
        {"whenAll": ["HAS_PROTECTION_BOUNDARY", "PUMP_MODE"],
         "actions": ["REFRESH_PO_TOKEN", "CONTINUE"]},
        {"whenAll": ["HAS_REDIRECT"], "actions": ["APPLY_REDIRECT", "CONTINUE"]},
        {"whenAll": [], "actions": ["CONTINUE"]}
      ]
    }
  },
  "keyId": "current",
  "signature": "base64-ed25519-signature"
}
```

The Ed25519 signature covers the deterministic binary serialization produced by
`SabrCompatibilityProfile.serialize()`, not the JSON whitespace or property order. A profile is
accepted only for its validity interval, supported format and capabilities, compatible Extractor
revision, embedded signing key, and positive monotonic revision.

The canonical payload writes the public client, wire-type, source, target, predicate, and action
identifiers as UTF-8 strings. It never depends on Java enum ordinals, so adding or reordering an
internal enum constant cannot silently change an existing signature payload.

## Request templates

Every field has a protobuf number, a matching `VARINT` or `BYTES` wire type, an enum source, and a
required flag. Initial and following templates are independent and contain at most 64 fields.
Supported sources are:

- `PLAYER_TIME_MS`
- `BUFFERED_RANGES`
- `PLAYBACK_COOKIE`
- `PO_TOKEN`
- `SELECTED_FORMATS`
- `CLIENT_CONTEXT`
- `CLIENT_ABR_STATE`
- `USTREAMER_CONFIG`
- `PREFERRED_AUDIO_FORMATS`
- `PREFERRED_VIDEO_FORMATS`

The profile receives the encoded value selected by the Host; it never receives an accessor for the
underlying secret or mutable stream state. The generated request remains subject to the Host's
256 KiB limit.

## Response mappings

A mapping identifies a UMP part type, a protobuf field path of at most eight elements, an expected
wire type, and one normalized target. When its UMP part is present, a missing required value fails
the profile and triggers the circuit breaker. Optional missing values leave the native value
unchanged.

The native media-header baseline ignores known field numbers whose wire type no longer matches the
bundled schema before applying profile mappings. This lets a profile deliberately reuse an old
field number with a new protobuf wire type without failing in the baseline decoder first.

Targets cover next-request timing and playback state, redirects, errors, reload and protection
signals, plus all normalized media-header properties. Media payload and media-end parts cannot be
inspected by mappings. Media header, payload, and end UMP types are selected once per session and
the payload continues through the native streaming collector without JSON or policy evaluation.

## Recovery and behavior

Recovery values are bounded by native limits: at most 16 omissions, 120 seconds elapsed, 300
seconds forward threshold, and 5 seconds retry delay. Rules contain only known predicates and
actions, have no loops, and must end in exactly one terminal action. A final unconditional
`CONTINUE` rule is mandatory.

The Host validates every decision again before applying it. Redirects, token refreshes, retries,
backoff, reloads, generations, request count, and all other hard limits remain native invariants.

## Compatibility boundary

A profile can adapt known request fields, response paths, UMP media part types, recovery thresholds,
and combinations of predefined actions. A new transport, cryptographic primitive, token flow,
media framing model, or Host capability is structural and requires an Extractor release.

A document may carry only WEB or only MWEB. Sessions for a client absent from that valid document
stay on the bundled native policy without disabling the profile for the client it does cover.
