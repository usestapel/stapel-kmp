"""Server-side observation points for the auth end-to-end suite (stand only, never shipped)."""
import threading

from django.http import JsonResponse
from stapel_core.captcha.backends import CaptchaVerifier

_lock = threading.Lock()
_counters = {"requests": 0, "with_cookie": 0, "cookie_paths": []}


class CookieProbeMiddleware:
    def __init__(self, get_response):
        self.get_response = get_response

    def __call__(self, request):
        if not request.path.startswith("/e2e/"):
            with _lock:
                _counters["requests"] += 1
                if request.META.get("HTTP_COOKIE"):
                    _counters["with_cookie"] += 1
                    _counters["cookie_paths"].append(request.path)
        return self.get_response(request)


def probe(request):
    with _lock:
        return JsonResponse(dict(_counters))


class FixedTokenVerifier(CaptchaVerifier):
    """Accepts the token equal to the configured secret; anything else is refused."""

    def verify(self, token, ip=None, *, level=None):
        return bool(token) and token == self.secret
