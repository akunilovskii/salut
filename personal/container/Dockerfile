FROM mcr.microsoft.com/openjdk/jdk@sha256:0658ac57a474971bc86d5b93c8493566f20cea4634f9df120559c82e461f0fd7 AS java
FROM ubuntu:24.04@sha256:73b999393d07f2bf65a40373778a44dc9a6d54ad7718d29247d8ff9413c4d6a8

LABEL org.opencontainers.image.source="https://github.com/akunilovskii/salut"
LABEL org.opencontainers.image.description="Salut Android build and test environment"
SHELL ["/bin/bash", "-o", "pipefail", "-c"]

RUN --mount=type=cache,target=/var/cache/apt,sharing=locked \
    --mount=type=cache,target=/var/lib/apt/lists,sharing=locked \
    apt-get update && apt-get install -y --no-install-recommends \
      ca-certificates curl git unzip zip tar zstd python3 python-is-python3 \
      libicu74 libssl3t64 libunwind8 libstdc++6 libfontconfig1 libfreetype6 fonts-dejavu-core

COPY --from=java /usr/lib/jvm/msopenjdk-21 /usr/lib/jvm/msopenjdk-21
ENV JAVA_HOME=/usr/lib/jvm/msopenjdk-21 \
    ANDROID_HOME=/opt/android-sdk \
    ANDROID_SDK_ROOT=/opt/android-sdk \
    SALUT_BUILD_CONTAINER=1 \
    DOTNET_CLI_TELEMETRY_OPTOUT=1 \
    POWERSHELL_TELEMETRY_OPTOUT=1
ENV PATH="/opt/powershell:/opt/android-commandline/cmdline-tools/bin:${JAVA_HOME}/bin:${PATH}"

RUN --mount=type=cache,target=/downloads \
    curl --fail --location --retry 3 https://github.com/PowerShell/PowerShell/releases/download/v7.5.5/powershell-7.5.5-linux-x64.tar.gz -o /downloads/powershell.tar.gz && \
    echo '39a62f466956e3606aee6637ed0d0735c1ed27612a76de973b111530ddff2e77  /downloads/powershell.tar.gz' | sha256sum --check - && \
    mkdir -p /opt/powershell && tar -xzf /downloads/powershell.tar.gz -C /opt/powershell && chmod +x /opt/powershell/pwsh && \
    curl --fail --location --retry 3 https://dl.google.com/android/repository/commandlinetools-linux-15859902_latest.zip -o /downloads/android-commandline.zip && \
    echo '4e4c464f145a7512b57d088ac6c278c03c9eea610886b35a5e0804e74eedf583  /downloads/android-commandline.zip' | sha256sum --check - && \
    unzip -q /downloads/android-commandline.zip -d /opt/android-commandline && \
    printf 'y\n%.0s' {1..100} | sdkmanager --sdk_root="${ANDROID_HOME}" --licenses && \
    sdkmanager --sdk_root="${ANDROID_HOME}" 'platforms;android-37.0' 'build-tools;37.0.0'

COPY Dockerfile /opt/salut-build/Dockerfile
RUN sha256sum /opt/salut-build/Dockerfile > /opt/salut-build/recipe.sha256 && \
    java -version && pwsh -NoProfile -Command '$PSVersionTable.PSVersion.ToString()' && python --version
WORKDIR /workspace
CMD ["pwsh", "-NoLogo", "-NoProfile"]
