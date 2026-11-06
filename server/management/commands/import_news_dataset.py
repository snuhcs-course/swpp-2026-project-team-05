from pathlib import Path

from django.core.management.base import BaseCommand, CommandError

from server.news_import import (
    DatasetError,
    dataset_digest,
    hydrate_articles,
    import_dataset,
    load_dataset,
)


class Command(BaseCommand):
    help = "Validate and atomically import a reviewed news-analysis JSON dataset."

    def add_arguments(self, parser):
        parser.add_argument("path", type=Path)
        parser.add_argument(
            "--dry-run",
            action="store_true",
            help="Validate all rows and roll the transaction back instead of persisting them.",
        )
        parser.add_argument(
            "--fetch-articles",
            action="store_true",
            help="Fetch current titles and article bodies before validating and importing.",
        )

    def handle(self, *args, **options):
        try:
            dataset, dataset_hash = load_dataset(options["path"])
            if options["fetch_articles"]:
                dataset = hydrate_articles(dataset)
                dataset_hash = dataset_digest(dataset)
            counts = import_dataset(
                dataset,
                dataset_hash=dataset_hash,
                dry_run=options["dry_run"],
            )
        except (DatasetError, OSError, RuntimeError) as exc:
            raise CommandError(str(exc)) from exc

        mode = "validated (rolled back)" if options["dry_run"] else "imported"
        self.stdout.write(
            self.style.SUCCESS(
                f"{mode}: publishers={counts.publishers} clusters={counts.clusters} "
                f"articles={counts.articles} propositions={counts.propositions} "
                f"findings={counts.findings} comparisons={counts.comparisons}"
            )
        )
