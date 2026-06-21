# Data Transfer Agreement — <<ORG_NAME>>

**Effective date:** <<EFFECTIVE_DATE>>
**Purpose:** Document PDPA §26 Transfer Limitation compliance for each overseas recipient.

## Recipient: `<<RECIPIENT_NAME>>`

| | |
|---|---|
| Country | `<<RECIPIENT_COUNTRY>>` |
| Service | `<<RECIPIENT_SERVICE>>` |
| Data categories transferred | `<<DATA_CATEGORIES>>` |
| Number of records (annual estimate) | `<<RECORD_VOLUME>>` |
| Start date | `<<TRANSFER_START_DATE>>` |

## Mechanism (pick at least one)

This recipient receives our data under the following §26 mechanism:

- [ ] **APEC Cross-Border Privacy Rules (CBPR) Certification**
- [ ] **APEC Privacy Recognition for Processors (PRP) Certification**
- [ ] **EU Standard Contractual Clauses (SCCs)** — modules: `<<SCC_MODULES>>`
- [ ] **Singapore PDPC Binding Corporate Rules**
- [ ] **PDPC-approved code of practice** — see https://www.pdpc.gov.sg
- [ ] **PDPC whitelisted country**

## Contractual safeguards (additional)

Beyond the chosen mechanism, the recipient is contractually bound by `<<ORG_NAME>>` to:

- Use the data solely for the documented purpose
- Apply encryption (AES-256 minimum) at rest
- Implement role-based access control equivalent to SG practice
- Honour all DSR pass-through requests within 7 days
- Notify `<DPO_EMAIL>` immediately on any sub-incident
- Submit to audit by `<ORG_NAME>>` annually
- Delete or anonymise when termination clause triggers

## Risk assessment

Factors considered before authorising this transfer:

- Recipient's jurisdiction's data-protection laws — `<<LINK>>`
- Recipient's data-handling certifications — `<<LINK>>`
- Nature and sensitivity of data categories
- Volume of records
- Probability of breach (recipient's track record)
- Availability and integrity of remedies in recipient jurisdiction

## Termination

On the earlier of:
1. Recipient loses its mechanism certification
2. Recipient breaches the contract substantively
3. Recipient's jurisdiction passes a law impairing the protection

— we will **suspend or terminate the transfer within 48 hours** and notify PDPC if records have already been transferred.

## Audit cycle

- Annual review of mechanism validity
- Annual review of recipient's breach history
- Quarterly sample audit of transferred data flows

---

*Compliant with §26 Transfer Limitation Obligation as of 21 June 2026.*
