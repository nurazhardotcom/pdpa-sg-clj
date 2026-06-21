# Personal Data Retention Schedule — <<ORG_NAME>>

**Effective date:** <<EFFECTIVE_DATE>>
**Last reviewed:** <<REVIEW_DATE>>

> Satisfies PDPA Retention Limitation Obligation. Stop retaining personal data (or remove the means by which it can be associated with individuals) when the purpose is met.

## Schedule

| Data category | Purpose | Maximum retention | Anonymisation trigger | Auto-purge? |
|---|---|---|---|---|
| User account data | Service delivery | `<<RETENTION_USER>>` years after last login | Account deletion | Yes |
| Order / transaction records | Legal accounting | `<<RETENTION_TX>>` years | 7-year mark | No (legal hold) |
| Marketing consent | Marketing | Until withdrawal OR 24 months inactive | Withdrawal | Yes |
| Customer support tickets | Quality + service | `<<RETENTION_TICKET>>` years after close | Close + 1 year | Yes |
| Server access logs | Security | 1 year, then aggregated | 1 year | Rolling |
| Backup snapshots | Disaster recovery | 30 days rolling | 30 days | Yes |

## Anonymisation vs. deletion

When the retention period ends:

- **Identify-then-anonymise** flows: replace identifying fields with random tokens; aggregate where possible.
- **Hard-delete** flows: physical file deletion + cryptographic shredding for encrypted backups.

## Reviewers

- Each line of business owns its row in the schedule.
- DPO reviews quarterly.
- Annual publication to users via PRIVACY_POLICY.

## Audit trail

Any early-purge or hold-extensions are recorded in the audit log.

---

*Template updated 21 June 2026.*
