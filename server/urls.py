"""HTTP endpoints for local article analysis."""

from django.urls import path

from .views import analysis_detail, analyses, analyze, health, login, me
from . import api_v1


urlpatterns = [
    path("api/health", health),
    path("api/analyze", analyze),
    path("api/auth/login", login),
    path("api/auth/me", me),
    path("api/analyses", analyses),
    path("api/analyses/<uuid:record_id>", analysis_detail),
    path("api/v1/auth/register", api_v1.register),
    path("api/v1/auth/login", api_v1.login),
    path("api/v1/auth/logout", api_v1.logout),
    path("api/v1/auth/me", api_v1.me),
    path("api/v1/publishers", api_v1.publishers),
    path("api/v1/articles", api_v1.articles),
    path("api/v1/articles/<uuid:article_id>", api_v1.article_detail),
    path("api/v1/articles/<uuid:article_id>/analysis", api_v1.article_analysis),
]
