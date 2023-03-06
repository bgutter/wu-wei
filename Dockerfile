
# Python 3.11 base image on Debian
FROM python:3.11-buster

# Create /app and install requirements
WORKDIR /app
COPY ./requirements.txt /app/requirements.txt
RUN pip install -r requirements.txt

# Copy in the server code and base environment
COPY ./wu_wei_server /app/wu_wei_server
COPY ./base-environment /app/base-environment

# Copy in base image
ENTRYPOINT [ "python" ]
CMD [ "-m", "wu_wei_server" ]
