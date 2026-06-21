# Privacy Policy — <<ORG_NAME>>

**Effective date:** <<EFFECTIVE_DATE>>
**Last reviewed:** <<EFFECTIVE_DATE>>

> This Privacy Policy is published under the Singapore Personal Data Protection Act 2012 ("PDPA"). It explains what personal data <<ORG_NAME>> collects, why, how long we keep it, who we share it with, and how to reach our Data Protection Officer.

## 1. What we collect

`<<ORG_NAME>>` collects the following categories of personal data:

- Identity data: `<<LIST_IDENTITY>>` (e.g. name, business contact details)
- Transaction data: `<<LIST_TRANSACTION>>` (e.g. order IDs, timestamps)
- Technical data: `<<LIST_TECH>>` (e.g. IP address, browser type, device)

We **do not collect** Singapore NRIC numbers for routine identification. Where NRIC is strictly required by another Singapore law, we apply the PDPC Safe NRIC controls (masking, encryption, no authentication reuse).

## 2. Why we collect it (purposes)

| Purpose | Lawful basis | Retention |
|---|---|---|
| `<<PURPOSE_1>>` | Consent | `<<RETENTION_PURPOSE_1>>` |
| `<<PURPOSE_2>>` | Consent | `<<RETENTION_PURPOSE_2>>` |
| `<<PURPOSE_3>>` | Contract performance | `<<RETENTION_PURPOSE_3>>` |

We do **not** use your personal data for AI/ML model training without separate opt-in consent.

## 3. Who we share it with

- Service providers: `<<LIST_PROCESSORS>>`
- Overseas recipients: `<<LIST_OVERSEAS>>` (each covered by a §26 Transfer Limitation mechanism — see our [DATA_TRANSFER_AGREEMENT.md](DATA_TRANSFER_AGREEMENT.md))
- Government / regulators: only when **legally required**.

## 4. How long we keep it

We retain your personal data for as long as the stated purpose is active. After that it is either deleted or fully anonymised. See our [RETENTION_SCHEDULE.md](RETENTION_SCHEDULE.md) for category-by-category timelines.

## 5. Your rights

You may, at any time:

| Right | How to exercise |
|---|---|
| **Access** your data | `GET /api/dsr/export` or email `<<DPO_EMAIL>>` |
| **Correct** your data | `POST /api/dsr/rectify` or email `<<DPO_EMAIL>>` |
| **Withdraw consent** | `POST /api/consent/withdraw` or click "Withdraw" in your dashboard |
| **Lodge a complaint** with PDPC | https://www.pdpc.gov.sg/complaints |

We will respond within **30 calendar days** (extendable to 60 with notice).

## 6. Security (Protection Obligation)

- TLS ≥ 1.2 in transit
- AES-256 encryption at rest for personal-data fields
- Role-based access control with quarterly review
- Audit logs retained for at least 1 year
- Annual penetration testing

See our [SECURITY_HARDENING.md](SECURITY_HARDENING.md) for technical detail.

## 7. Data breach handling

If a breach occurs causing significant harm to you, or affecting ≥ 500 individuals, we notify the PDPC within **3 calendar days** and you within **30 days**. See our [DATA_BREACH_RESPONSE.md](DATA_BREACH_RESPONSE.md).

## 8. Contact us

- Data Protection Officer: see [DPO_CONTACT.md](DPO_CONTACT.md)
- General privacy enquiries: <<DPO_EMAIL>>
- Postal address: <<ORG_ADDRESS>>

## 9. Changes

We review this policy at least annually. Material changes will be notified to all active users via `<NOTIFICATION_CHANNEL>`.

---

*This template complies with the 11 obligations of the Singapore PDPA as of 21 June 2026.*
