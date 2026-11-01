# Reviewed A–G gold set v5

This directory contains the 30 human-reviewed source JSON files used to seed and
regression-test the news Reader. The files retain evaluation-only material such
as negative examples, absent alignments, and equal-wording alignments.

`python manage.py import_gold_dataset server/fixtures/gold_v5 --replace-news`
hydrates the article snapshots, validates every reviewed sentence and marker,
and atomically replaces only the news-domain rows. Accounts, sessions, cached
URL analyses, and analysis history are not deleted.
