FROM python:3.12-slim

WORKDIR /app

COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

COPY app/ ./app/
RUN useradd --system --create-home --home-dir /home/knowflow --shell /usr/sbin/nologin knowflow \
    && chown -R knowflow:knowflow /app
USER knowflow

CMD ["python", "-m", "app.main"]
