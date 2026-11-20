FROM python:3.12-slim
WORKDIR /app
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt
COPY tetris_relay.py .
USER nobody
EXPOSE 8765 8766
CMD ["python", "tetris_relay.py", "--host", "0.0.0.0", "--port", "8765", "--direct-port", "8766"]
