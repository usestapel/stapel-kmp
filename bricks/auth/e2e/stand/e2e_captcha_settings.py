"""The captcha instance of the stand: identical, plus a verifier that accepts exactly one token."""
from e2e_settings import *  # noqa: F401,F403
from e2e_settings import BASE_DIR

DATABASES = {"default": {"ENGINE": "django.db.backends.sqlite3", "NAME": BASE_DIR / "e2e-captcha.sqlite3"}}

STAPEL_CAPTCHA = {"BACKEND": "e2e_probe.FixedTokenVerifier", "SECRET": "e2e-captcha-ok"}
