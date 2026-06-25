# Result callback gap

The bridge can launch tasks now.

Final results back to Telegram are not automatic yet.

Reason:

```text
PokeClaw can broadcast task results.
Termux is not itself a normal app receiver for those broadcasts.
```

Later fix:

```text
PokeClaw sends HTTP callback to local bridge.
Bridge stores result and forwards to Telegram.
```

Until then:

```text
Telegram confirms launch.
PokeClaw shows the final result in its UI.
```
