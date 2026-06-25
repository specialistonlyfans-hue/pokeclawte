# Kali Reports UI

The Kali Orchestrator now exposes a reports index and the Android Kali Lab UI can load and open reports.

## Server API

```text
GET /reports?limit=25
GET /reports/<report_id>.json
GET /reports/<report_id>.md
```

The index returns:

```text
id
created_at
action
target
returncode
summary
json_path
markdown_path
size_bytes
```

## Kali CLI test

```bash
cd kali-orchestrator
export KALI_ORCH_TOKEN="YOUR_TOKEN"
./reports-test.sh 10
```

## PokeClaw chat

```text
/kali reports
/kali report REPORT_ID
```

## Kali Lab UI

Open the app entry `Kali Lab`, then:

```text
Reports tab -> Load Reports
Reports tab -> paste Report ID -> Open Report
```
