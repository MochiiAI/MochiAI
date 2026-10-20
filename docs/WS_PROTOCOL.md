# Mochi WebSocket Protocol

The Android app and the cloud backend keep the agent state in lock-step over a
single authenticated WebSocket channel at `/ws`.

## Authentication

The client connects with a JWT issued by `POST /api/auth/register` (or
`/api/auth/refresh`):

```
ws://<host>/ws?token=<JWT>
```

If the token is missing/invalid the server closes with code `4401
unauthorized`.

## Server → client

| Type         | Example                                                               | Purpose                              |
| ------------ | --------------------------------------------------------------------- | ------------------------------------ |
| `hello`      | `{"type":"hello","state":"IDLE","heartbeat_interval":30}`             | sent on connect                      |
| `heartbeat`  | `{"type":"heartbeat","state":"IDLE","sent_at":"..."}`                 | periodic state snapshot              |
| `agent.state`| `{"type":"agent.state","state":"THINKING","action_name":null,"action_detail":null,"message":null,"sent_at":"..."}` | state transition                    |
| `state`      | `{"type":"state","state":"IDLE"}`                                     | reply to `state.get`                 |
| `event`      | `{"type":"event","event":"github.pr_created","data":{...}}`           | completion events (PR, issue, task)  |
| `error`      | `{"type":"error","detail":"..."}`                                     | protocol errors                      |
| `pong`       | `{"type":"pong","at":"..."}`                                          | reply to `ping`                      |

## Client → server

| Type           | Example                                                                                 | Purpose                     |
| -------------- | --------------------------------------------------------------------------------------- | --------------------------- |
| `ping`         | `{"type":"ping"}`                                                                       | keepalive                   |
| `state.get`    | `{"type":"state.get"}`                                                                  | fetch current server state  |
| `agent.state`  | `{"type":"agent.state","state":"ACTION","action_name":"Running terminal command","action_detail":null,"message":null}` | publish a local state transition |

## State values

`IDLE`, `THINKING`, `ACTION`, `CODING`, `OFFLINE`, `ERROR` — mirrored by
`AgentState` in the Android app and `app.models.AgentState` in the backend.

## REST fallback

If the WebSocket drops, the app can `PUT /api/state` (and `GET /api/state`)
with the same JSON body (`type` omitted) to keep the cloud in sync until the
socket reconnects.