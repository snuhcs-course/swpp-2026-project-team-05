from concurrent.futures import ThreadPoolExecutor
from threading import Barrier
from unittest import skipUnless

from django.db import close_old_connections, connection
from django.test import TransactionTestCase
from django.utils import timezone

from server.models import Article, ArticleReaction, Publisher, UserAccount
from server.reactions import set_reaction


@skipUnless(connection.vendor == "postgresql", "Requires the isolated PostgreSQL run")
class ReactionConcurrencyTests(TransactionTestCase):
    def setUp(self):
        publisher = Publisher.objects.create(code="news", name_ko="뉴스", domain="example.com")
        self.article = Article.objects.create(publisher=publisher, canonical_url="https://example.com/a",
            title="기사", body_text="본문", content_hash="0" * 64, category="SOCIETY", published_at=timezone.now())
        self.user = UserAccount.objects.create(username="one", password_hash="unused")
        self.other = UserAccount.objects.create(username="two", password_hash="unused")

    def concurrent(self, operations):
        barrier = Barrier(len(operations))
        def write(operation):
            close_old_connections()
            try:
                barrier.wait(timeout=5)
                user, kind = operation
                return set_reaction(article_id=self.article.pk, user=user, kind=kind)
            finally:
                close_old_connections()
        with ThreadPoolExecutor(max_workers=len(operations)) as pool:
            return list(pool.map(write, operations))

    def test_same_account_simultaneous_creates_and_put_delete_are_serialized(self):
        results = self.concurrent([(self.user, "LIKE"), (self.user, "SAD"), (self.user, "ANGRY")])
        self.assertEqual(ArticleReaction.objects.count(), 1)
        self.assertTrue(all(sum(r["counts"].values()) == 1 for r in results))
        self.concurrent([(self.user, None), (self.user, "MOVED")])
        self.assertLessEqual(ArticleReaction.objects.count(), 1)
        if ArticleReaction.objects.exists():
            self.assertEqual(ArticleReaction.objects.get().kind, "MOVED")

    def test_two_accounts_keep_two_independent_choices(self):
        self.concurrent([(self.user, "LIKE"), (self.other, "SAD")])
        self.assertEqual(set(ArticleReaction.objects.values_list("user_id", "kind")),
                         {(self.user.pk, "LIKE"), (self.other.pk, "SAD")})
