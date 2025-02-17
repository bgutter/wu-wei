# Start from the Clojure image
FROM clojure:tools-deps

# Set working directory in Docker container
WORKDIR /app

# Copy project files into the container
COPY . .

# Go into frontend dir
# This should be cleaned up some day
WORKDIR ./frontend/wu-wei

# Install dependencies
RUN clojure -Stree

# Expose a port for the app
EXPOSE 3449
EXPOSE 9500

# Set the startup command
CMD [ "clj", "-M", "--main",  "figwheel.main", "--build", "wu-wei", "--repl" ]

