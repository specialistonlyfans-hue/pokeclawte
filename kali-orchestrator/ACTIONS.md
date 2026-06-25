# Actions

Allowed MVP actions:

```text
ping
  Basic reachability check.

dns_check / dns
  Resolve hostnames to IP addresses.

web_check / web / web_headers
  Fetch HTTP response, headers, status code, content type and small body sample.

tls_check / tls
  Read TLS certificate metadata and cipher information.

scan_host
  Limited TCP service inventory using a small top-port count and light version detection.

service_inventory / inventory
  Combined DNS, ping and limited service inventory report.
```

Blocked by design:

```text
free shell
credential attacks
phishing flows
Wi-Fi disruption
malware or payload generation
stealth behavior
autonomous exploitation
```

Targets must match `policy.allowed_targets` in `config.json`.
