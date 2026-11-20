"""Settings of the push end-to-end stand: the scaffolded project, the mock push sender, the stand's probes."""
from config.settings import *  # noqa: F401,F403
from config.settings import BASE_DIR, STAPEL_AUTH

STAPEL_AUTH = {**STAPEL_AUTH, "OTP_RESEND_COOLDOWN": 2, "ANONYMOUS_RATE_LIMIT_PER_HOUR": 1000}
try:
    from config.settings import STAPEL_NOTIFICATIONS
except ImportError:
    STAPEL_NOTIFICATIONS = {}
# The mock sender: what FCM would have been handed is captured in-process (channels.push.mock_outbox).
STAPEL_NOTIFICATIONS = {**STAPEL_NOTIFICATIONS, "PUSH_PROVIDER": "mock"}

DATABASES = {"default": {"ENGINE": "django.db.backends.sqlite3", "NAME": BASE_DIR / "e2e-push.sqlite3"}}
ROOT_URLCONF = "e2e_urls"
