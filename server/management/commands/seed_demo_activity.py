import json

from django.core.management.base import BaseCommand

from server.demo_activity import seed_demo_activity


class Command(BaseCommand):
    help = "Preview or replace activity for 20 reserved, non-login demo accounts."

    def add_arguments(self, parser):
        parser.add_argument("--apply", action="store_true", help="Write the seed; default is a read-only preview.")
        parser.add_argument("--clear", action="store_true", help="Remove only this seed's accounts and activity.")

    def handle(self, *args, **options):
        result = seed_demo_activity(apply=options["apply"], clear=options["clear"])
        self.stdout.write(json.dumps(result))
