FROM python:3.11-slim-bullseye
ENV PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1 \
    PIP_NO_CACHE_DIR=on \
    TZ=Asia/Kolkata


RUN apt-get update && apt-get install -y --no-install-recommends \
    aria2 \
    ffmpeg \
    procps \
    ca-certificates \
    tzdata \
    && ln -snf /usr/share/zoneinfo/$TZ /etc/localtime \
    && echo $TZ > /etc/timezone \
    && apt-get clean \
    && rm -rf /var/lib/apt/lists/*

RUN useradd -m -u 1000 appuser

WORKDIR /app
COPY main.py requirements.txt /app/

RUN pip install --no-cache-dir -r requirements.txt

RUN mkdir -p /app/downloads \
    && chown -R appuser:appuser /app/downloads \
    && chmod 755 /app/downloads

COPY . /app/
USER appuser

EXPOSE 8080

CMD ["python", "main.py"]