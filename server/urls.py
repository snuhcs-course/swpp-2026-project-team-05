"""HTTP endpoints for local article analysis."""

from django.urls import path

from .views import analysis_detail, analyses, analyze, health, login, me


urlpatterns = [
    path("api/health", health),
    path("api/analyze", analyze),
    path("api/auth/login", login),
    path("api/auth/me", me),
    path("api/analyses", analyses),
    path("api/analyses/<uuid:record_id>", analysis_detail),
]
