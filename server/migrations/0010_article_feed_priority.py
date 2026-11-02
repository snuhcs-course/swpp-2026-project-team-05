from django.db import migrations, models


PINNED_ARTICLE_URLS = (
    "https://n.news.naver.com/mnews/article/138/0002246164",
    "https://n.news.naver.com/mnews/article/119/0003147364",
    "https://n.news.naver.com/mnews/article/658/0000159637",
    "https://n.news.naver.com/mnews/article/421/0009262290",
    "https://n.news.naver.com/mnews/article/015/0005348718",
    "https://n.news.naver.com/mnews/article/003/0014282136",
)


def set_reviewed_feed_priorities(apps, schema_editor):
    Article = apps.get_model("server", "Article")
    total = len(PINNED_ARTICLE_URLS)
    for index, url in enumerate(PINNED_ARTICLE_URLS):
        Article.objects.filter(canonical_url=url).update(feed_priority=total - index)


def clear_reviewed_feed_priorities(apps, schema_editor):
    Article = apps.get_model("server", "Article")
    Article.objects.filter(canonical_url__in=PINNED_ARTICLE_URLS).update(feed_priority=0)


class Migration(migrations.Migration):
    dependencies = [("server", "0009_user_publisher_follow")]

    operations = [
        migrations.AddField(
            model_name="article",
            name="feed_priority",
            field=models.PositiveSmallIntegerField(db_index=True, default=0),
        ),
        migrations.RunPython(
            set_reviewed_feed_priorities,
            clear_reviewed_feed_priorities,
        ),
        migrations.AlterModelOptions(
            name="article",
            options={"ordering": ["-feed_priority", "-published_at", "-id"]},
        ),
    ]
