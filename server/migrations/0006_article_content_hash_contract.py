import hashlib

from django.db import migrations


def hash_title_and_body(apps, schema_editor):
    Article = apps.get_model("server", "Article")
    for article in Article.objects.only("id", "title", "body_text").iterator():
        content_hash = hashlib.sha256(
            f"{article.title}\0{article.body_text}".encode()
        ).hexdigest()
        Article.objects.filter(id=article.id).update(content_hash=content_hash)


def hash_body_only(apps, schema_editor):
    Article = apps.get_model("server", "Article")
    for article in Article.objects.only("id", "body_text").iterator():
        content_hash = hashlib.sha256(article.body_text.encode()).hexdigest()
        Article.objects.filter(id=article.id).update(content_hash=content_hash)


class Migration(migrations.Migration):
    dependencies = [("server", "0005_useraccount_email")]

    operations = [
        migrations.RunPython(hash_title_and_body, hash_body_only),
    ]
