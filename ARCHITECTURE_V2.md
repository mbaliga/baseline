# Baseline architecture v2

## Product boundary

Baseline is a substrate, not another athlete or EEG UI. Crocodyl owns archery interpretation;
Ebbflow owns EEG acquisition and EEG-specific quality/features. Baseline links their evidence,
preserving provenance, uncertainty, time resolution, and alternative explanations.

## Data law

Apps append observations or versioned derived measures. Inferences are separate records that
reference their evidence. No model overwrites an observation or silently promotes a claim.

Resolution moves only right:

`RAW_SAMPLE -> WINDOW -> EVENT -> SESSION -> DAY -> PERIOD`

Every record has stable identity, subject/source identity, observation/effective/ingestion time,
timezone, resolution, source/app/device/version, raw/derived/inferred/declared classification,
measurement definition and units, quality, uncertainty, privacy class, schema version, content
hash, and optional supersession/retraction links.

## Deployment

- Embedded Community library for phone-only operation.
- Optional personal node for longer history and heavier models.

The phone remains authoritative for records it created until explicit mutually authenticated,
end-to-end encrypted local sync. Failure to reach the node never blocks capture or training.

## Evidence language

| Grade | Meaning | Maximum wording |
|---|---|---|
| E0 | observation | Recorded… |
| E1 | association | Associated with… |
| E2 | adjusted association | Still associated after accounting for… |
| E3 | temporal holdout prediction | Helps predict… |
| E4 | justified quasi-experiment | Evidence suggests an effect… |
| E5 | adequate randomized N-of-1 | Likely caused an average change of… |

An LLM or presentation layer cannot promote a claim above the estimator's grade.
