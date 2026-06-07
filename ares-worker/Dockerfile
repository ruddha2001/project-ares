FROM python:3.12-slim
RUN apt-get update && apt-get install -y \
    git \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app

COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

WORKDIR /app/workspace
RUN cocoindex init

COPY app.py codebase.py document.py ./

RUN useradd -u 8888 aresuser && chown -R aresuser:aresuser /app
USER aresuser

EXPOSE 8000

CMD ["uvicorn", "main:app", "--host", "0.0.0.0", "--port", "8000"]