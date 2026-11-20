"""The stand's media and one probe, beside the project's URLs. Prototype stand only: local port.

stapel-cdn stores files under MEDIA_ROOT and renders host-relative URLs (``/avatar/<hash>/120.webp``);
a deployment's web server serves them, ``runserver`` does not. Here Django serves them, and records
whether each request carried a bearer token, so an app's image loader can be checked on the wire:

- GET /<type>/<sha256>/<file>        the stored file
- GET /e2e/media/seen/               the media requests so far: [{path, authorized}]
"""
from django.conf import settings
from django.http import JsonResponse
from django.urls import path, re_path
from django.views.static import serve

from config.urls import urlpatterns as project_urlpatterns

SEEN = []


def media(request, file_path):
    SEEN.append({"path": "/" + file_path, "authorized": request.headers.get("Authorization", "").startswith("Bearer ")})
    return serve(request, file_path, document_root=settings.MEDIA_ROOT or settings.BASE_DIR)


def seen(request):
    return JsonResponse({"seen": SEEN})


urlpatterns = [
    path("e2e/media/seen/", seen),
    re_path(r"^(?P<file_path>[a-z]+/[0-9a-f]{64}/[^/]+)$", media),
    *project_urlpatterns,
]
