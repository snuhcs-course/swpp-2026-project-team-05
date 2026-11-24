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
    path("api/v1/me/followed-publishers", api_v1.followed_publishers),
    path("api/v1/stories", api_v1.stories),
    path("api/v1/trends", api_v1.trends),
    path("api/internal/trends/cleanup", api_v1.trends_cleanup),
    path("api/v1/articles", api_v1.articles),
    path("api/v1/articles/<uuid:article_id>", api_v1.article_detail),
    path("api/v1/articles/<uuid:article_id>/analysis", api_v1.article_analysis),
    path("api/v1/articles/<uuid:article_id>/view", api_v1.article_view),
    path("api/v1/link-analyses", api_v1.link_analyses),
    path("api/v1/saved-articles", api_v1.saved_articles),
    path("api/v1/saved-articles/<uuid:article_id>", api_v1.saved_article),
]
