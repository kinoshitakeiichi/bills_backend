SHELL := /bin/bash

GRADLEW := ./gradlew
APP_MODULE := b-application

RUN_DIR := .run
COMPILER_PID_FILE := $(RUN_DIR)/gradle-compiler.pid
APP_PID_FILE := $(RUN_DIR)/spring-boot.pid
COMPILER_LOG := /tmp/gradle-compiler.log

.DEFAULT_GOAL := help

.PHONY: help dev build clean stop logs

help:
	@echo "可用命令："
	@echo "  make dev    启动稳定 Spring Boot + 持续编译（代码变更后显式重启 backend）"
	@echo "  make build  执行完整构建"
	@echo "  make clean  清理 Gradle 构建目录"
	@echo "  make stop   停止开发环境进程"
	@echo "  make logs   查看持续编译日志"

# 启动稳定开发环境（DevTools 自动 restart 已关闭）
#
# Important: do not start `classes --continuous` before bootRun has finished its
# initial compilation. Two independent Gradle builds writing MapStruct generated
# sources at the same time can leave partially-written *MapperImpl.java files.
# We first compile once, boot the application, wait for the HTTP endpoint, and
# only then start the continuous compiler.
#
# Gradle 9.5.1 cannot combine explicitly-enabled file-system watching with
# --project-cache-dir. The dev container enables VFS watching globally, so
# bootRun uses a private project cache with --no-watch-fs, while the continuous
# compiler keeps the default project cache so file watching remains available. The compiler is
# intentionally diagnostic/build-only: DevTools automatic JVM restart is disabled because a
# restart can race with another Gradle task replacing a multi-module jar.
dev:
	@mkdir -p $(RUN_DIR)/gradle-project-cache/bootrun
	@rm -f "$(COMPILER_PID_FILE)" "$(APP_PID_FILE)"
	@set -euo pipefail; \
	echo "🔧 预编译项目，确保 bootRun 与持续编译不会争抢 generated sources..."; \
	$(GRADLEW) classes; \
	echo "🔥 启动 Spring Boot 应用..."; \
	$(GRADLEW) --no-watch-fs --project-cache-dir "$(RUN_DIR)/gradle-project-cache/bootrun" :$(APP_MODULE):bootRun & \
	app_pid=$$!; \
	echo $$app_pid > "$(APP_PID_FILE)"; \
	echo "🌱 Spring Boot Gradle PID: $$app_pid"; \
	cleanup() { \
		echo; \
		echo "🛑 正在停止开发环境..."; \
		if [ -n "$${compiler_pid:-}" ]; then kill $$compiler_pid 2>/dev/null || true; fi; \
		kill $$app_pid 2>/dev/null || true; \
		rm -f "$(COMPILER_PID_FILE)" "$(APP_PID_FILE)"; \
	}; \
	trap cleanup EXIT INT TERM; \
	echo "⏳ 等待 Spring Boot 首次启动完成..."; \
	ready=0; \
	for i in $$(seq 1 90); do \
		if ! kill -0 $$app_pid 2>/dev/null; then \
			echo "❌ Spring Boot 在就绪前退出。" >&2; \
			wait $$app_pid || true; \
			exit 1; \
		fi; \
		if curl -fsS --max-time 2 http://127.0.0.1:8080/api/v1/accounts >/dev/null 2>&1; then \
			ready=1; \
			break; \
		fi; \
		sleep 1; \
	done; \
	if [ $$ready -ne 1 ]; then \
		echo "❌ Spring Boot 90 秒内未就绪，不启动持续编译。" >&2; \
		exit 1; \
	fi; \
	echo "🚀 Spring Boot 已就绪，启动后台持续编译（不会自动重启 JVM）..."; \
	$(GRADLEW) classes --continuous > "$(COMPILER_LOG)" 2>&1 & \
	compiler_pid=$$!; \
	echo $$compiler_pid > "$(COMPILER_PID_FILE)"; \
	echo "⚡ 持续编译 PID: $$compiler_pid"; \
	echo "📝 编译日志: $(COMPILER_LOG)"; \
	wait $$app_pid

# 完整构建
build:
	$(GRADLEW) clean build

# 清理构建缓存
clean:
	$(GRADLEW) clean

# 停止遗留进程
stop:
	@echo "🛑 停止开发环境..."
	@if [ -f "$(COMPILER_PID_FILE)" ]; then \
		pid=$$(cat "$(COMPILER_PID_FILE)"); \
		if kill -0 $$pid 2>/dev/null; then \
			kill $$pid; \
			echo "已停止持续编译进程：$$pid"; \
		fi; \
		rm -f "$(COMPILER_PID_FILE)"; \
	fi
	@if [ -f "$(APP_PID_FILE)" ]; then \
		pid=$$(cat "$(APP_PID_FILE)"); \
		if kill -0 $$pid 2>/dev/null; then \
			kill $$pid; \
			echo "已停止 Spring Boot 进程：$$pid"; \
		fi; \
		rm -f "$(APP_PID_FILE)"; \
	fi

# 查看持续编译日志
logs:
	@touch "$(COMPILER_LOG)"
	tail -f "$(COMPILER_LOG)"
