"""Settings for the article analysis API."""

import os

from django.core.exceptions import ImproperlyConfigured


IS_PRODUCTION = os.getenv("DJANGO_ENV") == "production"
DEBUG = not IS_PRODUCTION
SECRET_KEY = os.getenv("DJANGO_SECRET_KEY", "")
if IS_PRODUCTION and not SECRET_KEY:
    raise ImproperlyConfigured("DJANGO_SECRET_KEY must be set in production")
if IS_PRODUCTION and not os.getenv("ANALYZE_ACCESS_TOKEN", "").strip():
    raise ImproperlyConfigured("ANALYZE_ACCESS_TOKEN must be set in production")
if not SECRET_KEY:
    SECRET_KEY = "unsafe-local-development-only"

ALLOWED_HOSTS = (
    [host.strip() for host in os.getenv("DJANGO_ALLOWED_HOSTS", ".run.app").split(",") if host.strip()]
    if IS_PRODUCTION else ["localhost", "127.0.0.1", "10.0.2.2", "testserver"]
)

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

SECURE_CONTENT_TYPE_NOSNIFF = True
SECURE_SSL_REDIRECT = IS_PRODUCTION
SECURE_HSTS_SECONDS = 3600 if IS_PRODUCTION else 0
SESSION_COOKIE_SECURE = IS_PRODUCTION
CSRF_COOKIE_SECURE = IS_PRODUCTION
if IS_PRODUCTION:
    SECURE_PROXY_SSL_HEADER = ("HTTP_X_FORWARDED_PROTO", "https")
