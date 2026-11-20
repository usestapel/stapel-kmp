#!/usr/bin/env bash
# The auth end-to-end stand: a backend scaffolded by stapel-tools from PyPI, run natively.
#
#   bricks/auth/e2e/stand/stand.sh up       # scaffold (once), install, migrate, start both instances
#   bricks/auth/e2e/stand/stand.sh status   # pids, ports, readiness
#   bricks/auth/e2e/stand/stand.sh down     # stop only the processes this script started (pid files)
#   bricks/auth/e2e/stand/stand.sh purge    # down + delete the stand directory this script created
#
# Two instances of the same project, each with its own SQLite file:
#   main     127.0.0.1:${K7_STAND_PORT:-18731}  prototype posture (mock OTP), access token lives 5 s
#   captcha  127.0.0.1:${K7_CAPTCHA_PORT:-18732} the same plus a captcha verifier that accepts one token
# Both log every request that carries a Cookie header; GET /e2e/probe/ reports the counters.
#
# Then: ./gradlew :stapel-auth-e2e:test -Pstapel.e2e.baseUrl=http://127.0.0.1:18731/ \
#                                       -Pstapel.e2e.captchaBaseUrl=http://127.0.0.1:18732/
set -euo pipefail

TOOLS_VERSION="0.86.0"
# The backend the suite proves: the pinned contract's version (contracts/auth/pin.json) and its core.
AUTH_VERSION="0.54.0"
CORE_VERSION="0.99.4"
PYTHON="${K7_PYTHON:-python3.12}"
HERE="$(cd "$(dirname "$0")" && pwd)"
STAND="$(cd "$HERE/.." && pwd)/build/stand"
PROJECT="$STAND/e2eauth"
MAIN_PORT="${K7_STAND_PORT:-18731}"
CAPTCHA_PORT="${K7_CAPTCHA_PORT:-18732}"

log() { printf '[k7-stand] %s\n' "$*" >&2; }

scaffold() {
    if [[ ! -x "$STAND/tools-venv/bin/stapel-create-project" ]]; then
        log "creating the stapel-tools $TOOLS_VERSION venv"
        mkdir -p "$STAND"
        "$PYTHON" -m venv "$STAND/tools-venv"
        "$STAND/tools-venv/bin/pip" install -q "stapel-tools==$TOOLS_VERSION"
    fi
    if [[ ! -f "$PROJECT/manage.py" ]]; then
        log "scaffolding the project (--type minimal --modules auth --posture prototype)"
        (cd "$STAND" && "$STAND/tools-venv/bin/stapel-create-project" e2eauth \
            --type minimal --title E2E --url "http://127.0.0.1:$MAIN_PORT" \
            --company-name Example --company-email e2e@example.com \
            --modules auth --auth-model public --auth-kinds phone,email,password \
            --posture prototype --no-git --output-dir . >"$STAND/scaffold.log" 2>&1)
    fi
    if [[ ! -x "$PROJECT/.venv/bin/python" ]]; then
        log "installing the project's requirements (stapel-auth $AUTH_VERSION, stapel-core $CORE_VERSION)"
        # The scaffold's registry may cap a library below the pinned contract: the stand moves the two
        # lines it proves to exact versions and changes nothing else.
        sed -E -i '' \
            -e "s/^stapel-auth(\[[a-z,]+\])?[<>=!~].*/stapel-auth\1==$AUTH_VERSION/" \
            -e "s/^stapel-core(\[[a-z,]+\])?[<>=!~].*/stapel-core\1==$CORE_VERSION/" \
            "$PROJECT/requirements.txt"
        "$PYTHON" -m venv "$PROJECT/.venv"
        "$PROJECT/.venv/bin/pip" install -q -r "$PROJECT/requirements.txt" >"$STAND/pip.log" 2>&1
    fi
    cp "$HERE/e2e_settings.py" "$HERE/e2e_captcha_settings.py" "$HERE/e2e_probe.py" "$HERE/e2e_urls.py" "$PROJECT/"
}

pidfile() { echo "$STAND/$1.pid"; }

running() {
    local f; f="$(pidfile "$1")"
    [[ -f "$f" ]] && kill -0 "$(cat "$f")" 2>/dev/null
}

start() { # name port settings-module
    local name="$1" port="$2" settings="$3"
    if running "$name"; then log "$name already running (pid $(cat "$(pidfile "$name")"))"; return; fi
    if lsof -nP -iTCP:"$port" -sTCP:LISTEN >/dev/null 2>&1; then
        log "port $port is taken by a process this script did not record; not starting $name"; exit 1
    fi
    log "migrating $name"
    (cd "$PROJECT" && DJANGO_SETTINGS_MODULE="$settings" .venv/bin/python manage.py migrate --noinput >"$STAND/$name-migrate.log" 2>&1)
    log "starting $name on 127.0.0.1:$port"
    (cd "$PROJECT" && DJANGO_SETTINGS_MODULE="$settings" nohup .venv/bin/python manage.py runserver \
        "127.0.0.1:$port" --noreload </dev/null >"$STAND/$name.log" 2>&1 & echo $! >"$(pidfile "$name")") >/dev/null 2>&1
    for _ in $(seq 1 60); do
        if curl -fsS "http://127.0.0.1:$port/auth/api/v1/capabilities/" >/dev/null 2>&1; then
            # The venv interpreter may re-exec: record the process that actually listens.
            lsof -t -nP -iTCP:"$port" -sTCP:LISTEN | head -1 >"$(pidfile "$name")"
            log "$name ready (pid $(cat "$(pidfile "$name")"))"; return
        fi
        sleep 0.5
    done
    log "$name did not become ready; see $STAND/$name.log"; exit 1
}

stop() {
    local name="$1" f; f="$(pidfile "$1")"
    if running "$name"; then
        kill "$(cat "$f")"
        log "stopped $name (pid $(cat "$f"))"
    fi
    rm -f "$f"
}

case "${1:-}" in
    up)
        scaffold
        start k7-main "$MAIN_PORT" e2e_settings
        start k7-captcha "$CAPTCHA_PORT" e2e_captcha_settings
        "$PROJECT/.venv/bin/pip" show stapel-auth stapel-core | grep -E '^(Name|Version):' >"$STAND/versions.txt"
        cat "$STAND/versions.txt" >&2
        ;;
    down)
        stop k7-main
        stop k7-captcha
        ;;
    status)
        for n in k7-main k7-captcha; do
            if running "$n"; then echo "$n running pid $(cat "$(pidfile "$n")")"; else echo "$n stopped"; fi
        done
        ;;
    purge)
        stop k7-main
        stop k7-captcha
        rm -rf "$STAND"
        ;;
    *)
        echo "usage: $0 {up|down|status|purge}" >&2
        exit 2
        ;;
esac
