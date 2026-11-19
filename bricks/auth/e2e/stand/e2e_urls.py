from django.urls import path

from config.urls import urlpatterns as project_urlpatterns
from e2e_probe import probe

urlpatterns = [path("e2e/probe/", probe), *project_urlpatterns]
