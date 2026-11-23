# Feedback receiver

For non-container execution, install the same image decoder as the container
by running `python -m pip install Pillow==13.0.0`. Start the receiver with
`python tools/feedback_server.py --database /data/feedback.sqlite`. The delivery
worker uses the existing token-file configuration and the same SQLite database.

Configure the HTTPS proxy to allow at least 15,761,424 request body bytes and
at least 60 seconds for the client upload and upstream response. Preserve the
body and Content-Type for POST requests to `/v1/feedback`. Keep the existing
trusted-proxy and access-log settings.
