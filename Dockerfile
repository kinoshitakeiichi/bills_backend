# 使用你指定的顶级 JDK 25 镜像作为基础
FROM gradle:jdk25

# 切换为 root 以安装系统级软件
USER root

# hadolint ignore=DL3008
# 1. 安装基础工具、Neovim、全局搜索以及 zsh
RUN apt-get update && apt-get install -y --no-install-recommends \
  neovim \
  git \
  curl \
  build-essential \
  unzip \
  gzip \
  tar \
  fd-find \
  ripgrep \
  nodejs \
  npm \
  zsh \
  postgresql-client \
  sqlfluff \
  jp \
  coreutils \
  && rm -rf /var/lib/apt/lists/*

RUN apt-get update && apt-get install -y openjdk-17-jdk

# 2. 创建 fd 软链接
RUN ln -s $(which fdfind) /usr/local/bin/fd

RUN mkdir -p /home/gradle/.config/nvim \
  /home/gradle/.local/share/nvim \
  /home/gradle/.local/state/nvim \
  /home/gradle/.cache/nvim \
  /home/gradle/.oh-my-zsh/custom \
  && chown -R gradle:gradle /home/gradle

# 4. 设置工作目录并确保权限
WORKDIR /app
RUN chown -R gradle:gradle /app && rm -rf /home/gradle/.oh-my-zsh
# 5. 顺畅切换回非 root 的 gradle 用户
USER gradle
ENV HOME=/home/gradle
RUN git clone --depth=1 https://github.com/ohmyzsh/ohmyzsh.git ${HOME}/.oh-my-zsh

# =========================
# Install JDTLS and Lombok
# =========================
ARG JDTLS_VERSION=1.60.0-202606262232
ARG LOMBOK_VERSION=1.18.46

RUN set -eux; \
  JDTLS_HOME="${HOME}/.local/share/jdtls"; \
  rm -rf "${JDTLS_HOME}"; \
  mkdir -p "${JDTLS_HOME}"; \
  \
  curl -fsSL \
  "https://download.eclipse.org/jdtls/snapshots/jdt-language-server-${JDTLS_VERSION}.tar.gz" \
  -o /tmp/jdtls.tar.gz; \
  \
  tar -xzf /tmp/jdtls.tar.gz \
  -C "${JDTLS_HOME}"; \
  \
  curl -fsSL \
  "https://repo.maven.apache.org/maven2/org/projectlombok/lombok/${LOMBOK_VERSION}/lombok-${LOMBOK_VERSION}.jar" \
  -o "${JDTLS_HOME}/lombok.jar"; \
  \
  chmod +x "${JDTLS_HOME}/bin/jdtls"; \
  rm -f /tmp/jdtls.tar.gz; \
  \
  test -x "${JDTLS_HOME}/bin/jdtls"; \
  test -s "${JDTLS_HOME}/lombok.jar"; \
  test -d "${JDTLS_HOME}/plugins"; \
  test -d "${JDTLS_HOME}/config_linux"


RUN git clone --depth=1 https://github.com/zsh-users/zsh-autosuggestions.git ${HOME}/.oh-my-zsh/custom/plugins/zsh-autosuggestions && \
  git clone --depth=1 https://github.com/zsh-users/zsh-syntax-highlighting.git ${HOME}/.oh-my-zsh/custom/plugins/zsh-syntax-highlighting && \
  git clone --depth=1 https://github.com/romkatv/powerlevel10k.git ${HOME}/.oh-my-zsh/custom/themes/powerlevel10k
# 默认保持容器不退出
CMD ["tail", "-f", "/dev/null"]

