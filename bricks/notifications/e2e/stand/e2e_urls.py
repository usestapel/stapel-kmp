"""The stand's own probes, beside the project's URLs. Prototype stand only: no auth, local port.

- GET  /e2e/push/ready/                       liveness
- GET  /e2e/push/devices/?user_id=<id>         the server's device rows of an account (incl. session binding)
- POST /e2e/push/send/ {user_id, url}          a real notification through process_notification with the
                                               mock sender; answers the captured messages (push.json shape)
"""
import json

from django.http import JsonResponse
from django.urls import path
from django.views.decorators.csrf import csrf_exempt

from config.urls import urlpatterns as project_urlpatterns


def ready(request):
    return JsonResponse({"ok": True})


def devices(request):
    from stapel_notifications.models import DevicePushToken

    rows = DevicePushToken.objects.filter(user_id=request.GET["user_id"]).order_by("pk")
    return JsonResponse({"devices": [
        {
            "id": r.pk,
            "platform": r.platform,
            "is_active": r.is_active,
            "device_id": getattr(r, "device_id", None),
            "session_id": str(getattr(r, "session_id", "") or "") or None,
        }
        for r in rows
    ]})


@csrf_exempt
def send(request):
    from stapel_notifications.channels import push as push_channel
    from stapel_notifications.services import process_notification

    body = json.loads(request.body or b"{}")
    push_channel.mock_outbox.clear()
    process_notification(
        notification_type=body.get("notification_type", "new_message"),
        user_id=body["user_id"],
        variables={"push_url": body["url"]} if body.get("url") else {},
    )
    return JsonResponse({"outbox": list(push_channel.mock_outbox)})


urlpatterns = [
    path("e2e/push/ready/", ready),
    path("e2e/push/devices/", devices),
    path("e2e/push/send/", send),
    *project_urlpatterns,
]
