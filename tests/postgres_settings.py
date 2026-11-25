"""Isolated local/CI PostgreSQL tests; never connects to the production database."""
import os

from server.settings import *  # noqa: F403

DATABASES = {"default": {
    "ENGINE": "django.db.backends.postgresql",
    "NAME": "postgres",
    "USER": "postgres",
    "PASSWORD": os.environ.get("FRAMELESS_TEST_PG_PASSWORD", "postgres"),
    "HOST": "127.0.0.1",
    "PORT": os.environ.get("FRAMELESS_TEST_PG_PORT", "5432"),
    "CONN_MAX_AGE": 0,
}}
