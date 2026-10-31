"""Database models for accounts, legacy URL analysis, and the news product."""

import uuid

from django.core.exceptions import ValidationError
from django.db import models
from django.db.models.functions import Lower


class NewsCategory(models.TextChoices):
    POLITICS = "POLITICS", "Politics"
    SOCIETY = "SOCIETY", "Society"


class AnalysisStatus(models.TextChoices):
    NOT_STARTED = "NOT_STARTED", "Not started"
    UPDATING = "UPDATING", "Updating"
    READY = "READY", "Ready"
    PARTIAL = "PARTIAL", "Partial"
    FAILED = "FAILED", "Failed"


class TextScope(models.TextChoices):
    TITLE = "TITLE", "Title"
    BODY = "BODY", "Body"


class ArticleVisibility(models.TextChoices):
    VISIBLE = "VISIBLE", "Visible"
    HIDDEN = "HIDDEN", "Hidden"


class ExpressionOwner(models.TextChoices):
    REPORTER = "REPORTER", "Reporter"
    ATTRIBUTED_SOURCE = "ATTRIBUTED_SOURCE", "Attributed source"
    OFFICIAL_STATEMENT = "OFFICIAL_STATEMENT", "Official statement"


EXPRESSION_CATEGORIES = frozenset("ABCDEFG")


def _validate_text_span(*, text: str, start: int, end: int, quote: str, label: str) -> None:
    if start < 0 or end <= start or end > len(text):
        raise ValidationError({label: f"{label} offsets are outside the selected article text"})
    if text[start:end] != quote:
        raise ValidationError({label: f"{label} text does not match its offsets"})


def _validate_categories(categories: object) -> None:
    if not isinstance(categories, list) or not categories:
        raise ValidationError({"categories": "categories must be a non-empty list"})
    if len(categories) != len(set(categories)) or not set(categories) <= EXPRESSION_CATEGORIES:
        raise ValidationError({"categories": "categories must contain distinct A-G codes"})


class CachedAnalysis(models.Model):
    key = models.CharField(max_length=64, primary_key=True)
    source_url = models.URLField(max_length=2048)
    result = models.JSONField()
    execution_status = models.CharField(max_length=16)
    created_at = models.DateTimeField(auto_now_add=True)
    expires_at = models.DateTimeField(db_index=True)

    class Meta:
        db_table = "frameless_cached_analysis"


class UserAccount(models.Model):
    """A small first-party account, deliberately independent of Django admin."""

    id = models.UUIDField(primary_key=True, default=uuid.uuid4, editable=False)
    email = models.EmailField(max_length=254, null=True, blank=True)
    username = models.CharField(max_length=64, unique=True)
    password_hash = models.CharField(max_length=256)
    is_developer = models.BooleanField(default=False)
    created_at = models.DateTimeField(auto_now_add=True)
    updated_at = models.DateTimeField(auto_now=True)

    class Meta:
        db_table = "frameless_user_account"
        constraints = [
            models.UniqueConstraint(
                Lower("email"),
                condition=models.Q(email__isnull=False),
                name="frameless_user_email_ci_uniq",
            ),
            models.UniqueConstraint(
                Lower("username"),
                name="frameless_user_username_ci_uniq",
            ),
        ]


class AuthSession(models.Model):
    """Only a SHA-256 digest of a bearer token is kept in PostgreSQL."""

    id = models.UUIDField(primary_key=True, default=uuid.uuid4, editable=False)
    user = models.ForeignKey(UserAccount, on_delete=models.CASCADE, related_name="sessions")
    token_hash = models.CharField(max_length=64, unique=True)
    created_at = models.DateTimeField(auto_now_add=True)
    expires_at = models.DateTimeField(db_index=True)

    class Meta:
        db_table = "frameless_auth_session"


class AnalysisRecord(models.Model):
    """The latest usable analysis of one source URL for an account."""

    id = models.UUIDField(primary_key=True, default=uuid.uuid4, editable=False)
    user = models.ForeignKey(UserAccount, null=True, blank=True, on_delete=models.CASCADE,
                             related_name="analysis_records")
    # Used only to claim records created before account login was added.
    owner_key = models.CharField(max_length=64)
    source_url = models.URLField(max_length=2048)
    source_url_hash = models.CharField(max_length=64)
    source_title = models.CharField(max_length=512)
    result = models.JSONField()
    execution_status = models.CharField(max_length=16)
    result_version = models.CharField(max_length=32, default="v1")
    created_at = models.DateTimeField(auto_now_add=True)
    updated_at = models.DateTimeField(auto_now=True)

    class Meta:
        db_table = "frameless_analysis_record"
        ordering = ["-updated_at"]
        constraints = [
            models.UniqueConstraint(
                fields=["owner_key", "source_url_hash"],
                name="frameless_history_owner_url_uniq",
            )
        ]
        indexes = [
            models.Index(fields=["owner_key", "-updated_at"], name="frameless_history_recent"),
            models.Index(fields=["user", "-updated_at"], name="frameless_history_user_recent"),
        ]


class Publisher(models.Model):
    """A supported news publisher."""

    id = models.UUIDField(primary_key=True, default=uuid.uuid4, editable=False)
    code = models.SlugField(max_length=64, unique=True)
    name_ko = models.CharField(max_length=128)
    domain = models.CharField(max_length=255)
    logo_url = models.URLField(max_length=2048, null=True, blank=True)
    is_active = models.BooleanField(default=True)
    display_order = models.PositiveIntegerField(default=0)

    class Meta:
        db_table = "frameless_publisher"
        ordering = ["display_order", "name_ko"]


class StoryCluster(models.Model):
    """Articles about one event, with one incrementally maintained analysis."""

    id = models.UUIDField(primary_key=True, default=uuid.uuid4, editable=False)
    internal_label = models.CharField(max_length=255, blank=True)
    category = models.CharField(max_length=16, choices=NewsCategory.choices)
    analysis_status = models.CharField(
        max_length=16, choices=AnalysisStatus.choices, default=AnalysisStatus.NOT_STARTED,
    )
    analysis_version = models.CharField(max_length=64, blank=True)
    analysis_input_hash = models.CharField(max_length=64, blank=True)
    analyzed_at = models.DateTimeField(null=True, blank=True)
    created_at = models.DateTimeField(auto_now_add=True)
    updated_at = models.DateTimeField(auto_now=True)

    class Meta:
        db_table = "frameless_story_cluster"
        indexes = [
            models.Index(fields=["category", "-updated_at"], name="frameless_cluster_feed"),
        ]


class Article(models.Model):
    """A stored publisher article shown in the news feed and Reader."""

    id = models.UUIDField(primary_key=True, default=uuid.uuid4, editable=False)
    publisher = models.ForeignKey(Publisher, on_delete=models.PROTECT, related_name="articles")
    story_cluster = models.ForeignKey(
        StoryCluster, null=True, blank=True, on_delete=models.SET_NULL, related_name="articles",
    )
    canonical_url = models.URLField(max_length=2048, unique=True)
    source_article_id = models.CharField(max_length=128, blank=True)
    title = models.CharField(max_length=512)
    body_text = models.TextField()
    content_hash = models.CharField(max_length=64)
    category = models.CharField(max_length=16, choices=NewsCategory.choices)
    published_at = models.DateTimeField()
    visibility = models.CharField(
        max_length=16, choices=ArticleVisibility.choices, default=ArticleVisibility.VISIBLE,
    )
    created_at = models.DateTimeField(auto_now_add=True)
    updated_at = models.DateTimeField(auto_now=True)

    class Meta:
        db_table = "frameless_article"
        ordering = ["-published_at", "-id"]
        indexes = [
            models.Index(
                fields=["story_cluster", "-published_at"], name="frameless_article_cluster",
            ),
            models.Index(
                fields=["category", "visibility", "-published_at"],
                name="frameless_article_feed",
            ),
        ]


class SharedProposition(models.Model):
    """The exact meaning shared by one finding and its comparison targets."""

    id = models.UUIDField(primary_key=True, default=uuid.uuid4, editable=False)
    story_cluster = models.ForeignKey(
        StoryCluster, on_delete=models.CASCADE, related_name="shared_propositions",
    )
    normalized_proposition = models.TextField()
    created_at = models.DateTimeField(auto_now_add=True)

    class Meta:
        db_table = "frameless_shared_proposition"
        constraints = [
            models.UniqueConstraint(
                fields=["story_cluster", "normalized_proposition"],
                name="frameless_cluster_proposition_uniq",
            ),
        ]


class ExpressionFinding(models.Model):
    """One verified A-G marker and its containing source sentence."""

    id = models.UUIDField(primary_key=True, default=uuid.uuid4, editable=False)
    article = models.ForeignKey(Article, on_delete=models.CASCADE, related_name="findings")
    source_scope = models.CharField(max_length=8, choices=TextScope.choices)
    source_sentence_start = models.PositiveIntegerField()
    source_sentence_end = models.PositiveIntegerField()
    source_sentence_text = models.TextField()
    marker_start = models.PositiveIntegerField()
    marker_end = models.PositiveIntegerField()
    marker_text = models.TextField()
    categories = models.JSONField()
    explanation = models.TextField()
    display_order = models.PositiveIntegerField(default=0)
    shared_proposition = models.ForeignKey(
        SharedProposition,
        null=True,
        blank=True,
        on_delete=models.RESTRICT,
        related_name="findings",
    )

    class Meta:
        db_table = "frameless_expression_finding"
        ordering = ["display_order", "source_sentence_start", "marker_start"]
        constraints = [
            models.UniqueConstraint(
                fields=["article", "source_scope", "marker_start", "marker_end"],
                name="frameless_article_marker_uniq",
            ),
            models.CheckConstraint(
                condition=models.Q(source_sentence_end__gt=models.F("source_sentence_start")),
                name="frameless_source_sentence_range",
            ),
            models.CheckConstraint(
                condition=models.Q(marker_end__gt=models.F("marker_start")),
                name="frameless_source_marker_range",
            ),
            models.CheckConstraint(
                condition=(models.Q(marker_start__gte=models.F("source_sentence_start"))
                           & models.Q(marker_end__lte=models.F("source_sentence_end"))),
                name="frameless_marker_in_sentence",
            ),
        ]
        indexes = [
            models.Index(fields=["article", "display_order"], name="frameless_finding_article"),
        ]

    def clean(self):
        super().clean()
        selected_text = self.article.title if self.source_scope == TextScope.TITLE else self.article.body_text
        _validate_text_span(
            text=selected_text,
            start=self.source_sentence_start,
            end=self.source_sentence_end,
            quote=self.source_sentence_text,
            label="source_sentence_text",
        )
        _validate_text_span(
            text=selected_text,
            start=self.marker_start,
            end=self.marker_end,
            quote=self.marker_text,
            label="marker_text",
        )
        if not (self.source_sentence_start <= self.marker_start
                and self.marker_end <= self.source_sentence_end):
            raise ValidationError({"marker_text": "marker must be inside the source sentence"})
        _validate_categories(self.categories)
        if self.shared_proposition_id and (
            self.article.story_cluster_id != self.shared_proposition.story_cluster_id
        ):
            raise ValidationError({"shared_proposition": "finding and proposition must share a cluster"})


class FindingComparison(models.Model):
    """A verified wording contrast between one finding and one target sentence."""

    id = models.UUIDField(primary_key=True, default=uuid.uuid4, editable=False)
    expression_finding = models.ForeignKey(
        ExpressionFinding, on_delete=models.CASCADE, related_name="comparisons",
    )
    target_article = models.ForeignKey(
        Article, on_delete=models.CASCADE, related_name="comparison_targets",
    )
    target_scope = models.CharField(max_length=8, choices=TextScope.choices)
    target_sentence_start = models.PositiveIntegerField()
    target_sentence_end = models.PositiveIntegerField()
    target_sentence_text = models.TextField()
    target_marker_start = models.PositiveIntegerField()
    target_marker_end = models.PositiveIntegerField()
    target_marker_text = models.TextField()
    target_owner = models.CharField(max_length=24, choices=ExpressionOwner.choices)
    target_speaker_label = models.CharField(max_length=255, null=True, blank=True)
    comparison_summary = models.TextField()
    display_rank = models.PositiveIntegerField(default=0)

    class Meta:
        db_table = "frameless_finding_comparison"
        ordering = ["display_rank", "id"]
        constraints = [
            models.UniqueConstraint(
                fields=[
                    "expression_finding", "target_article", "target_scope",
                    "target_sentence_start", "target_sentence_end",
                ],
                name="frameless_finding_target_uniq",
            ),
            models.CheckConstraint(
                condition=models.Q(target_sentence_end__gt=models.F("target_sentence_start")),
                name="frameless_target_sentence_range",
            ),
            models.CheckConstraint(
                condition=models.Q(target_marker_end__gt=models.F("target_marker_start")),
                name="frameless_target_marker_range",
            ),
            models.CheckConstraint(
                condition=(models.Q(target_marker_start__gte=models.F("target_sentence_start"))
                           & models.Q(target_marker_end__lte=models.F("target_sentence_end"))),
                name="frameless_target_marker_in_sentence",
            ),
        ]
        indexes = [
            models.Index(
                fields=["expression_finding", "display_rank"],
                name="frameless_comparison_finding",
            ),
        ]

    def clean(self):
        super().clean()
        finding = self.expression_finding
        if not finding.shared_proposition_id:
            raise ValidationError({"expression_finding": "comparison requires a shared proposition"})
        if self.target_article_id == finding.article_id:
            raise ValidationError({"target_article": "target article must differ from source article"})
        if not finding.article.story_cluster_id or (
            self.target_article.story_cluster_id != finding.article.story_cluster_id
        ):
            raise ValidationError({"target_article": "source and target must share a cluster"})
        selected_text = (
            self.target_article.title if self.target_scope == TextScope.TITLE
            else self.target_article.body_text
        )
        _validate_text_span(
            text=selected_text,
            start=self.target_sentence_start,
            end=self.target_sentence_end,
            quote=self.target_sentence_text,
            label="target_sentence_text",
        )
        _validate_text_span(
            text=selected_text,
            start=self.target_marker_start,
            end=self.target_marker_end,
            quote=self.target_marker_text,
            label="target_marker_text",
        )
        if not (self.target_sentence_start <= self.target_marker_start
                and self.target_marker_end <= self.target_sentence_end):
            raise ValidationError({"target_marker_text": "target marker must be inside the sentence"})
