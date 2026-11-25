"""Keep Django-owned storage behind the API, including on Supabase."""
from django.db import migrations


def restrict_direct_access(apps, schema_editor):
    if schema_editor.connection.vendor != "postgresql":
        return
    tables = sorted({model._meta.db_table for model in apps.get_app_config("server").get_models()
                     if model._meta.managed} | {"django_migrations"})
    quote = schema_editor.quote_name
    with schema_editor.connection.cursor() as cursor:
        cursor.execute("SELECT rolname FROM pg_roles WHERE rolname IN ('anon', 'authenticated')")
        roles = ["PUBLIC"] + [quote(row[0]) for row in cursor.fetchall()]
    for table in tables:
        schema_editor.execute(f"ALTER TABLE {quote(table)} ENABLE ROW LEVEL SECURITY")
        schema_editor.execute(f"REVOKE ALL ON TABLE {quote(table)} FROM {', '.join(roles)}")


class Migration(migrations.Migration):
    dependencies = [("server", "0016_auth_throttle")]
    # Code rollback must not reopen direct access to account/session storage.
    operations = [migrations.RunPython(restrict_direct_access, migrations.RunPython.noop)]
