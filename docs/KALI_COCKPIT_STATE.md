# Kali Cockpit State

The Kali Orchestrator now stores and exposes operational state for the Android Kali Lab cockpit.

## Server endpoints

```text
GET  /targets
POST /targets
GET  /findings
GET  /evidence
GET  /jobs
```

## PokeClaw chat commands

```text
/kali targets
/kali target add home-router 192.168.1.1 router
/kali findings
/kali evidence
/kali jobs
```

## Kali Lab UI tabs

```text
Run
Workflows
Targets
Findings
Reports
Settings
```

## Storage files

```text
storage/targets.json
storage/findings.jsonl
storage/jobs.jsonl
reports/*.json
reports/*.md
```

## CLI tests

```bash
cd kali-orchestrator
export KALI_ORCH_TOKEN="YOUR_TOKEN"
./targets-test.sh home-router 127.0.0.1 host
./state-test.sh
```

Findings are derived from completed reports. Evidence currently lists report artifacts. Job history records completed action/workflow runs with report IDs.
