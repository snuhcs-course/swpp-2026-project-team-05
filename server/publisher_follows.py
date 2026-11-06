"""Atomic replacement of one account's explicitly followed publishers."""

from __future__ import annotations

from django.db import transaction

from .models import (
    ArticleVisibility,
    Publisher,
    UserAccount,
    UserPublisherFollow,
)


class InvalidPublisherCodes(ValueError):
    def __init__(self, codes: set[str]):
        super().__init__("one or more publishers cannot be followed")
        self.codes = codes


def selectable_publishers():
    return (Publisher.objects
            .filter(
                is_active=True,
                articles__visibility=ArticleVisibility.VISIBLE,
                articles__is_feed_visible=True,
            )
            .distinct())


@transaction.atomic
def replace_followed_publishers(*, user: UserAccount, codes: list[str]) -> list[str]:
    """Replace the complete follow set, serialized per user."""

    UserAccount.objects.select_for_update().get(pk=user.pk)
    normalized = list(dict.fromkeys(codes))
    publishers = list(
        selectable_publishers()
        .filter(code__in=normalized)
        .order_by("display_order", "name_ko", "code")
    )
    known = {publisher.code for publisher in publishers}
    invalid = set(normalized) - known
    if invalid:
        raise InvalidPublisherCodes(invalid)

    desired_ids = {publisher.id for publisher in publishers}
    existing_ids = set(
        UserPublisherFollow.objects.filter(user=user)
        .values_list("publisher_id", flat=True)
    )
    UserPublisherFollow.objects.filter(user=user).exclude(
        publisher_id__in=desired_ids
    ).delete()
    UserPublisherFollow.objects.bulk_create([
        UserPublisherFollow(user=user, publisher_id=publisher_id)
        for publisher_id in desired_ids - existing_ids
    ])
    return [publisher.code for publisher in publishers]
