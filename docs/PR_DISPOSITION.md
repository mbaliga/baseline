# Legacy PR disposition

| PR | Disposition | Reason / preserved seam |
|---|---|---|
| #3 Android MW75 app | **Port acquisition to Ebbflow; do not merge app into Baseline.** | Packet framing and documented device constants move to Ebbflow Community. BLE/RFCOMM/Room/Compose remain Ebbflow-owned. The placeholder focus estimator is not shipped as truth. |
| #4 sport engine | **Port concepts and tests; superseded as engine.** | Welford/personal-baseline seams remain useful, but the small correlation engine cannot serve as the temporal graph or causal layer. |
| #5 archery IMU | **Park research code; do not wire or merge.** | Crocodyl owns archery biomechanics. Production Steady Aim integration requires an authorized protocol/export and validation; no reverse-engineered production BLE. |
| #8 proprietary flip | **Superseded by Community + Advanced split.** | Preserves commercial Advanced differentiation without making open apps unbuildable or gating safety/export/history. |

Branches remain historical evidence until the corresponding ports land. Their app surfaces are not
canonical architecture and should be closed with a pointer to this document after review.
