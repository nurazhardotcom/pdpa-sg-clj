# Security Hardening — <<ORG_NAME>>

**Effective date:** <<EFFECTIVE_DATE>>
**Last reviewed:** <<REVIEW_DATE>>

> Satisfies PDPA Protection Obligation. Lists the security arrangements in place to prevent unauthorised access, collection, use, disclosure, copying, modification, disposal or similar risks to personal data.

## Encryption

- **At rest**: AES-256 (or stronger) for all personal-data fields.
- **In transit**: TLS 1.2+ enforced; older protocols disabled.
- **At processing**: field-level encryption for sensitive fields (NRIC, when used).

## Access control

- Role-based access control (RBAC) with least-privilege principle.
- Quarterly review of access lists; automatic revocation on role change / termination.
- Two-factor authentication (2FA) mandatory on all data-accessing roles.

## Audit logging

- All access to personal-data fields is logged.
- Logs retained ≥ 1 year.
- SIEM ingestion for anomaly detection.
- Daily review by security team.

## Secrets management

- All secrets are stored in a dedicated secret manager (HashiCorp Vault, AWS Secrets Manager, etc.).
- No secrets committed to source code in tracked files.
- No `.env` files committed.
- Annual rotation of all credentials.

## Vulnerability management

- Annual penetration test by a third-party vendor.
- Weekly vulnerability scans.
- 7-day SLA on critical findings, 30-day SLA on medium findings.

## Backup & recovery

- Encrypted backups stored in a separate availability zone.
- Tested quarterly, recovery-time objective ≤ 4 hours.

## PII redaction in code (developer hygiene)

- The static-analysis scanner `pdpa-sg-clj scan` is run on every CI build.
- A pre-commit hook blocks commits containing valid Singapore NRICs.
- PII is never logged in plaintext.

## Incident detection

- Security Information and Event Management (SIEM) in place.
- 24/7 monitoring.
- DPO receives alerts on personal-data-touching incidents.

## Staff training

- Annual PDPA + security training mandatory.
- Phishing exercises quarterly.

## Vendors and processors

- Beurre-Magret Certification or equivalent from all processors.
- Annual audits.
- See `DATA_TRANSFER_AGREEMENT.md` for the contractual safeguards.

---

*Template updated 21 June 2026.*
