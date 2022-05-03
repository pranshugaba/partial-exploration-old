# docker build -t gitlab.lrz.de:5005/i7/partial-exploration:$(sha256sum < Dockerfile | cut -c -8) --compress - < Dockerfile

FROM alpine:latest

# Add build dependencies for PRISM

RUN apk --update --no-cache add \
    autoconf \
    automake \
    file \
    g++ \
    gcc \
    libtool \
    make \
    patch \
    python3 \
    subversion \
    swig \
    unzip \
    wget \
    git \
    openssh \
    gradle \
    openjdk11 \
    py3-numpy \
    py3-matplotlib


# Copy all the contents in the current directory and paste it into the container
COPY . ./home

# Make project root as working directory
WORKDIR ./home/

# Make prism file and run gradle
RUN cd lib/models/lib/prism/prism && \
    make && \
    cd ../../../../.. && \
    ./gradlew compileJava

