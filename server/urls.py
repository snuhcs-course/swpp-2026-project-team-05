"""HTTP endpoints used by the Android client and Render health checks."""

from django.urls import path

from .views import analyze, health


urlpatterns = [
    path("api/health", health),
    path("api/analyze", analyze),
]
