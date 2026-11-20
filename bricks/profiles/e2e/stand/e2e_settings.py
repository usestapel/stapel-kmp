"""Settings of the profiles end-to-end stand: the scaffolded project plus short OTP cooldowns."""
from config.settings import *  # noqa: F401,F403
from config.settings import BASE_DIR, STAPEL_AUTH

# Every scenario signs fresh accounts in from 127.0.0.1: short resend cooldown, no guest ceiling.
STAPEL_AUTH = {**STAPEL_AUTH, "OTP_RESEND_COOLDOWN": 2, "ANONYMOUS_RATE_LIMIT_PER_HOUR": 1000}

DATABASES = {"default": {"ENGINE": "django.db.backends.sqlite3", "NAME": BASE_DIR / "e2e-profiles.sqlite3"}}
