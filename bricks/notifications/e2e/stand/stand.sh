#!/usr/bin/env bash
# The notifications push end-to-end stand: a backend scaffolded by stapel-tools from PyPI with auth and
# notifications only, run natively (no Docker) in prototype posture (mock OTP) with the mock push sender.
#
#   bricks/notifications/e2e/stand/stand.sh up       # scaffold (once), install, migrate, start
#   bricks/notifications/e2e/stand/stand.sh status
#   bricks/notifications/e2e/stand/stand.sh down     # stop only the process this script started (pid file)
#   bricks/notifications/e2e/stand/stand.sh purge    # down + delete the stand directory this script created
#
# Then: ./gradlew :stapel-notifications-e2e:test -Pstapel.e2e.baseUrl=http://127.0.0.1:18771/
set -euo pipefail

TOOLS_VERSION="0.86.0"
# The backends the suite proves: the pinned contract's version (contracts/notifications/pin.json) and its
# siblings (auth 0.54.1 mints the session claim the device rows are bound by).
NOTIFICATIONS_VERSION="0.31.1"
AUTH_VERSION="0.54.1"
CORE_VERSION="0.99.5"
PYTHON="${KP_PYTHON:-python3.12}"
HERE="$(cd "$(dirname "$0")" && pwd)"
STAND="$(cd "$HERE/.." && pwd)/build/stand"
PROJECT="$STAND/e2epush"
PORT="${PUSH_STAND_PORT:-18771}"

log() { printf '[push-stand] %s\n' "$*" >&2; }

scaffold() {
    if [[ ! -x "$STAND/tools-venv/bin/stapel-create-project" ]]; then
        log "creating the stapel-tools $TOOLS_VERSION venv"
        mkdir -p "$STAND"
        "$PYTHON" -m venv "$STAND/tools-venv"
        "$STAND/tools-venv/bin/pip" install -q "stapel-tools==$TOOLS_VERSION"
    fi
    if [[ ! -f "$PROJECT/manage.py" ]]; then
        log "scaffolding the project (--type minimal --modules auth notifications --posture prototype)"
        (cd "$STAND" && "$STAND/tools-venv/bin/stapel-create-project" e2epush \
            --type minimal --title E2E --url "http://127.0.0.1:$PORT" \
            --company-name Example --company-email e2e@example.com \
            --modules auth notifications --auth-model public --auth-kinds email \
            --posture prototype --no-git --output-dir . >"$STAND/scaffold.log" 2>&1)
    fi
    if [[ ! -x "$PROJECT/.venv/bin/python" ]]; then
        log "installing (stapel-notifications $NOTIFICATIONS_VERSION, stapel-auth $AUTH_VERSION, stapel-core $CORE_VERSION)"
        sed -E -i '' \
            -e "s/^stapel-notifications(\[[a-z,]+\])?[<>=!~].*/stapel-notifications\1==$NOTIFICATIONS_VERSION/" \
            -e "s/^stapel-auth(\[[a-z,]+\])?[<>=!~].*/stapel-auth\1==$AUTH_VERSION/" \
            -e "s/^stapel-core(\[[a-z,]+\])?[<>=!~].*/stapel-core\1==$CORE_VERSION/" \
            "$PROJECT/requirements.txt"
        "$PYTHON" -m venv "$PROJECT/.venv"
        "$PROJECT/.venv/bin/pip" install -q -r "$PROJECT/requirements.txt" >"$STAND/pip.log" 2>&1
    fi
    cp "$HERE/e2e_settings.py" "$HERE/e2e_urls.py" "$PROJECT/"
}

pidfile() { echo "$STAND/push.pid"; }
running() { [[ -f "$(pidfile)" ]] && kill -0 "$(cat "$(pidfile)")" 2>/dev/null; }

start() {
    if running; then log "already running (pid $(cat "$(pidfile)"))"; return; fi
    if lsof -nP -iTCP:"$PORT" -sTCP:LISTEN >/dev/null 2>&1; then
        log "port $PORT is taken by a process this script did not record; not starting"; exit 1
    fi
    log "migrating"
    (cd "$PROJECT" && DJANGO_SETTINGS_MODULE=e2e_settings .venv/bin/python manage.py migrate --noinput >"$STAND/migrate.log" 2>&1)
    log "starting on 127.0.0.1:$PORT"
    (cd "$PROJECT" && DJANGO_SETTINGS_MODULE=e2e_settings nohup .venv/bin/python manage.py runserver \
        "127.0.0.1:$PORT" --noreload </dev/null >"$STAND/server.log" 2>&1 & echo $! >"$(pidfile)") >/dev/null 2>&1
    for _ in $(seq 1 60); do
        if curl -fsS "http://127.0.0.1:$PORT/e2e/push/ready/" >/dev/null 2>&1; then
            lsof -t -nP -iTCP:"$PORT" -sTCP:LISTEN | head -1 >"$(pidfile)"
            log "ready (pid $(cat "$(pidfile)"))"; return
        fi
        sleep 0.5
    done
    log "did not become ready; see $STAND/server.log"; exit 1
}

stop() {
    if running; then kill "$(cat "$(pidfile)")"; log "stopped (pid $(cat "$(pidfile)"))"; fi
    rm -f "$(pidfile)"
}

case "${1:-}" in
    up)
        scaffold
        start
        "$PROJECT/.venv/bin/pip" show stapel-notifications stapel-auth stapel-core | grep -E '^(Name|Version):' >"$STAND/versions.txt"
        cat "$STAND/versions.txt" >&2
        ;;
    down) stop ;;
    status) if running; then echo "running pid $(cat "$(pidfile)")"; else echo "stopped"; fi ;;
    purge) stop; rm -rf "$STAND" ;;
    *) echo "usage: $0 {up|down|status|purge}" >&2; exit 2 ;;
esac
