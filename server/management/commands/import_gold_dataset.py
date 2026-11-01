from pathlib import Path

from django.core.management.base import BaseCommand, CommandError

from server.gold_dataset import build_product_dataset, load_gold_documents
from server.news_import import (
    DatasetError,
    dataset_digest,
    hydrate_articles,
    import_dataset,
    replace_news_dataset,
)


class Command(BaseCommand):
    help = "Validate, hydrate, and import the reviewed A-G gold dataset."

    def add_arguments(self, parser):
        parser.add_argument("path", type=Path)
        parser.add_argument(
            "--dry-run",
            action="store_true",
            help="Validate all rows and roll the transaction back.",
        )
        parser.add_argument(
            "--replace-news",
            action="store_true",
            help="Atomically replace only Publisher/StoryCluster/Article analysis rows.",
        )
        parser.add_argument(
            "--max-workers",
            type=int,
            default=5,
            help="Maximum concurrent article fetches (default: 5).",
        )

    def handle(self, *args, **options):
        if options["max_workers"] < 1 or options["max_workers"] > 10:
            raise CommandError("max-workers must be between 1 and 10")
        try:
            documents = load_gold_documents(options["path"])
            dataset = build_product_dataset(documents)
            dataset = hydrate_articles(dataset, max_workers=options["max_workers"])
            dataset_hash = dataset_digest(dataset)
            importer = replace_news_dataset if options["replace_news"] else import_dataset
            counts = importer(
                dataset,
                dataset_hash=dataset_hash,
                dry_run=options["dry_run"],
            )
        except (DatasetError, OSError, RuntimeError) as exc:
            raise CommandError(str(exc)) from exc

        action = "replaced" if options["replace_news"] else "imported"
        mode = f"validated ({action}, rolled back)" if options["dry_run"] else action
        self.stdout.write(
            self.style.SUCCESS(
                f"{mode}: publishers={counts.publishers} clusters={counts.clusters} "
                f"articles={counts.articles} propositions={counts.propositions} "
                f"findings={counts.findings} comparisons={counts.comparisons}"
            )
        )
