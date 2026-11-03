# 1. 选择一个基础镜像。推荐使用 slim 版本，体积更小
#    请根据您项目的 Java 版本选择，例如 openjdk:11-jre-slim, openjdk:17-jdk-slim
FROM openjdk:17-jdk-slim

# 安装ffmpeg
RUN apt-get update && apt-get install -y ffmpeg && rm -rf /var/lib/apt/lists/*

# 2. 维护者信息 (可选)
LABEL maintainer="huihui"

# 3. 在容器内创建一个工作目录
WORKDIR /app

# 4. 将打包好的 JAR 文件复制到容器的工作目录中
#    注意：请将 "target/*.jar" 中的 "target" 换成您实际的 JAR 包路径
#    "app.jar" 是在容器内的新名称，方便后续统一引用
COPY target/*.jar app.jar

# 5. 声明容器对外暴露的端口
#    这个端口应与您 Spring Boot application.properties/yml 中配置的 server.port 一致
EXPOSE 8091

# 6. 容器启动时执行的命令
#    使用 exec 格式的 ENTRYPOINT，这是推荐的做法
ENTRYPOINT ["java", "-jar", "app.jar"]