# Generated manually for the FrameLESS news-app account contract.

from django.db import migrations, models
from django.db.models.functions import Lower


class Migration(migrations.Migration):

    dependencies = [
        ("server", "0004_news_domain"),
    ]

    operations = [
        migrations.AddField(
            model_name="useraccount",
            name="email",
            field=models.EmailField(blank=True, max_length=254, null=True),
        ),
        migrations.AddConstraint(
            model_name="useraccount",
            constraint=models.UniqueConstraint(
                Lower("email"),
                condition=models.Q(email__isnull=False),
                name="frameless_user_email_ci_uniq",
            ),
        ),
        migrations.AddConstraint(
            model_name="useraccount",
            constraint=models.UniqueConstraint(
                Lower("username"),
                name="frameless_user_username_ci_uniq",
            ),
        ),
    ]
