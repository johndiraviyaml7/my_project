FROM python:3.12-slim

# System deps for psycopg2 + healthchecks
RUN apt-get update && apt-get install -y --no-install-recommends \
        libpq5 curl \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app

# Install Python deps first for layer caching
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

# Copy source
COPY . .

# Working dirs (also created at runtime by settings.py, but pre-create for clarity)
RUN mkdir -p /app/data /app/output/deid_dicom /app/logs /app/input

ENV PYTHONUNBUFFERED=1 \
    PYTHONPATH=/app

EXPOSE 8000

# Default command runs the FastAPI service.
# To run the one-shot pipeline instead, override with:
#   docker compose run --rm dicom_parser python run_pipeline.py
CMD ["uvicorn", "api.main:app", "--host", "0.0.0.0", "--port", "8000"]
