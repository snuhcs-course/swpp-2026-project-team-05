"""HTTP endpoints for local article analysis."""

from django.urls import path

from .views import analyze, health


urlpatterns = [
    path("api/health", health),
    path("api/analyze", analyze),
]
