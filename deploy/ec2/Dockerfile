FROM python:3.14-slim

ENV PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1 \
    DJANGO_ENV=production

WORKDIR /app

COPY requirements.txt ./
RUN pip install --no-cache-dir -r requirements.txt

COPY manage.py ./
COPY server/ ./server/

CMD ["sh", "-c", "exec gunicorn server.wsgi:application --bind 0.0.0.0:${PORT:-8080} --workers 1 --threads 2 --timeout 650"]
