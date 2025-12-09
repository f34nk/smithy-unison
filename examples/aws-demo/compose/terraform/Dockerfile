FROM amazonlinux:2023

USER root

ARG ARCHITECTURE=${ARCHITECTURE:-'arm64'}
ARG TERRAFORM_VERSION=1.7.1
ENV TERRAFORM_RELEASE=terraform_${TERRAFORM_VERSION}_linux_${ARCHITECTURE}

# Install dependencies and create non-root user
RUN dnf -y --quiet install unzip shadow-utils && \
  useradd --system --create-home nonroot && \
  # Download and extract `terraform` binary
  # See https://www.hashicorp.com/official-release-channels
  curl --silent --show-error --fail https://releases.hashicorp.com/terraform/${TERRAFORM_VERSION}/${TERRAFORM_RELEASE}.zip --output ${TERRAFORM_RELEASE}.zip && \
  unzip -q ./${TERRAFORM_RELEASE}.zip -d /opt/hashicorp

ENV PATH="/opt/hashicorp:${PATH}"

# Set up working directory with proper permissions
WORKDIR /app
COPY --chmod=755 entrypoint.sh main.tf config1.toml config2.toml /app/
RUN chown -R nonroot:nonroot /app

USER nonroot
WORKDIR /app

CMD ["./entrypoint.sh"]
