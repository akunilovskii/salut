FROM python:3.13-slim@sha256:e48f59cdba69a3cbd9ed2cf0f348d90d45a8d350d98134cf1070bc106ee9ed90
ENV PYTHONDONTWRITEBYTECODE=1
WORKDIR /app
COPY tools/feedback_server.py tools/feedback_delivery.py ./
USER 1001:1001
EXPOSE 8080
CMD ["python", "feedback_server.py", "--bind", "0.0.0.0", "--port", "8080", "--database", "/data/feedback.sqlite"]
