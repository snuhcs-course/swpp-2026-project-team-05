"""Minimal, database-free settings for the article analysis API."""

import os

from django.core.exceptions import ImproperlyConfigured


ON_RENDER = bool(os.getenv("RENDER"))
DEBUG = os.getenv("DJANGO_DEBUG") == "1" and not ON_RENDER

SECRET_KEY = os.getenv("DJANGO_SECRET_KEY", "")
if not SECRET_KEY:
    if ON_RENDER:
        raise ImproperlyConfigured("DJANGO_SECRET_KEY must be set on Render")
    SECRET_KEY = "unsafe-local-development-only"

ALLOWED_HOSTS = ["localhost", "127.0.0.1", "testserver"]
ALLOWED_HOSTS.extend(
    host.strip() for host in os.getenv("DJANGO_ALLOWED_HOSTS", "").split(",") if host.strip()
)
if ON_RENDER:
    hostname = os.getenv("RENDER_EXTERNAL_HOSTNAME", "")
    if not hostname:
        raise ImproperlyConfigured("RENDER_EXTERNAL_HOSTNAME is missing")
    ALLOWED_HOSTS.append(hostname)

ROOT_URLCONF = "server.urls"
INSTALLED_APPS = []
MIDDLEWARE = [
    "django.middleware.security.SecurityMiddleware",
    "django.middleware.csrf.CsrfViewMiddleware",
    "django.middleware.clickjacking.XFrameOptionsMiddleware",
]
DATABASES = {}
TEMPLATES = []
WSGI_APPLICATION = "server.wsgi.application"
DATA_UPLOAD_MAX_MEMORY_SIZE = 8192

SECURE_PROXY_SSL_HEADER = ("HTTP_X_FORWARDED_PROTO", "https") if ON_RENDER else None
SECURE_CONTENT_TYPE_NOSNIFF = True
SECURE_SSL_REDIRECT = ON_RENDER
SECURE_HSTS_SECONDS = 3600 if ON_RENDER else 0
CSRF_COOKIE_SECURE = ON_RENDER
