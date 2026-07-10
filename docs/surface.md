# The surface

Written by `npm run surface` from the code itself, for garterhole/1.0.0.

## HTTP routes

| method | path |
| --- | --- |
| GET | `/health` |
| GET | `/ready` |
| GET | `/routes` |
| GET | `/methods` |
| POST | `/methods` |
| GET | `/methods/:title` |
| DELETE | `/methods/:title` |
| POST | `/methods/:title/analyse` |
| GET | `/towers` |
| POST | `/towers` |
| GET | `/towers/:id` |
| GET | `/towers/:id/bells` |
| DELETE | `/towers/:id` |
| GET | `/performances` |
| POST | `/performances` |
| GET | `/performances/:id` |
| POST | `/performances/check` |
| GET | `/report` |
| GET | `/statistics` |
| POST | `/striking` |
| POST | `/striking/report` |
| POST | `/striking/blows.csv` |
| POST | `/striking/bells.csv` |

## Commands

- `garterhole method add`
- `garterhole method list`
- `garterhole method show`
- `garterhole method remove`
- `garterhole serve`
- `garterhole tower add`
- `garterhole tower list`
- `garterhole tower show`
- `garterhole prove`
- `garterhole lead`
- `garterhole records file`
- `garterhole records list`
- `garterhole records report`
- `garterhole records board`
- `garterhole serve`

## Settings

| variable | default |
| --- | --- |
| `GARTERHOLE_PORT` | `8080` |
| `GARTERHOLE_HOST` | `127.0.0.1` |
| `GARTERHOLE_DATA` | `data` |
| `GARTERHOLE_LOG_LEVEL` | `info` |
| `GARTERHOLE_RATE_LIMIT` | `120` |
| `GARTERHOLE_READ_ONLY` | `false` |
