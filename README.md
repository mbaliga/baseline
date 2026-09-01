# Baseline

Baseline is the local personal evidence engine shared by Crocodyl, Ebbflow, and future apps.
It preserves a temporal evidence graph, provenance, time resolution, contextual baselines,
experiments, and evidence-ranked explanations. Source apps remain authoritative for what they
directly observed.

## Distribution

- **Community:** open contracts, deterministic local ledger, provenance, contextual summaries,
  experiment logging, export, deletion, and safety-critical behavior.
- **Advanced:** optional proprietary causal discovery, forecasting, counterfactual simulation,
  and cross-domain recommendations.

Every open consumer must build and remain useful without Advanced. Advanced may not gate safety,
export, retraction, or athlete history.

## Modules

- `baseline-contracts`: stable evidence/event types and resolution/evidence laws.
- `baseline-ledger`: append-only local evidence ledger and retractions.
- `baseline-stats`: honest descriptive/adjusted analysis acceptance fixtures.

See [`ARCHITECTURE_V2.md`](ARCHITECTURE_V2.md) and
[`docs/PR_DISPOSITION.md`](docs/PR_DISPOSITION.md).
