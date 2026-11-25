"""Explicit, replaceable demo activity; never runs on an application request."""

from datetime import timedelta
from hashlib import sha256
import random
from uuid import NAMESPACE_URL, uuid5

from django.core.management.base import CommandError
from django.db import IntegrityError, transaction
from django.db.models import Case, CharField, F, Q, Value, When, Window
from django.db.models.functions import Cast, Concat, Lower, RowNumber
from django.utils import timezone

from .models import Article, ArticleReaction, ArticleView, ReactionKind, TrendState, UserAccount
from .trends import latest_keys

ACCOUNT_COUNT = 20
TOPIC_LIMIT = 60
PASSWORD_MARKER = "!frameless-demo-activity-v1"


def demo_accounts():
    return [UserAccount(
        id=uuid5(NAMESPACE_URL, f"https://frameless.example.invalid/demo-activity/v1/{i}"),
        username=f"test_activity_{i:02d}", email=f"activity-{i:02d}@frameless.example.invalid",
        password_hash=PASSWORD_MARKER, is_developer=False,
    ) for i in range(1, ACCOUNT_COUNT + 1)]


def demo_viewer_key(account_id):
    # Ordinary API keys are hex-only. This reserved namespace is identifiable,
    # deterministic, and cannot collide with a live account/guest viewer key.
    return "demo:" + sha256(str(account_id).encode()).hexdigest()[:59]


def _check_ownership(accounts):
    expected = {row.pk: row for row in accounts}
    rows = UserAccount.objects.annotate(normalized_username=Lower("username"), normalized_email=Lower("email")).filter(
        Q(pk__in=expected) | Q(normalized_username__in=[row.username for row in accounts]) |
        Q(normalized_email__in=[row.email for row in accounts])
    )
    for row in rows:
        wanted = expected.get(row.pk)
        if wanted is None or (row.username, row.email, row.password_hash, row.is_developer) != (
            wanted.username, wanted.email, PASSWORD_MARKER, False
        ):
            raise CommandError("A reserved demo identity is occupied or modified; no data was changed.")


def _topic_articles(keys):
    clusters = [key.split(":", 1)[1] for key in keys if key.startswith("cluster:")]
    singles = [key.split(":", 1)[1] for key in keys if key.startswith("article:")]
    rows = Article.objects.filter(
        Q(story_cluster_id__in=clusters) | Q(pk__in=singles),
        visibility="VISIBLE", publisher__is_active=True,
    ).annotate(topic_key=Case(
        When(story_cluster__isnull=False,
             then=Concat(Value("cluster:"), Cast("story_cluster_id", CharField()))),
        default=Concat(Value("article:"), Cast("pk", CharField())), output_field=CharField(),
    )).annotate(position=Window(
        expression=RowNumber(), partition_by=[F("topic_key")],
        order_by=[F("is_feed_visible").desc(), F("published_at").desc(), F("id").asc()],
    )).filter(position__lte=3).order_by("topic_key", "position").values("id", "story_cluster_id")
    grouped = {}
    for row in rows:
        key = f"cluster:{row['story_cluster_id']}" if row["story_cluster_id"] else f"article:{row['id']}"
        grouped.setdefault(key, []).append(row["id"])
    return grouped


def _activity(accounts, keys, now):
    grouped = _topic_articles(keys)
    identities = {account.pk: demo_viewer_key(account.pk) for account in accounts}
    views, reactions = [], []
    kinds = ReactionKind.values
    for index, key in enumerate(keys):
        articles = grouped.get(key, [])
        if not articles or index % 7 == 6:
            continue
        rng = random.Random(20261125 + index)
        readers = rng.sample(accounts, len(accounts))
        view_count = [20, 17, 14, 11, 8][index] if index < 5 else 1 + index % 7
        reaction_count = ([8, 14, 6, 18, 10][index] if index < 5 else
                          (1 if index % 11 == 10 else index % (view_count + 1)))
        # Some readers react to an older read; a reaction is not itself a qualifying view.
        for user_index, account in enumerate(readers[:max(view_count, reaction_count)]):
            for article_index, article_id in enumerate(articles):
                if article_index > 0 and user_index % (3 + article_index) != 0:
                    continue
                if user_index < view_count:
                    age = 240 + rng.randrange(60) if index % 9 == 8 else rng.randrange(1, 161)
                    views.append(ArticleView(article_id=article_id, viewer_key=identities[account.pk],
                                             last_viewed_at=now - timedelta(minutes=age)))
                if user_index < reaction_count:
                    age = 30 * 60 + rng.randrange(120) if index % 11 == 10 else rng.randrange(1, 23 * 60)
                    reactions.append(ArticleReaction(article_id=article_id, user_id=account.pk,
                        kind=kinds[(index + user_index + article_index) % len(kinds)],
                        reacted_at=now - timedelta(minutes=age)))
    return views, reactions, identities


@transaction.atomic
def seed_demo_activity(*, apply=False, clear=False):
    now = timezone.now()
    accounts = demo_accounts()
    _check_ownership(accounts)
    keys = [] if clear else latest_keys(TOPIC_LIMIT, set())
    if not clear and not keys:
        raise CommandError("No discoverable topics; no data was changed.")
    views, reactions, identities = _activity(accounts, keys, now)
    report = {"mode": "clear" if clear else "replace", "applied": apply,
              "accounts": 0 if clear else len(accounts), "topics": len(keys),
              "views": len(views), "reactions": len(reactions)}
    if not apply:
        return report
    # Lock only these accounts, in stable order. A first-create identity conflict
    # rolls back the command rather than adopting an unrelated account.
    try:
        existing = set(UserAccount.objects.filter(pk__in=[a.pk for a in accounts]).values_list("pk", flat=True))
        if not clear:
            UserAccount.objects.bulk_create([a for a in accounts if a.pk not in existing])
        list(UserAccount.objects.select_for_update().filter(pk__in=[a.pk for a in accounts]).order_by("pk"))
        _check_ownership(accounts)
        ArticleView.objects.filter(viewer_key__in=identities.values()).delete()
        ArticleReaction.objects.filter(user_id__in=[a.pk for a in accounts]).delete()
        if clear:
            UserAccount.objects.filter(pk__in=[a.pk for a in accounts]).delete()
        else:
            ArticleView.objects.bulk_create(views)
            ArticleReaction.objects.bulk_create(reactions)
        TrendState.objects.filter(pk=1).update(next_refresh_at=now)
    except IntegrityError as failure:
        raise CommandError("Demo identity or activity conflict; the operation was rolled back.") from failure
    return report
