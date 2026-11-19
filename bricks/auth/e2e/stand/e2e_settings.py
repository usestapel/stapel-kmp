"""Settings of the auth end-to-end stand: the scaffolded project plus what makes expiry observable."""
from config.settings import *  # noqa: F401,F403
from config.settings import BASE_DIR, MIDDLEWARE

# Seconds. A 5-second access token makes "refresh after expiry" a real server event.
JWT_ACCESS_TOKEN_LIFETIME = 5
JWT_REFRESH_TOKEN_LIFETIME = 600

# Seconds between two codes for one identifier. Short, so scenarios that sign the same identifier in
# on two devices do not wait 30 s; capabilities publish the value, the client reads it from there.
from config.settings import STAPEL_AUTH  # noqa: E402

STAPEL_AUTH = {**STAPEL_AUTH, "OTP_RESEND_COOLDOWN": 2}

DATABASES = {"default": {"ENGINE": "django.db.backends.sqlite3", "NAME": BASE_DIR / "e2e-main.sqlite3"}}

# Counts requests that carry a Cookie header (the client must never send one).
MIDDLEWARE = ["e2e_probe.CookieProbeMiddleware", *MIDDLEWARE]
ROOT_URLCONF = "e2e_urls"
