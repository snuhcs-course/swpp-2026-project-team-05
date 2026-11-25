import json

from django.core.management.base import BaseCommand, CommandError

from server.trends import cleanup_views


class Command(BaseCommand):
    help = "Remove expired recent-read rows in bounded batches."

    def handle(self, *args, **options):
        result = cleanup_views()
        self.stdout.write(json.dumps(result))
        if result["remaining"]:
            raise CommandError("Cleanup budget reached or rows are locked; rerun to finish.")
