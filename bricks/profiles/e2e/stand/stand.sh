#!/usr/bin/env bash
# The profiles end-to-end stand: a backend scaffolded by stapel-tools from PyPI with auth, profiles and
# cdn (the avatar upload), run natively in prototype posture (mock OTP).
#
#   bricks/profiles/e2e/stand/stand.sh up       # scaffold (once), install, migrate, sync languages, start
#   bricks/profiles/e2e/stand/stand.sh status
#   bricks/profiles/e2e/stand/stand.sh down     # stop only the process this script started (pid file)
#   bricks/profiles/e2e/stand/stand.sh purge    # down + delete the stand directory this script created
#
# Then: ./gradlew :stapel-profiles-e2e:test -Pstapel.e2e.baseUrl=http://127.0.0.1:18761/
set -euo pipefail

TOOLS_VERSION="0.86.0"
# The backends the suite proves: the pinned contracts' versions (contracts/*/pin.json) and their core.
PROFILES_VERSION="0.23.3"
AUTH_VERSION="0.54.0"
CDN_VERSION="0.31.2"
CORE_VERSION="0.99.4"
PYTHON="${K15_PYTHON:-python3.12}"
HERE="$(cd "$(dirname "$0")" && pwd)"
STAND="$(cd "$HERE/.." && pwd)/build/stand"
PROJECT="$STAND/e2eprofiles"
PORT="${PROFILES_STAND_PORT:-18761}"

log() { printf '[profiles-stand] %s\n' "$*" >&2; }

scaffold() {
    if [[ ! -x "$STAND/tools-venv/bin/stapel-create-project" ]]; then
        log "creating the stapel-tools $TOOLS_VERSION venv"
        mkdir -p "$STAND"
        "$PYTHON" -m venv "$STAND/tools-venv"
        "$STAND/tools-venv/bin/pip" install -q "stapel-tools==$TOOLS_VERSION"
    fi
    if [[ ! -f "$PROJECT/manage.py" ]]; then
        log "scaffolding the project (--type minimal --modules auth profiles cdn --posture prototype)"
        (cd "$STAND" && "$STAND/tools-venv/bin/stapel-create-project" e2eprofiles \
            --type minimal --title E2E --url "http://127.0.0.1:$PORT" \
            --company-name Example --company-email e2e@example.com \
            --modules auth profiles cdn --auth-model public --auth-kinds phone,email,password \
            --posture prototype --no-git --output-dir . >"$STAND/scaffold.log" 2>&1)
    fi
    if [[ ! -x "$PROJECT/.venv/bin/python" ]]; then
        log "installing (stapel-profiles $PROFILES_VERSION, stapel-auth $AUTH_VERSION, stapel-cdn $CDN_VERSION, stapel-core $CORE_VERSION)"
        # The scaffold's registry may cap a library below the pinned contract: the stand moves the lines
        # it proves to exact versions and changes nothing else.
        sed -E -i '' \
            -e "s/^stapel-profiles(\[[a-z,]+\])?[<>=!~].*/stapel-profiles\1==$PROFILES_VERSION/" \
            -e "s/^stapel-auth(\[[a-z,]+\])?[<>=!~].*/stapel-auth\1==$AUTH_VERSION/" \
            -e "s/^stapel-cdn(\[[a-z,]+\])?[<>=!~].*/stapel-cdn\1==$CDN_VERSION/" \
            -e "s/^stapel-core(\[[a-z,]+\])?[<>=!~].*/stapel-core\1==$CORE_VERSION/" \
            "$PROJECT/requirements.txt"
        "$PYTHON" -m venv "$PROJECT/.venv"
        "$PROJECT/.venv/bin/pip" install -q -r "$PROJECT/requirements.txt" >"$STAND/pip.log" 2>&1
    fi
    cp "$HERE/e2e_settings.py" "$PROJECT/"
}

pidfile() { echo "$STAND/profiles.pid"; }
running() { [[ -f "$(pidfile)" ]] && kill -0 "$(cat "$(pidfile)")" 2>/dev/null; }

start() {
    if running; then log "already running (pid $(cat "$(pidfile)"))"; return; fi
    if lsof -nP -iTCP:"$PORT" -sTCP:LISTEN >/dev/null 2>&1; then
        log "port $PORT is taken by a process this script did not record; not starting"; exit 1
    fi
    log "migrating and syncing the language catalogue (empty on a fresh deployment)"
    (cd "$PROJECT" && DJANGO_SETTINGS_MODULE=e2e_settings .venv/bin/python manage.py migrate --noinput >"$STAND/migrate.log" 2>&1)
    (cd "$PROJECT" && DJANGO_SETTINGS_MODULE=e2e_settings .venv/bin/python manage.py sync_languages >"$STAND/languages.log" 2>&1)
    log "starting on 127.0.0.1:$PORT"
    (cd "$PROJECT" && DJANGO_SETTINGS_MODULE=e2e_settings nohup .venv/bin/python manage.py runserver \
        "127.0.0.1:$PORT" --noreload </dev/null >"$STAND/server.log" 2>&1 & echo $! >"$(pidfile)") >/dev/null 2>&1
    for _ in $(seq 1 60); do
        if curl -fsS "http://127.0.0.1:$PORT/profiles/api/v1/languages/" >/dev/null 2>&1; then
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
        "$PROJECT/.venv/bin/pip" show stapel-profiles stapel-auth stapel-core stapel-cdn | grep -E '^(Name|Version):' >"$STAND/versions.txt"
        cat "$STAND/versions.txt" >&2
        ;;
    down) stop ;;
    status) if running; then echo "running pid $(cat "$(pidfile)")"; else echo "stopped"; fi ;;
    purge) stop; rm -rf "$STAND" ;;
    *) echo "usage: $0 {up|down|status|purge}" >&2; exit 2 ;;
esac
