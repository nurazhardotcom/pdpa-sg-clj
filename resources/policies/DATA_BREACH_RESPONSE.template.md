# Data Breach Response Plan — <<ORG_NAME>>

**Effective date:** <<EFFECTIVE_DATE>>
**Last reviewed:** <<REVIEW_DATE>>

> This plan satisfies Singapore PDPA's Data Breach Notification Obligation: notify PDPC within **3 calendar days** of assessment, notify affected individuals within 30 days, when a breach causes significant harm OR affects ≥ 500 individuals.

## 4-hour detection → containment timeline

| Hour | Action | Owner |
|---|---|---|
| **0:00** | Anomaly detected (alert, ticket, or external report) | sec.oncall |
| **0:30** | Severity classification: `LOW (no PDPA report)`, `MEDIUM (PDPC report necessary)`, `HIGH (also notify individuals)` | DPO + CTO |
| **1:00** | Containment — block source of breach, rotate compromised credentials | sec.oncall |
| **4:00** | Initial impact assessment: scope of data, number of records, identity categories | DPO |
| **24:00** | Full forensic analysis begun; DPO assesses the "significant harm" test (above 500 affected OR potential harm to rights/loss of dignity) | DPO |
| **48:00** | PDPC submission prepared (`Breach Notification Form` on PDPC portal) | DPO |
| **72:00** | **PDPC notified** (3 calendar day clock) — unless LOW | DPO |

## Sub-plan A: PDPC notification (≥ 500 affected)

If the breach affects **500 or more** individuals, OR causes significant harm, file with PDPC within **3 calendar days** of assessment.

1. Visit the PDPC Notification Portal (https://www.pdpc.gov.sg)
2. Provide:
   - Date and time of breach
   - Categories and number of affected individuals
   - Likely consequences
   - Categories and number of affected records
   - Mitigation measures planned and taken
3. Submit; PDPC acknowledgment is by email

## Sub-plan B: Affected-individual notification (≤ 30 days)

If the breach causes significant harm to individuals, OR sensitive data was exposed, notify them via `<NOTIFICATION_CHANNEL>` within **30 days**.

This message must include:
- Description of the breach in plain language
- Categories of data involved
- Likely consequences
- Mitigation steps we are taking
- Recommended steps they can take (e.g. password resets, suspicious activity monitoring)
- Contact details for further information

## Roles

- **DPO** — single accountable point of contact; owns regulatory submission
- **CTO** — owns technical containment
- **CEO / Senior Management** — owns public communications
- **Legal** — owns contractual notifications to processors (§26)
- **PR / Comms** — owns media handling (only after DPO/CEO sign-off)

## Tabletop exercise (annual)

Run a simulated breach once a year:
- Random date, after-hours start
- Cross-functional team rehearses the 0/0.5/1/4 hour steps
- After-action review captures gaps; updates to this plan are MANDATORY

## Past incidents

| Date | Description | Resolution |
|---|---|---|
| `<<PAST_INCIDENT_1>>` | `<<DESC_1>>` | `<<RES_1>>` |

---

*Template compliant with PDPA as of 21 June 2026. Rule version stamp tracked by `pdpa-sg-clj`.*
