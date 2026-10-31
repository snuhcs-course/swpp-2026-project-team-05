import uuid
from datetime import datetime, timezone

from django.core.exceptions import ValidationError
from django.test import SimpleTestCase

from server.models import (
    Article,
    ExpressionFinding,
    ExpressionOwner,
    FindingComparison,
    NewsCategory,
    Publisher,
    SharedProposition,
    StoryCluster,
    TextScope,
)


class NewsAnalysisModelTests(SimpleTestCase):
    def setUp(self):
        self.publisher = Publisher(
            id=uuid.uuid4(), code="publisher", name_ko="언론사", domain="example.com"
        )
        self.cluster = StoryCluster(
            id=uuid.uuid4(), internal_label="테스트 사건", category=NewsCategory.POLITICS
        )
        self.other_cluster = StoryCluster(
            id=uuid.uuid4(), internal_label="다른 사건", category=NewsCategory.POLITICS
        )
        self.source = self._article(
            cluster=self.cluster,
            url="https://example.com/source",
            body="정부는 정책을 전격 추진한다고 밝혔다. 후속 협의는 다음 주 열린다.",
        )
        self.target = self._article(
            cluster=self.cluster,
            url="https://example.com/target",
            body="정부는 정책을 빠르게 추진할 계획이라고 설명했다.",
        )
        self.proposition = SharedProposition(
            id=uuid.uuid4(),
            story_cluster=self.cluster,
            normalized_proposition="정부가 정책을 추진한다",
        )

    def _article(self, *, cluster, url, body):
        return Article(
            id=uuid.uuid4(),
            publisher=self.publisher,
            story_cluster=cluster,
            canonical_url=url,
            title="기사 제목",
            body_text=body,
            content_hash="0" * 64,
            category=NewsCategory.POLITICS,
            published_at=datetime(2026, 10, 31, tzinfo=timezone.utc),
        )

    def _finding(self, **overrides):
        sentence = "정부는 정책을 전격 추진한다고 밝혔다."
        finding = ExpressionFinding(
            id=uuid.uuid4(),
            article=self.source,
            source_scope=TextScope.BODY,
            source_sentence_start=0,
            source_sentence_end=len(sentence),
            source_sentence_text=sentence,
            marker_start=8,
            marker_end=10,
            marker_text="전격",
            categories=["D"],
            explanation="속도와 결단성을 강조한다.",
            shared_proposition=self.proposition,
        )
        for field, value in overrides.items():
            setattr(finding, field, value)
        return finding

    def test_finding_accepts_verified_sentence_and_marker_offsets(self):
        self._finding().clean()

    def test_finding_rejects_marker_text_that_does_not_match_offsets(self):
        with self.assertRaises(ValidationError):
            self._finding(marker_text="신속").clean()

    def test_finding_rejects_unknown_or_duplicate_categories(self):
        for categories in (["H"], ["A", "A"], []):
            with self.subTest(categories=categories), self.assertRaises(ValidationError):
                self._finding(categories=categories).clean()

    def test_finding_rejects_proposition_from_another_cluster(self):
        proposition = SharedProposition(
            id=uuid.uuid4(),
            story_cluster=self.other_cluster,
            normalized_proposition="다른 사건의 명제",
        )
        with self.assertRaises(ValidationError):
            self._finding(shared_proposition=proposition).clean()

    def test_comparison_accepts_verified_target_in_same_cluster(self):
        finding = self._finding()
        sentence = self.target.body_text
        FindingComparison(
            id=uuid.uuid4(),
            expression_finding=finding,
            target_article=self.target,
            target_scope=TextScope.BODY,
            target_sentence_start=0,
            target_sentence_end=len(sentence),
            target_sentence_text=sentence,
            target_marker_start=8,
            target_marker_end=11,
            target_marker_text="빠르게",
            target_owner=ExpressionOwner.REPORTER,
            comparison_summary="같은 추진을 더 일반적인 속도 표현으로 전한다.",
        ).clean()

    def test_comparison_requires_a_shared_proposition(self):
        finding = self._finding(shared_proposition=None)
        comparison = FindingComparison(
            id=uuid.uuid4(),
            expression_finding=finding,
            target_article=self.target,
            target_scope=TextScope.BODY,
            target_sentence_start=0,
            target_sentence_end=len(self.target.body_text),
            target_sentence_text=self.target.body_text,
            target_marker_start=8,
            target_marker_end=11,
            target_marker_text="빠르게",
            target_owner=ExpressionOwner.REPORTER,
            comparison_summary="표현 차이",
        )
        with self.assertRaises(ValidationError):
            comparison.clean()

    def test_comparison_rejects_target_from_another_cluster(self):
        other_target = self._article(
            cluster=self.other_cluster,
            url="https://example.com/other",
            body=self.target.body_text,
        )
        comparison = FindingComparison(
            id=uuid.uuid4(),
            expression_finding=self._finding(),
            target_article=other_target,
            target_scope=TextScope.BODY,
            target_sentence_start=0,
            target_sentence_end=len(other_target.body_text),
            target_sentence_text=other_target.body_text,
            target_marker_start=8,
            target_marker_end=11,
            target_marker_text="빠르게",
            target_owner=ExpressionOwner.REPORTER,
            comparison_summary="표현 차이",
        )
        with self.assertRaises(ValidationError):
            comparison.clean()
