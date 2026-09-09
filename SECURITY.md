# Security

Report vulnerabilities privately. Do **not** open a public issue with passwords, tokens, customer site details, or exploit proofs.

## How to report

Email the maintainers, or open a **private** security advisory on the public GitHub repository. Include:

- Affected version / commit
- What you observed
- How to reproduce **without** sharing production secrets

We will not accept reports that include live passwords, MQTT credentials, database dumps, or customer floor plans.

## What this repository must never contain

- Passwords, JWT / MQTT / database secrets, SSH keys, `deploy/.sshpass`
- Device factory unlock PINs
- Customer site addresses, internal Git hosts, or live host IPs
- Signed license **private** keys (`deploy/.license-private*`)

Official builds verify license tokens with a public key in source. The signing private key stays off this tree.

If you find a secret in git history, rotate it and tell us privately. Do not paste it into an issue.
