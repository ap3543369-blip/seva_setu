FROM python:3.12-slim
WORKDIR /app
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt
COPY forecast.py service.py ./
ENV ECOAI_HOST=0.0.0.0 ECOAI_PORT=5001
EXPOSE 5001
CMD ["python", "service.py"]
