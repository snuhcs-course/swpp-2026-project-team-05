"""Settings for the article analysis API."""

import os
from urllib.parse import unquote, urlsplit

from django.core.exceptions import ImproperlyConfigured


IS_PRODUCTION = os.getenv("DJANGO_ENV") == "production"
DEBUG = not IS_PRODUCTION
SECRET_KEY = os.getenv("DJANGO_SECRET_KEY", "")
if IS_PRODUCTION and not SECRET_KEY:
    raise ImproperlyConfigured("DJANGO_SECRET_KEY must be set in production")
if not SECRET_KEY:
    SECRET_KEY = "unsafe-local-development-only"

ALLOWED_HOSTS = (
    [host.strip() for host in os.getenv("DJANGO_ALLOWED_HOSTS", ".run.app").split(",") if host.strip()]
    if IS_PRODUCTION else ["localhost", "127.0.0.1", "10.0.2.2", "testserver"]
)

ROOT_URLCONF = "server.urls"
INSTALLED_APPS = ["server"]
MIDDLEWARE = [
    "django.middleware.security.SecurityMiddleware",
    "django.middleware.csrf.CsrfViewMiddleware",
    "django.middleware.clickjacking.XFrameOptionsMiddleware",
]
DATABASE_URL = os.getenv("DATABASE_URL", "").strip()
if DATABASE_URL:
    parsed_database = urlsplit(DATABASE_URL)
    if parsed_database.scheme not in {"postgres", "postgresql"} or not parsed_database.hostname:
        raise ImproperlyConfigured("DATABASE_URL must be a PostgreSQL connection URL")
    DATABASES = {
        "default": {
            "ENGINE": "django.db.backends.postgresql",
            "NAME": unquote(parsed_database.path.lstrip("/")) or "postgres",
            "USER": unquote(parsed_database.username or ""),
            "PASSWORD": unquote(parsed_database.password or ""),
            "HOST": parsed_database.hostname,
            "PORT": parsed_database.port or 5432,
            # Vercel functions should not hold database sessions between requests.
            "CONN_MAX_AGE": 0,
            "DISABLE_SERVER_SIDE_CURSORS": True,
            "OPTIONS": {
                "sslmode": "require",
                "connect_timeout": 5,
                # Supabase transaction pooling does not support prepared statements.
                "prepare_threshold": None,
            },
        }
    }
else:
    DATABASES = (
        {"default": {
            "ENGINE": "django.db.backends.sqlite3",
            "NAME": os.getenv("FRAMELESS_SQLITE_PATH", ":memory:"),
        }}
        if os.getenv("FRAMELESS_USE_SQLITE") == "1" else {}
    )
TEMPLATES = []
WSGI_APPLICATION = "server.wsgi.application"
DATA_UPLOAD_MAX_MEMORY_SIZE = 8192
AUTH_PASSWORD_VALIDATORS = [
    {"NAME": "django.contrib.auth.password_validation.MinimumLengthValidator",
     "OPTIONS": {"min_length": 10}},
    {"NAME": "django.contrib.auth.password_validation.CommonPasswordValidator"},
    {"NAME": "django.contrib.auth.password_validation.NumericPasswordValidator"},
]

SECURE_CONTENT_TYPE_NOSNIFF = True
SECURE_SSL_REDIRECT = IS_PRODUCTION
SECURE_HSTS_SECONDS = 3600 if IS_PRODUCTION else 0
SESSION_COOKIE_SECURE = IS_PRODUCTION
CSRF_COOKIE_SECURE = IS_PRODUCTION
if IS_PRODUCTION:
    SECURE_PROXY_SSL_HEADER = ("HTTP_X_FORWARDED_PROTO", "https")

LOGGING = {
    "version": 1,
    "disable_existing_loggers": False,
    "formatters": {
        "analysis": {"format": "%(levelname)s %(name)s %(message)s"},
    },
    "handlers": {
        "analysis_console": {
            "class": "logging.StreamHandler",
            "formatter": "analysis",
        },
    },
    "loggers": {
        "server.analysis": {
            "handlers": ["analysis_console"],
            "level": "INFO",
            "propagate": False,
        },
        "server.views": {
            "handlers": ["analysis_console"],
            "level": "INFO",
            "propagate": False,
        },
    },
}
