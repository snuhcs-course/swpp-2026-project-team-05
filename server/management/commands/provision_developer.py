from django.core.management.base import BaseCommand, CommandError

from server.analysis_history import claim_legacy_history, owner_key_for_request
from server.authentication import provision_developer


class Command(BaseCommand):
    help = "Create or rotate a developer account and optionally claim a legacy device history."

    def add_arguments(self, parser):
        parser.add_argument("--username", required=True)
        parser.add_argument("--password", required=True)
        parser.add_argument("--claim-client-id")

    def handle(self, *args, **options):
        username, password = options["username"].strip(), options["password"]
        if not 3 <= len(username) <= 64 or len(password) < 12:
            raise CommandError("username must be 3–64 characters and password at least 12 characters")
        user = provision_developer(username, password)
        client_id = options.get("claim_client_id")
        legacy_key = None
        if client_id:
            class Request: META = {"HTTP_X_FRAMELESS_CLIENT_ID": client_id}
            legacy_key = owner_key_for_request(Request())
            if legacy_key is None:
                raise CommandError("claim-client-id must be a UUID")
        claimed = claim_legacy_history(user, legacy_key)
        self.stdout.write(self.style.SUCCESS(f"developer={user.username} claimed_records={claimed}"))
