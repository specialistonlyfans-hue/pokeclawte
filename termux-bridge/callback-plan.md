# Callback plan for later APK work

The bridge can launch PokeClaw today. Terminal result callbacks need one extra Android-side piece later.

PokeClaw already supports callback fields:

```text
request_id
return_action
return_package
```

And statuses:

```text
accepted
completed
failed
cancelled
blocked
rejected
```

Current bridge state:

```text
Termux can launch tasks and keep local launch history.
Termux cannot yet receive PokeClaw terminal broadcasts cleanly.
```

Best later APK addition:

```text
PokeClaw result -> small exported callback receiver/service -> local socket or file -> Termux bridge -> Telegram reply
```

Alternative:

```text
Add a tiny HTTP callback sender inside PokeClaw that POSTs to http://127.0.0.1:8787/callback
```

Preferred route for your setup:

```text
Add PokeClaw HTTP callback sender later.
```

Reason: Termux already has the HTTP server. PokeClaw can send the final result to Termux on localhost without requiring a separate receiver APK.
